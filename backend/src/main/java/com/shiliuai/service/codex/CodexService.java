package com.shiliuai.service.codex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiliuai.dto.CodexRunRequest;
import com.shiliuai.dto.CodexRunResponse;
import com.shiliuai.util.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 视流 AI · 真实的 Codex 调用层。
 *
 * 设计原则：
 * - 真实运行 `codex exec --json ... <prompt>`，逐行解析 JSON 事件流。
 * - 默认 sandbox=read-only，避免移动端/飞书远程触发误改文件；
 *   显式 workspace-write 才允许写。
 * - 通过 ProcessBuilder 控制 working dir + 超时，避免进程僵死。
 *
 * Codex CLI 不可用时返回 disabled，由调用方降级。
 */
@Service
public class CodexService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CodexService.class);
    private static final Set<String> ALLOWED_SANDBOXES = Set.of("read-only", "workspace-write");

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final boolean enabled;
    private final String binary;
    private final Path defaultWorkingDir;
    private final List<Path> allowedWorkingRoots;
    private final long timeoutSeconds;
    private final List<String> extraArgs;
    private final String codexHome;

    /**
     * 异步 run 缓存：手机 → cloudflare quick tunnel → 后端的同步等待会被 100 秒响应窗口截掉
     * （Error 524）。submitAsync 立刻返回 runId，前端轮询 fetchRun 直到 status 不是 running。
     *
     * ConcurrentHashMap：runId 是 UUID，不会重复。容量较小（CLI 调用本身较慢），
     * 通过 sweepRetainedRuns 在每次提交时清理超过 4 小时的旧记录，防止内存堆积。
     */
    private final ConcurrentMap<String, CodexRunResponse> runCache = new ConcurrentHashMap<>();
    private static final long RUN_RETENTION_SECONDS = 4 * 60 * 60L;

    public CodexService(ObjectMapper objectMapper,
                        Clock clock,
                        @Value("${shiliu.codex.enabled:true}") boolean enabled,
                        @Value("${shiliu.codex.binary:codex}") String binary,
                        @Value("${shiliu.codex.working-dir:/home/chase/GitHub/shiliu-ai-v1}") String workingDir,
                        @Value("${shiliu.codex.timeout-seconds:110}") long timeoutSeconds,
                        @Value("${shiliu.codex.extra-args:}") String extraArgsRaw,
                        @Value("${shiliu.codex.allowed-working-roots:/home/chase/GitHub/shiliu-ai-v1}") String allowedWorkingRootsRaw,
                        @Value("${shiliu.codex.home:}") String codexHome) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.enabled = enabled;
        this.binary = binary;
        this.defaultWorkingDir = Paths.get(workingDir).toAbsolutePath().normalize();
        this.allowedWorkingRoots = parseAllowedRoots(allowedWorkingRootsRaw, this.defaultWorkingDir);
        this.timeoutSeconds = timeoutSeconds;
        this.extraArgs = parseArgs(extraArgsRaw);
        this.codexHome = codexHome == null ? "" : codexHome.trim();
    }

    public CodexRunResponse run(CodexRunRequest request) {
        return doRun(request, null);
    }

    /**
     * 异步提交：立刻返回 runId + status=running 的占位响应，子线程在后台跑真实 codex。
     *
     * 设计目的：手机端在 Cloudflare quick tunnel 后面调用 codex 时，
     * 单个 HTTP 响应窗口只有 100 秒，远小于 codex 默认 5 分钟超时，必须改成 poll 模式。
     */
    public CodexRunResponse submitAsync(CodexRunRequest request) {
        CodexRunResponse response = new CodexRunResponse();
        response.runId = Ids.runId(clock);
        if (request == null || !StringUtils.hasText(request.prompt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Codex prompt 不能为空");
        }
        response.prompt = request.prompt;
        Path workPath = resolveWorkingDir(request.workingDir);
        response.workingDir = workPath.toString();
        response.sandbox = normalizeSandbox(request.sandbox);
        response.status = "running";
        response.summary = "Codex 已入队，正在后台运行。";
        response.startedAt = Instant.now(clock).toString();

        // 保存初始占位，前端可以立刻轮询
        runCache.put(response.runId, response);
        sweepRetainedRuns();

        Thread worker = new Thread(() -> {
            try {
                doRun(request, response);
            } catch (RuntimeException exception) {
                response.status = "failed";
                response.summary = "Codex 异步执行失败：" + exception.getMessage();
                response.finishedAt = Instant.now(clock).toString();
            }
        }, "codex-async-" + response.runId);
        worker.setDaemon(true);
        worker.start();

        // 返回浅拷贝，避免前端拿到的字段在子线程中继续被修改
        return snapshotOf(response);
    }

    /**
     * 查询某次异步 run 的当前状态。前端按 1~3 秒轮询。
     */
    public CodexRunResponse fetchRun(String runId) {
        if (!StringUtils.hasText(runId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "runId 不能为空");
        }
        CodexRunResponse cached = runCache.get(runId);
        if (cached == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Codex run 不存在或已过期：" + runId);
        }
        return snapshotOf(cached);
    }

    private CodexRunResponse doRun(CodexRunRequest request, CodexRunResponse existing) {
        CodexRunResponse response = existing != null ? existing : new CodexRunResponse();
        if (existing == null) {
            response.runId = Ids.runId(clock);
        }
        if (request == null || !StringUtils.hasText(request.prompt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Codex prompt 不能为空");
        }
        response.prompt = request.prompt;
        Path workPath = resolveWorkingDir(request.workingDir);
        response.workingDir = workPath.toString();
        response.sandbox = normalizeSandbox(request.sandbox);

        if (!enabled) {
            response.status = "disabled";
            response.summary = "Codex 已禁用。设置 SHILIU_CODEX_ENABLED=true 后启用。";
            response.startedAt = Instant.now(clock).toString();
            response.finishedAt = response.startedAt;
            response.durationMs = 0L;
            return response;
        }

        File workDir = workPath.toFile();
        if (!workDir.isDirectory()) {
            response.status = "failed";
            response.summary = "工作目录不存在：" + response.workingDir;
            return response;
        }

        List<String> command = new ArrayList<>();
        command.add(binary);
        command.add("exec");
        command.add("--json");
        command.add("--sandbox");
        command.add(response.sandbox);
        // skip-git-repo-check 允许在非 git 子目录运行；保持可移植
        command.add("--skip-git-repo-check");
        command.addAll(extraArgs);
        command.add(request.prompt);

        LOGGER.info("Codex run id={} workingDir={} sandbox={} command={}",
                response.runId, response.workingDir, response.sandbox,
                String.join(" ", command));

        Instant started = Instant.now(clock);
        response.startedAt = started.toString();

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir);
        pb.redirectErrorStream(false);
        if (StringUtils.hasText(codexHome)) {
            pb.environment().put("CODEX_HOME", Paths.get(codexHome).toAbsolutePath().normalize().toString());
        }

        Process process;
        try {
            process = pb.start();
            process.getOutputStream().close();
        } catch (IOException e) {
            response.status = "failed";
            response.summary = "无法启动 codex 进程：" + e.getMessage()
                    + "\n请确认 codex CLI 已安装且 PATH 可见，或设置 SHILIU_CODEX_BINARY 指向绝对路径。";
            response.finishedAt = Instant.now(clock).toString();
            response.durationMs = Duration.between(started, Instant.now(clock)).toMillis();
            return response;
        }

        AtomicReference<String> stderrTail = new AtomicReference<>("");
        AtomicReference<String> stdoutError = new AtomicReference<>("");
        StringBuilder summary = new StringBuilder();
        Thread stdoutPump = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.isEmpty()) continue;
                    synchronized (response.events) {
                        response.events.add(line);
                        // 防止超大输出撑爆响应：events 截断到 500 行
                        if (response.events.size() > 500) {
                            response.events.remove(0);
                        }
                    }
                    synchronized (summary) {
                        appendMessageIfPresent(line, summary);
                    }
                }
            } catch (IOException e) {
                stdoutError.set(e.getMessage() == null ? e.toString() : e.getMessage());
                LOGGER.warn("Codex stdout read failed", e);
            }
        }, "codex-stdout-" + response.runId);
        stdoutPump.setDaemon(true);

        Thread stderrPump = new Thread(() -> {
            StringBuilder buf = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    buf.append(line).append('\n');
                    // 只保留最后 4KB stderr，防止内存爆
                    if (buf.length() > 4096) {
                        buf.delete(0, buf.length() - 4096);
                    }
                }
            } catch (IOException ignored) {
            }
            stderrTail.set(buf.toString());
        }, "codex-stderr-" + response.runId);
        stderrPump.setDaemon(true);
        stdoutPump.start();
        stderrPump.start();

        boolean finished;
        try {
            finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            response.status = "failed";
            response.summary = "Codex 调用被中断。";
            response.finishedAt = Instant.now(clock).toString();
            response.durationMs = Duration.between(started, Instant.now(clock)).toMillis();
            return response;
        }

        if (!finished) {
            process.destroyForcibly();
            waitForProcessExit(process);
            joinPump(stdoutPump, 2000);
            joinPump(stderrPump, 2000);
            response.status = "timeout";
            response.exitCode = null;
            String partial = snapshot(summary);
            response.summary = "Codex 执行超时（>" + timeoutSeconds + "s）。已强制终止。"
                    + (partial.isBlank() ? "" : "\n" + partial);
            response.stderrTail = stderrTail.get();
            response.finishedAt = Instant.now(clock).toString();
            response.durationMs = Duration.between(started, Instant.now(clock)).toMillis();
            return response;
        }

        joinPump(stdoutPump, 2000);
        joinPump(stderrPump, 2000);

        int exit = process.exitValue();
        response.exitCode = exit;
        response.stderrTail = stderrTail.get();
        response.finishedAt = Instant.now(clock).toString();
        response.durationMs = Duration.between(started, Instant.now(clock)).toMillis();
        response.status = exit == 0 ? "done" : "failed";
        String finalText = snapshot(summary);
        if (finalText.isEmpty()) {
            finalText = exit == 0
                    ? "Codex 已完成（未输出 message 事件）。"
                    : "Codex 退出码 " + exit + "，无输出。stderr 尾部：\n" + truncate(stderrTail.get(), 400);
        }
        if (exit != 0 && StringUtils.hasText(stdoutError.get())) {
            finalText = finalText + "\nstdout 读取错误：" + stdoutError.get();
        }
        response.summary = finalText;
        return response;
    }

    /**
     * Codex JSON 事件示例（不同子集会出现）：
     *   {"type":"message","role":"assistant","content":"..."}
     *   {"type":"agent_message","message":"..."}
     *   {"type":"item","item":{"type":"assistant_message","text":"..."}}
     * 这里采用宽松解析：抓任何 "text" / "message" / "content" 字段。
     */
    private void appendMessageIfPresent(String jsonLine, StringBuilder summary) {
        try {
            JsonNode node = objectMapper.readTree(jsonLine);
            String type = node.path("type").asText("");
            // 跳过明显非消息事件
            if (type.equals("turn_started") || type.equals("turn_finished")
                    || type.equals("tool_call") || type.equals("tool_result")) {
                return;
            }
            String text = firstNonEmpty(
                    node.path("text").asText(""),
                    node.path("message").asText(""),
                    node.path("content").asText(""),
                    node.at("/item/text").asText(""),
                    node.at("/item/content").asText(""),
                    node.at("/delta/text").asText(""),
                    node.at("/error/message").asText("")
            );
            if (StringUtils.hasText(text)) {
                if (summary.length() > 0) summary.append('\n');
                summary.append(text);
            }
        } catch (IOException ignored) {
            // 非 JSON 行直接忽略
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (StringUtils.hasText(v)) return v;
        }
        return "";
    }

    private Path resolveWorkingDir(String requested) {
        Path candidate = defaultWorkingDir;
        if (StringUtils.hasText(requested)) {
            Path raw = Paths.get(requested.trim());
            candidate = raw.isAbsolute()
                    ? raw.normalize()
                    : defaultWorkingDir.resolve(raw).normalize();
        }
        if (!isAllowedWorkingDir(candidate)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Codex workingDir 不在允许目录内：" + candidate);
        }
        return candidate;
    }

    private boolean isAllowedWorkingDir(Path candidate) {
        return allowedWorkingRoots.stream().anyMatch(candidate::startsWith);
    }

    private static String normalizeSandbox(String requested) {
        String sandbox = StringUtils.hasText(requested) ? requested.trim() : "read-only";
        if (!ALLOWED_SANDBOXES.contains(sandbox)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "不支持的 Codex sandbox：" + sandbox + "。仅允许 read-only 或 workspace-write。");
        }
        return sandbox;
    }

    private static List<Path> parseAllowedRoots(String raw, Path fallback) {
        if (!StringUtils.hasText(raw)) {
            return List.of(fallback);
        }
        List<Path> roots = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(value -> Paths.get(value).toAbsolutePath().normalize())
                .toList();
        return roots.isEmpty() ? List.of(fallback) : roots;
    }

    private static List<String> parseArgs(String raw) {
        if (!StringUtils.hasText(raw)) return List.of();
        return Arrays.stream(raw.trim().split("\\s+")).filter(s -> !s.isEmpty()).toList();
    }

    private static void joinPump(Thread thread, long millis) {
        try {
            thread.join(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static void waitForProcessExit(Process process) {
        try {
            process.waitFor(2, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static String snapshot(StringBuilder summary) {
        synchronized (summary) {
            return summary.toString().trim();
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max - 1) + "…";
    }

    /**
     * 浅拷贝：events 是列表，需要在快照时复制，避免读端遍历过程中后台仍在 append。
     */
    private static CodexRunResponse snapshotOf(CodexRunResponse src) {
        CodexRunResponse copy = new CodexRunResponse();
        copy.runId = src.runId;
        copy.status = src.status;
        copy.exitCode = src.exitCode;
        copy.prompt = src.prompt;
        copy.workingDir = src.workingDir;
        copy.sandbox = src.sandbox;
        copy.summary = src.summary;
        copy.stderrTail = src.stderrTail;
        copy.durationMs = src.durationMs;
        copy.startedAt = src.startedAt;
        copy.finishedAt = src.finishedAt;
        synchronized (src.events) {
            copy.events = new ArrayList<>(src.events);
        }
        return copy;
    }

    private void sweepRetainedRuns() {
        Instant now = Instant.now(clock);
        for (Map.Entry<String, CodexRunResponse> entry : runCache.entrySet()) {
            CodexRunResponse value = entry.getValue();
            String stamp = value.finishedAt != null ? value.finishedAt : value.startedAt;
            if (stamp == null) continue;
            try {
                Instant t = Instant.parse(stamp);
                if (t.plusSeconds(RUN_RETENTION_SECONDS).isBefore(now)) {
                    runCache.remove(entry.getKey(), value);
                }
            } catch (Exception ignored) {
                // 时间戳异常的记录直接保留，等下次再尝试
            }
        }
    }
}
