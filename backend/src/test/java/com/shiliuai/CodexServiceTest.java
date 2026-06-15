package com.shiliuai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiliuai.dto.CodexRunRequest;
import com.shiliuai.dto.CodexRunResponse;
import com.shiliuai.service.codex.CodexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodexServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-14T00:00:00Z"), ZoneOffset.UTC);
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createDirectories(Path.of(
                "target",
                "codex-service-test",
                String.valueOf(SEQUENCE.incrementAndGet())).toAbsolutePath());
    }

    @Test
    void runsCodexBinaryAndParsesJsonMessages() throws Exception {
        Path workDir = Files.createDirectories(tempDir.resolve("repo"));
        Path binary = script("fake-codex", """
                #!/usr/bin/env sh
                printf '%s\\n' '{"type":"agent_message","message":"OK from fake codex"}'
                """);

        CodexRunResponse response = service(binary, workDir, 5).run(request("scan repo"));

        assertThat(response.status).isEqualTo("done");
        assertThat(response.exitCode).isZero();
        assertThat(response.workingDir).isEqualTo(workDir.toString());
        assertThat(response.sandbox).isEqualTo("read-only");
        assertThat(response.summary).contains("OK from fake codex");
        assertThat(response.events).hasSize(1);
    }

    @Test
    void closesChildStdinSoCodexDoesNotWaitForMoreInput() throws Exception {
        Path workDir = Files.createDirectories(tempDir.resolve("repo"));
        Path binary = script("fake-codex", """
                #!/usr/bin/env sh
                cat >/dev/null
                printf '%s\\n' '{"type":"agent_message","message":"stdin closed"}'
                """);

        CodexRunResponse response = service(binary, workDir, 5).run(request("scan repo"));

        assertThat(response.status).isEqualTo("done");
        assertThat(response.summary).contains("stdin closed");
    }

    @Test
    void rejectsDangerFullAccessSandbox() throws Exception {
        Path workDir = Files.createDirectories(tempDir.resolve("repo"));
        Path binary = script("fake-codex", """
                #!/usr/bin/env sh
                exit 0
                """);
        CodexRunRequest request = request("scan repo");
        request.sandbox = "danger-full-access";

        assertThatThrownBy(() -> service(binary, workDir, 5).run(request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void rejectsWorkingDirOutsideAllowedRoot() throws Exception {
        Path workDir = Files.createDirectories(tempDir.resolve("repo"));
        Path outside = Files.createDirectories(tempDir.resolve("outside"));
        Path binary = script("fake-codex", """
                #!/usr/bin/env sh
                exit 0
                """);
        CodexRunRequest request = request("scan repo");
        request.workingDir = outside.toString();

        assertThatThrownBy(() -> service(binary, workDir, 5).run(request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void timeoutDoesNotWaitForStdoutToCloseFirst() throws Exception {
        Path workDir = Files.createDirectories(tempDir.resolve("repo"));
        Path binary = script("fake-codex", """
                #!/usr/bin/env sh
                exec sleep 30
                """);

        long started = System.nanoTime();
        CodexRunResponse response = service(binary, workDir, 1).run(request("hang"));
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;

        assertThat(response.status).isEqualTo("timeout");
        assertThat(response.exitCode).isNull();
        assertThat(response.summary).contains("执行超时");
        assertThat(elapsedMs).isLessThan(5_000L);
    }

    private CodexService service(Path binary, Path workDir, long timeoutSeconds) {
        return new CodexService(
                new ObjectMapper(),
                CLOCK,
                true,
                binary.toString(),
                workDir.toString(),
                timeoutSeconds,
                "",
                workDir.toString(),
                "");
    }

    private CodexRunRequest request(String prompt) {
        CodexRunRequest request = new CodexRunRequest();
        request.prompt = prompt;
        return request;
    }

    private Path script(String name, String body) throws Exception {
        Path path = tempDir.resolve(name);
        Files.writeString(path, body.stripLeading(), StandardCharsets.UTF_8);
        assertThat(path.toFile().setExecutable(true)).isTrue();
        return path;
    }
}
