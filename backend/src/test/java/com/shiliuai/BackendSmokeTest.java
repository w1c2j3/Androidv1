package com.shiliuai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiliuai.dto.FileRef;
import com.shiliuai.dto.OcrBlock;
import com.shiliuai.dto.OcrResult;
import com.shiliuai.dto.PaperDto;
import com.shiliuai.entity.FeishuCardActionLogEntity;
import com.shiliuai.repository.FeishuCardActionLogRepository;
import com.shiliuai.repository.ReportMaterialRepository;
import com.shiliuai.repository.VisionTraceRepository;
import com.shiliuai.service.feishu.FeishuClientAdapter;
import com.shiliuai.service.feishu.FeishuResourceDownloader;
import com.shiliuai.service.feishu.FeishuTokenProvider;
import com.shiliuai.service.agent.PaperSearchService;
import com.shiliuai.service.vision.OcrProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shiliu-test;DB_CLOSE_DELAY=-1",
        "shiliu.file-storage-dir=./target/test-files",
        "shiliu.llm.enabled=false",
        "shiliu.agent.allowed-project-roots=/workspace/shiliu-ai-v1,/home/chase/GitHub/shiliu-ai-v1"
})
@AutoConfigureMockMvc
class BackendSmokeTest {
    private static final String AUTH = "Bearer dev-admin-token";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ExternalServiceStubs.RecordingFeishuClientAdapter feishuClientAdapter;

    @Autowired
    ReportMaterialRepository reportMaterialRepository;

    @Autowired
    VisionTraceRepository visionTraceRepository;

    @Autowired
    FeishuCardActionLogRepository feishuCardActionLogRepository;

