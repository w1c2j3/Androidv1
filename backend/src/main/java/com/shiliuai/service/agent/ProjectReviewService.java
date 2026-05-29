package com.shiliuai.service.agent;

import com.shiliuai.dto.RiskFlagDto;
import com.shiliuai.dto.TaskCandidateDto;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class ProjectReviewService {
    private static final int MAX_SCAN_DEPTH = 8;
    private static final int MAX_FILES = 8000;

    public ProjectReviewResult review(String projectPath, String command) {
        Path root = Path.of(projectPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("项目路径不存在或不是目录：" + root);
        }

        List<Path> files = listFiles(root);
        Map<String, Long> extensionCounts = extensionCounts(files);
        GitSnapshot git = gitSnapshot(root);
        boolean wantsBugs = containsAny(command, List.of("/bug", "bug", "问题", "风险"));

        ProjectReviewResult result = new ProjectReviewResult();
        result.summary = buildSummary(root, files, extensionCounts, git);
        result.nextSteps.addAll(buildNextSteps(files, extensionCounts, git, wantsBugs));
        result.risks.addAll(buildRisks(files, extensionCounts, git));
        result.tasks.addAll(buildTasks(result.risks, wantsBugs));
        result.logs.add("scannedRoot=" + root);
        result.logs.add("fileCount=" + files.size());
        result.logs.add("gitBranch=" + emptyAs(git.branch, "unknown"));
        result.logs.add("gitDirtyCount=" + git.dirtyCount);
        return result;
    }

    private static List<Path> listFiles(Path root) {
        List<Path> files = new ArrayList<>();
        try {
            Files.walkFileTree(root, Set.of(), MAX_SCAN_DEPTH, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(root) && isIgnored(root.relativize(dir))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return files.size() >= MAX_FILES ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile() && !isIgnored(root.relativize(file))) {
                        files.add(file);
                    }
                    return files.size() >= MAX_FILES ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                }
            });
            return files;
        } catch (Exception exception) {
            throw new IllegalStateException("扫描项目文件失败：" + exception.getMessage(), exception);
        }
    }

    private static boolean isIgnored(Path relativePath) {
        String value = relativePath.toString().replace('\\', '/');
        return value.equals("build")
                || value.equals("target")
                || value.equals(".gradle")
                || value.equals(".mvn")
                || value.equals(".gradle-user-home")
                || value.equals(".android-user-home")
                || value.equals(".git")
                || value.equals("node_modules")
                || value.equals(".venv")
                || value.equals("venv")
                || value.equals("__pycache__")
                || value.equals(".cache")
                || value.equals(".pip-cache")
                || value.equals(".uv-cache")
                || value.equals(".home")
                || value.equals(".pytest_cache")
                || value.equals(".mypy_cache")
                || value.equals(".tmp")
                || value.equals("data")
                || value.startsWith("build/")
                || value.startsWith("target/")
                || value.startsWith(".gradle/")
                || value.startsWith(".mvn/")
                || value.startsWith(".gradle-user-home/")
                || value.startsWith(".android-user-home/")
                || value.startsWith(".git/")
                || value.startsWith("node_modules/")
                || value.startsWith(".venv/")
                || value.startsWith("venv/")
                || value.startsWith("__pycache__/")
                || value.startsWith(".cache/")
                || value.startsWith(".pip-cache/")
                || value.startsWith(".uv-cache/")
                || value.startsWith(".home/")
                || value.startsWith(".pytest_cache/")
                || value.startsWith(".mypy_cache/")
                || value.startsWith(".tmp/")
                || value.startsWith("data/")
                || value.contains("/build/")
                || value.contains("/target/")
                || value.contains("/.gradle/")
                || value.contains("/.mvn/")
                || value.contains("/.gradle-user-home/")
                || value.contains("/.android-user-home/")
                || value.contains("/.git/")
                || value.contains("/node_modules/")
                || value.contains("/.venv/")
                || value.contains("/venv/")
                || value.contains("/__pycache__/")
                || value.contains("/.cache/")
                || value.contains("/.pip-cache/")
                || value.contains("/.uv-cache/")
                || value.contains("/.home/")
                || value.contains("/.pytest_cache/")
                || value.contains("/.mypy_cache/")
                || value.contains("/.tmp/")
                || value.contains("/data/")
                || value.endsWith(".png")
                || value.endsWith(".jpg")
                || value.endsWith(".jpeg")
                || value.endsWith(".apk");
    }

    private static Map<String, Long> extensionCounts(List<Path> files) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Path file : files) {
            String name = file.getFileName().toString();
            int dot = name.lastIndexOf('.');
            String ext = dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "no_ext";
            counts.put(ext, counts.getOrDefault(ext, 0L) + 1);
        }
        return counts;
    }

    private static String buildSummary(Path root, List<Path> files, Map<String, Long> extensionCounts, GitSnapshot git) {
        String topExtensions = extensionCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(5)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + ", " + right)
                .orElse("无可识别文件类型");
        String gitText = StringUtils.hasText(git.branch)
                ? "Git 分支 " + git.branch + "，未提交变更 " + git.dirtyCount + " 项。"
                : "未读取到 Git 仓库状态。";
        return "已完成真实只读项目扫描：" + root + "，扫描文件 " + files.size() + " 个，主要类型：" + topExtensions + "。" + gitText;
    }

    private static List<RiskFlagDto> buildRisks(List<Path> files, Map<String, Long> extensionCounts, GitSnapshot git) {
        List<RiskFlagDto> risks = new ArrayList<>();
        if (git.dirtyCount > 0) {
            risks.add(new RiskFlagDto("git_dirty", "当前工作区存在 " + git.dirtyCount + " 项未提交变更，继续自动修改前需要先确认边界。"));
        }
        boolean hasBackendTests = files.stream().anyMatch(path -> path.toString().contains("src/test"));
        if (!hasBackendTests) {
            risks.add(new RiskFlagDto("missing_tests", "扫描范围内没有发现测试目录，代码修改风险较高。"));
        }
        boolean hasAndroid = extensionCounts.getOrDefault("java", 0L) > 0
                && files.stream().anyMatch(path -> path.toString().contains("android"));
        boolean hasGradle = extensionCounts.containsKey("gradle");
        if (hasAndroid && !hasGradle) {
            risks.add(new RiskFlagDto("android_build", "发现 Android Java 代码，但没有在扫描深度内发现 Gradle 构建文件。"));
        }
        boolean hasReadme = files.stream().anyMatch(path -> path.getFileName().toString().equalsIgnoreCase("README.md"));
        if (!hasReadme) {
            risks.add(new RiskFlagDto("docs", "扫描范围内没有 README.md，后续移动端交接成本会升高。"));
        }
        if (risks.isEmpty()) {
            risks.add(new RiskFlagDto("review", "未发现结构性阻塞；下一步应结合真实测试输出做更细 review。"));
        }
        return risks;
    }

    private static List<String> buildNextSteps(List<Path> files,
                                               Map<String, Long> extensionCounts,
                                               GitSnapshot git,
                                               boolean wantsBugs) {
        List<String> steps = new ArrayList<>();
        if (git.dirtyCount > 0) {
            steps.add("先确认当前未提交变更哪些属于本次重构，避免覆盖用户修改。");
        }
        if (extensionCounts.getOrDefault("java", 0L) > 0) {
            steps.add("运行后端测试和 Android debug 构建，验证 Java 链路。");
        }
        if (files.stream().anyMatch(path -> path.toString().contains("docs/"))) {
            steps.add("同步清理 docs/plan 中与当前实现不一致的说明。");
        }
        if (wantsBugs) {
            steps.add("基于测试失败、Git diff 和真实日志继续做 bug review。");
        }
        return steps.stream().distinct().limit(5).toList();
    }

    private static List<TaskCandidateDto> buildTasks(List<RiskFlagDto> risks, boolean wantsBugs) {
        List<TaskCandidateDto> tasks = new ArrayList<>();
        int index = 1;
        for (RiskFlagDto risk : risks.stream().limit(wantsBugs ? 5 : 3).toList()) {
            TaskCandidateDto task = new TaskCandidateDto();
            task.tempId = "task_tmp_" + index++;
            task.title = "处理项目扫描风险：" + risk.type;
            task.owner = "未指定";
            task.priority = "git_dirty".equals(risk.type) || "missing_tests".equals(risk.type) ? "high" : "medium";
            task.status = "pending_confirm";
            task.confidence = 0.78;
            tasks.add(task);
        }
        return tasks;
    }

    private static GitSnapshot gitSnapshot(Path root) {
        GitSnapshot snapshot = new GitSnapshot();
        snapshot.branch = runGit(root, "rev-parse", "--abbrev-ref", "HEAD").firstLine();
        CommandOutput status = runGit(root, "status", "--short");
        snapshot.dirtyCount = (int) status.lines.stream().filter(StringUtils::hasText).count();
        snapshot.statusLines = status.lines.stream().limit(20).toList();
        return snapshot;
    }

    private static CommandOutput runGit(Path root, String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        try {
            Process process = new ProcessBuilder(command)
                    .directory(root.toFile())
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new CommandOutput(List.of());
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                return new CommandOutput(reader.lines().toList());
            }
        } catch (Exception ignored) {
            return new CommandOutput(List.of());
        }
    }

    private static boolean containsAny(String value, List<String> needles) {
        String text = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return needles.stream().anyMatch(text::contains);
    }

    private static String emptyAs(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private record CommandOutput(List<String> lines) {
        String firstLine() {
            return lines.stream().filter(StringUtils::hasText).findFirst().orElse("");
        }
    }

    private static class GitSnapshot {
        String branch;
        int dirtyCount;
        List<String> statusLines = List.of();
    }

    public static class ProjectReviewResult {
        public String summary;
        public List<RiskFlagDto> risks = new ArrayList<>();
        public List<String> nextSteps = new ArrayList<>();
        public List<TaskCandidateDto> tasks = new ArrayList<>();
        public List<String> logs = new ArrayList<>();
    }
}