    @Test
    void healthReturnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.service").value("shiliu-ai-backend"));
    }

    @Test
    void registerBotAndQueryHealth() throws Exception {
        String botId = registerBot();
        mockMvc.perform(get("/api/v1/bots/{botId}/health", botId).header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(true))
                .andExpect(jsonPath("$.status").value("waiting_event"));
    }

    @Test
    void setupReadinessReportsBotAndNextStep() throws Exception {
        registerBot();
        mockMvc.perform(get("/api/v1/setup/readiness").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backendOk").value(true))
                .andExpect(jsonPath("$.ocrConfigured").value(true))
                .andExpect(jsonPath("$.llmConfigured").value(false))
                .andExpect(jsonPath("$.llmModel").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.botRegistered").value(true))
                .andExpect(jsonPath("$.tokenValid").value(true))
                .andExpect(jsonPath("$.ready").value(false))
                .andExpect(jsonPath("$.nextStep").isNotEmpty());
    }

    @Test
    void queueStatusReportsRealExecutors() throws Exception {
        mockMvc.perform(get("/api/v1/setup/queues").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.healthy").value(true))
                .andExpect(jsonPath("$.status").isNotEmpty())
                .andExpect(jsonPath("$.pools.length()").value(2))
                .andExpect(jsonPath("$.pools[0].activeCount").isNumber())
                .andExpect(jsonPath("$.pools[0].queueSize").isNumber())
                .andExpect(jsonPath("$.pools[0].queueCapacity").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void uploadVisionResultAndSaveTask() throws Exception {
        String traceId = uploadVisionAndWait();
        mockMvc.perform(get("/api/v1/vision/results/{traceId}", traceId).header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("done"))
                .andExpect(jsonPath("$.stage").value("done"))
                .andExpect(jsonPath("$.progress").value(100))
                .andExpect(jsonPath("$.tasks.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.dailyReportMaterials.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.extractMode").value("rule_fallback"))
                .andExpect(jsonPath("$.ocrConfidence").value(greaterThanOrEqualTo(0.9)))
                .andExpect(jsonPath("$.ocr.blockCount").value(3))
                .andExpect(jsonPath("$.ocr.averageConfidence").value(greaterThanOrEqualTo(0.9)))
                .andExpect(jsonPath("$.ocr.width").value(1080))
                .andExpect(jsonPath("$.ocr.blocks[0].id").value("b1"))
                .andExpect(jsonPath("$.tasks[0].evidence").isNotEmpty())
                .andExpect(jsonPath("$.tasks[0].sourceBlockIds[0]").value("b1"));

        mockMvc.perform(post("/api/v1/tasks/from-trace/{traceId}", traceId)
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selectedTaskTempIds\":[\"task_tmp_1\"],\"override\":{\"owner\":\"我\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedCount").value(1))
                .andExpect(jsonPath("$.tasks[0].owner").value("我"))
                .andExpect(jsonPath("$.tasks[0].sourceType").value("vision_trace"))
                .andExpect(jsonPath("$.tasks[0].sourceId").value(traceId))
                .andExpect(jsonPath("$.tasks[0].evidenceText").isNotEmpty());

        org.assertj.core.api.Assertions.assertThat(reportMaterialRepository.findByTraceIdOrderByCreatedAtAsc(traceId))
                .isNotEmpty();
        org.assertj.core.api.Assertions.assertThat(visionTraceRepository.findById(traceId))
                .get()
                .satisfies(trace -> {
                    org.assertj.core.api.Assertions.assertThat(trace.getExtractMode()).isEqualTo("rule_fallback");
                    org.assertj.core.api.Assertions.assertThat(trace.getOcrConfidence()).isGreaterThan(0.9);
                });

        mockMvc.perform(get("/api/v1/tasks?status=todo").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void uploadWithoutFilenameSuffixUsesContentTypeExtension() throws Exception {
        String traceId = startVisionUpload("android_upload", "auto", "content-uri", "image/png");
        waitForVisionStatus(traceId, "done");
        org.assertj.core.api.Assertions.assertThat(visionTraceRepository.findById(traceId))
                .get()
                .satisfies(trace -> org.assertj.core.api.Assertions.assertThat(trace.getImagePath()).endsWith(".png"));
    }

    @Test
    void workbenchApisAreAvailable() throws Exception {
        String traceId = uploadVisionAndWait();
        mockMvc.perform(get("/api/v1/vision/traces?limit=5").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.items[0].traceId").isNotEmpty());

        mockMvc.perform(get("/api/v1/workbench/overview").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayTraceCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.recentTraces.length()").value(greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/v1/vision/files/{traceId}", traceId).header("Authorization", AUTH))
                .andExpect(status().isOk());
    }

    @Test
    void taskStatusCanBeUpdatedFromWorkbench() throws Exception {
        String traceId = uploadVisionAndWait();
        String saveJson = mockMvc.perform(post("/api/v1/tasks/from-trace/{traceId}", traceId)
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selectedTaskTempIds\":[\"task_tmp_1\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedCount").value(1))
                .andReturn().getResponse().getContentAsString();
        String taskId = objectMapper.readTree(saveJson).path("tasks").get(0).path("id").asText();

        mockMvc.perform(patch("/api/v1/tasks/{taskId}/status", taskId)
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"in_progress\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("in_progress"));

        mockMvc.perform(post("/api/v1/tasks/{taskId}/status", taskId)
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"done\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("done"));

        mockMvc.perform(patch("/api/v1/tasks/{taskId}/status", taskId)
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"bad\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void agentRunRoutesResearchToRealDataSourceAndSaveTasks() throws Exception {
        String runJson = mockMvc.perform(post("/api/v1/agent/runs")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "command":"/paper 收集 benchmark 测评论文",
                                  "projectPath":"/workspace/shiliu-ai-v1",
                                  "source":"android"
                                }
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("done"))
                .andExpect(jsonPath("$.intent").value("research"))
                .andExpect(jsonPath("$.module").value("Research Agent"))
                .andExpect(jsonPath("$.papers.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.tasks.length()").value(greaterThanOrEqualTo(1)))
                .andReturn().getResponse().getContentAsString();
        String runId = objectMapper.readTree(runJson).path("runId").asText();

        mockMvc.perform(get("/api/v1/agent/runs/{runId}", runId).header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId));

        mockMvc.perform(post("/api/v1/agent/runs/{runId}/tasks", runId).header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedCount").value(greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/v1/agent/runs").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.items[0].runId").isNotEmpty());
    }

    @Test
    void agentRunDigestUsesProvidedContextWithoutFakeData() throws Exception {
        mockMvc.perform(post("/api/v1/agent/runs")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "command":"/digest 总结 OCR 内容",
                                  "source":"android",
                                  "contextText":"请完成权限方案\\n确认 Android 真机测试"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("done"))
                .andExpect(jsonPath("$.intent").value("digest"))
                .andExpect(jsonPath("$.summary").value(org.hamcrest.Matchers.containsString("真实输入")))
                .andExpect(jsonPath("$.tasks.length()").value(greaterThanOrEqualTo(2)));
    }

    @Test
    void agentRunDigestRequiresRealContext() throws Exception {
        mockMvc.perform(post("/api/v1/agent/runs")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "command":"/digest 总结今天群消息",
                                  "source":"android"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("needs_data_source"))
                .andExpect(jsonPath("$.risks[0].type").value("digest_source_missing"));
    }

    @Test
    void agentRunRejectsProjectPathOutsideAllowedRoots() throws Exception {
        mockMvc.perform(post("/api/v1/agent/runs")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "command":"/plan 分析系统路径",
                                  "projectPath":"/etc",
                                  "source":"android"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void textTaskCanBeCreatedDirectly() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"移动端创建任务\",\"source\":\"android\",\"owner\":\"我\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("移动端创建任务"))
                .andExpect(jsonPath("$.status").value("todo"));
    }

    @Test
    void memoryCanBeSavedAndListed() throws Exception {
        mockMvc.perform(post("/api/v1/memory")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"产品定位\",\"content\":\"Android-first，CV/OCR 是辅助输入模块\",\"category\":\"decision\",\"source\":\"test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("产品定位"));

        mockMvc.perform(get("/api/v1/memory").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.items[0].content").isNotEmpty());

        mockMvc.perform(post("/api/v1/agent/runs")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"command\":\"/remember 记住：手机端默认只读分析，写代码需要确认\",\"source\":\"android\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("memory"));
    }

    @Test
    void feishuCardCallbackCanShowConfirmAndSuccessCards() throws Exception {
        String botId = registerBot();
        String traceId = uploadVisionAndWait();
        mockMvc.perform(post("/feishu/card-callback/{botId}", botId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "header":{"token":"token_xxx"},
                                  "event":{"action":{"value":{"action":"open_task_confirm","traceId":"%s"}}}
                                }
                                """.formatted(traceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.card.header.title.content").value("识别到 1 条任务，请确认"));

        mockMvc.perform(post("/feishu/card-callback/{botId}", botId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "header":{"token":"token_xxx"},
                                  "event":{"action":{"value":{"action":"save_tasks","traceId":"%s"}}}
                                }
                """.formatted(traceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toast.type").value("success"))
                .andExpect(jsonPath("$.card.header.title.content").value("任务保存成功"));

        FeishuCardActionLogEntity legacySuccess = new FeishuCardActionLogEntity();
        legacySuccess.setId("legacy_success_log");
        legacySuccess.setBotId(botId);
        legacySuccess.setTraceId(traceId);
        legacySuccess.setAction("legacy_success");
        legacySuccess.setStatus("success");
        legacySuccess.setCreatedAt(Instant.now().plusSeconds(1));
        feishuCardActionLogRepository.save(legacySuccess);

        mockMvc.perform(get("/api/v1/workbench/overview").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recentCardActions.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.recentCardActions[0].status").value("ok"));
    }

    @Test
    void uploadReturnsProcessingBeforeAsyncJobCompletes() throws Exception {
        String traceId = startVisionUpload("slow_upload", "auto");
        mockMvc.perform(get("/api/v1/vision/results/{traceId}", traceId).header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("processing"))
                .andExpect(jsonPath("$.progress").value(lessThan(100)));

        JsonNode done = waitForVisionStatus(traceId, "done");
        org.assertj.core.api.Assertions.assertThat(done.path("stage").asText()).isEqualTo("done");
    }

    @Test
    void ocrFailureIsStoredAsTraceError() throws Exception {
        String traceId = startVisionUpload("fail_upload", "auto");
        JsonNode error = waitForVisionStatus(traceId, "error");
        org.assertj.core.api.Assertions.assertThat(error.path("errorCode").asText()).isEqualTo("OCR_FAILED");
        org.assertj.core.api.Assertions.assertThat(error.path("message").asText()).contains("test ocr failure");

        mockMvc.perform(post("/api/v1/tasks/from-trace/{traceId}", traceId)
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selectedTaskTempIds\":[\"task_tmp_1\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void feishuChallengeReturnsChallenge() throws Exception {
        mockMvc.perform(post("/feishu/events/bot_missing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challenge\":\"abc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challenge").value("abc"));
    }

    @Test
    void feishuPingEventIsAccepted() throws Exception {
        String botId = registerBot();
        String body = """
                {
                  "schema": "2.0",
                  "header": {
                    "event_id": "evt_1",
                    "event_type": "im.message.receive_v1",
                    "token": "token_xxx",
                    "app_id": "cli_xxx"
                  },
                  "event": {
                    "message": {
                      "message_id": "om_xxx",
                      "chat_id": "oc_xxx",
                      "message_type": "text",
                      "content": "{\\"text\\":\\"/ping\\"}"
                    }
                  }
                }
                """;
        mockMvc.perform(post("/feishu/events/{botId}", botId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/v1/bots/{botId}/health", botId).header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.eventCallbackVerified").value(true));
    }

    @Test
    void feishuTextCommandRunsAgentRun() throws Exception {
        feishuClientAdapter.clear();
        String botId = registerBot();
        String body = """
                {
                  "schema": "2.0",
                  "header": {
                    "event_id": "evt_agent_1",
                    "event_type": "im.message.receive_v1",
                    "token": "token_xxx",
                    "app_id": "cli_xxx"
                  },
                  "event": {
                    "message": {
                      "message_id": "om_agent_xxx",
                      "chat_id": "oc_xxx",
                      "message_type": "text",
                      "content": "{\\"text\\":\\"@test /paper 收集 benchmark 测评论文\\"}"
                    }
                  }
                }
                """;
        mockMvc.perform(post("/feishu/events/{botId}", botId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("text accepted"));

        String reply = waitForTextContaining("Research Agent");
        org.assertj.core.api.Assertions.assertThat(reply).contains("论文候选");

        String runsJson = mockMvc.perform(get("/api/v1/agent/runs").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(greaterThanOrEqualTo(1)))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(runsJson).contains("\"intent\":\"research\"");
    }

    @Test
    void feishuImageEventSchedulesAsyncVisionJob() throws Exception {
        feishuClientAdapter.clear();
        String botId = registerBot();
        String body = """
                {
                  "schema": "2.0",
                  "header": {
                    "event_id": "evt_img_1",
                    "event_type": "im.message.receive_v1",
                    "token": "token_xxx",
                    "app_id": "cli_xxx"
                  },
                  "event": {
                    "message": {
                      "message_id": "om_img_xxx",
                      "chat_id": "oc_xxx",
                      "message_type": "image",
                      "content": "{\\"image_key\\":\\"img_v3_xxx\\"}"
                    }
                  }
                }
                """;
        String json = mockMvc.perform(post("/feishu/events/{botId}", botId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("image accepted"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String traceId = objectMapper.readTree(json).path("traceId").asText();
        JsonNode done = waitForVisionStatus(traceId, "done");
        org.assertj.core.api.Assertions.assertThat(done.path("scene").asText()).isEqualTo("chat_screenshot");
        JsonNode finalCard = waitForUpdatedCardTitle("视流 AI 已整理完成");
        org.assertj.core.api.Assertions.assertThat(finalCard.at("/config/update_multi").asBoolean()).isTrue();
    }

    private String registerBot() throws Exception {
        String json = mockMvc.perform(post("/api/v1/bots/register")
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "botName":"视流助手-测试",
                                  "appId":"cli_xxx",
                                  "appSecret":"secret_xxx",
                                  "verificationToken":"token_xxx",
                                  "encryptKey":"",
                                  "tenantName":"测试企业"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("registered"))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(json);
        return node.path("botId").asText();
    }

    private String uploadVisionAndWait() throws Exception {
        String traceId = startVisionUpload("android_upload", "auto");
        waitForVisionStatus(traceId, "done");
        return traceId;
    }

    private String startVisionUpload(String source, String sceneHint) throws Exception {
        return startVisionUpload(source, sceneHint, "screenshot.png", "image/png");
    }

    private String startVisionUpload(String source, String sceneHint, String filename, String contentType) throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "file",
                filename,
                contentType,
                "fake image".getBytes()
        );
        String uploadJson = mockMvc.perform(multipart("/api/v1/vision/upload")
                        .file(image)
                        .param("source", source)
                        .param("sceneHint", sceneHint)
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("processing"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(uploadJson).path("traceId").asText();
    }

    private JsonNode waitForVisionStatus(String traceId, String expectedStatus) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        JsonNode latest = null;
        while (System.currentTimeMillis() < deadline) {
            String body = mockMvc.perform(get("/api/v1/vision/results/{traceId}", traceId).header("Authorization", AUTH))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            latest = objectMapper.readTree(body);
            if (expectedStatus.equals(latest.path("status").asText())) {
                return latest;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("trace " + traceId + " did not reach status " + expectedStatus
                + ", latest=" + (latest == null ? "<none>" : latest.toString()));
    }

    private JsonNode waitForUpdatedCardTitle(String expectedTitle) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            for (Map<String, Object> card : feishuClientAdapter.updatedCards) {
                JsonNode node = objectMapper.valueToTree(card);
                if (expectedTitle.equals(node.at("/header/title/content").asText())) {
                    return node;
                }
            }
            Thread.sleep(50);
        }
        throw new AssertionError("did not update card title " + expectedTitle
                + ", updatedCards=" + feishuClientAdapter.updatedCards.size());
    }

    private String waitForTextContaining(String expectedText) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            for (String text : feishuClientAdapter.texts) {
                if (text.contains(expectedText)) {
                    return text;
                }
            }
            Thread.sleep(50);
        }
        throw new AssertionError("did not send text containing " + expectedText
                + ", texts=" + feishuClientAdapter.texts);
    }

    @TestConfiguration
    static class ExternalServiceStubs {
        @Bean
        @Primary
        OcrProvider testOcrProvider() {
            return request -> {
                if ("fail_upload".equals(request.source)) {
                    throw new IllegalStateException("test ocr failure");
                }
                if ("slow_upload".equals(request.source)) {
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("test interrupted", exception);
                    }
                }
                OcrResult result = new OcrResult();
                result.traceId = request.traceId;
                result.imageType = "chat_screenshot";
                result.width = 1080;
                result.height = 2340;
                result.engine = "static";
                result.engineVersion = "test";
                result.modelProfile = "mobile";
                result.lang = "ch";
                result.plainText = "明天下班前把权限方案整理一下\n还要考虑 Android Studio 真机测试\nhttps://example.com";
                result.blocks = List.of(
                        block("b1", "明天下班前把权限方案整理一下", 120, 410, 920, 468, 0.94),
                        block("b2", "还要考虑 Android Studio 真机测试", 120, 490, 930, 548, 0.91),
                        block("b3", "https://example.com", 120, 560, 930, 620, 0.98)
                );
                result.quality = Map.of("blur", 0.12, "brightness", 0.78);
                return result;
            };
        }

        @Bean
        @Primary
        RecordingFeishuClientAdapter testFeishuClientAdapter() {
            return new RecordingFeishuClientAdapter();
        }

        @Bean
        @Primary
        FeishuResourceDownloader testFeishuResourceDownloader() {
            return (bot, messageId, imageKey) -> {
                FileRef ref = new FileRef();
                ref.localFileId = "file_test";
                ref.localPath = "target/test-files/feishu-image.png";
                ref.mimeType = "image/png";
                ref.sizeBytes = 1;
                ref.sha256 = "test";
                ref.downloadStatus = "success";
                return ref;
            };
        }

        @Bean
        @Primary
        FeishuTokenProvider testFeishuTokenProvider() {
            return bot -> "test_tenant_access_token";
        }

        @Bean
        @Primary
        PaperSearchService testPaperSearchService() {
            return (command, limit) -> {
                PaperDto paper = new PaperDto();
                paper.title = "Real API Result Fixture";
                paper.year = "2026";
                paper.venue = "arXiv:cs.AI";
                paper.url = "https://arxiv.org/abs/0000.00000";
                paper.contribution = "测试替身模拟外部 arXiv 响应，生产环境使用 ArxivPaperSearchService。";
                paper.whyRelevant = "测试中避免访问外网。";
                paper.tags = List.of("cs.AI");
                return List.of(paper);
            };
        }

        private static OcrBlock block(String id, String text, int x1, int y1, int x2, int y2, double confidence) {
            OcrBlock block = new OcrBlock();
            block.id = id;
            block.type = "text_line";
            block.text = text;
            block.bbox = new int[]{x1, y1, x2, y2};
            block.confidence = confidence;
            return block;
        }

        static class RecordingFeishuClientAdapter implements FeishuClientAdapter {
            private final AtomicInteger sequence = new AtomicInteger();
            final List<String> texts = new CopyOnWriteArrayList<>();
            final List<Map<String, Object>> sentCards = new CopyOnWriteArrayList<>();
            final List<Map<String, Object>> updatedCards = new CopyOnWriteArrayList<>();

            @Override
            public String sendText(com.shiliuai.entity.BotConfigEntity bot, String chatId, String text) {
                texts.add(text);
                return "om_text_" + sequence.incrementAndGet();
            }

            @Override
            public String sendCard(com.shiliuai.entity.BotConfigEntity bot, String chatId, Map<String, Object> card) {
                sentCards.add(card);
                return "om_card_" + sequence.incrementAndGet();
            }

            @Override
            public void updateCard(com.shiliuai.entity.BotConfigEntity bot, String messageId, Map<String, Object> card) {
                updatedCards.add(card);
            }

            @Override
            public List<String> listRecentTextMessages(com.shiliuai.entity.BotConfigEntity bot, String chatId, int limit) {
                return List.of(
                        "今天需要整理 benchmark 测评方案",
                        "请确认 Android 真机测试链路",
                        "完成飞书机器人回调配置"
                );
            }

            void clear() {
                texts.clear();
                sentCards.clear();
                updatedCards.clear();
            }
        }
    }
}
