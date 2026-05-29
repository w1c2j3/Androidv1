package com.shiliuai.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity {
    private static final int BG = Color.rgb(245, 247, 251);
    private static final int SURFACE = Color.WHITE;
    private static final int TEXT = Color.rgb(15, 23, 42);
    private static final int MUTED = Color.rgb(100, 116, 139);
    private static final int LINE = Color.rgb(226, 232, 240);
    private static final int PRIMARY = Color.rgb(37, 99, 235);
    private static final int GREEN = Color.rgb(16, 185, 129);
    private static final int ORANGE = Color.rgb(249, 115, 22);
    private static final int PURPLE = Color.rgb(124, 58, 237);
    private static final int TEAL = Color.rgb(8, 145, 178);
    private static final int DARK = Color.rgb(15, 23, 42);
    private static final int RED = Color.rgb(239, 68, 68);

    private Screen currentScreen = Screen.WORKBENCH;
    private Tab currentTab = Tab.WORKBENCH;
    private final ArrayDeque<Screen> backStack = new ArrayDeque<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final List<String> appLogs = new ArrayList<>();
    private ActivityResultLauncher<Intent> imagePicker;
    private String backendBaseUrl = "http://10.0.2.2:8080";
    private String adminToken = "dev-admin-token";
    private String commandText = "检查这个项目最严重的 5 个问题，并给出下一步";
    private String projectPath = "/home/chase/GitHub/shiliu-ai-v1";
    private String statusText = "未检测后端";
    private JSONObject lastRun;
    private JSONObject overview;
    private JSONObject readiness;
    private JSONObject botRegistration;
    private JSONObject lastVisionUpload;
    private JSONObject lastVisionResult;
    private JSONArray tasks = new JSONArray();
    private JSONArray memories = new JSONArray();

    private enum Tab {
        WORKBENCH, TASKS, KNOWLEDGE, PROJECTS, SETTINGS
    }

    private enum Screen {
        WORKBENCH,
        COMMAND_CENTER,
        INTENT_DETAIL,
        AGENT_RUN,
        RUN_RESULT,
        PROJECTS,
        PROJECT_DETAIL,
        CODEX_CONFIRM,
        TASKS,
        TASK_DETAIL,
        KNOWLEDGE,
        RESEARCH,
        PAPER_DETAIL,
        MESSAGE_DIGEST,
        DIGEST_RESULT,
        OCR_TOOL,
        DISPATCH,
        FEISHU,
        SETTINGS,
        RUN_LOGS
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadSettings();
        imagePicker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                uploadImage(result.getData().getData());
            }
        });
        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);
        WindowCompat.getInsetsController(window, window.getDecorView()).setAppearanceLightStatusBars(true);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!backStack.isEmpty()) {
                    Screen previous = backStack.pop();
                    render(previous, tabFor(previous), false);
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });
        render(Screen.WORKBENCH, Tab.WORKBENCH, false);
        refreshOverview();
    }

    private void render(Screen screen, Tab tab, boolean push) {
        if (push && currentScreen != screen) {
            backStack.push(currentScreen);
        }
        currentScreen = screen;
        currentTab = tab;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(18), dp(20), dp(18));
        scrollView.addView(body, new ScrollView.LayoutParams(-1, -2));
        root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));

        switch (screen) {
            case WORKBENCH -> renderWorkbench(body);
            case COMMAND_CENTER -> renderCommandCenter(body);
            case INTENT_DETAIL -> renderIntentDetail(body);
            case AGENT_RUN -> renderAgentRun(body);
            case RUN_RESULT -> renderRunResult(body);
            case PROJECTS -> renderProjects(body);
            case PROJECT_DETAIL -> renderProjectDetail(body);
            case CODEX_CONFIRM -> renderCodexConfirm(body);
            case TASKS -> renderTasks(body);
            case TASK_DETAIL -> renderTaskDetail(body);
            case KNOWLEDGE -> renderKnowledge(body);
            case RESEARCH -> renderResearch(body);
            case PAPER_DETAIL -> renderPaperDetail(body);
            case MESSAGE_DIGEST -> renderMessageDigest(body);
            case DIGEST_RESULT -> renderDigestResult(body);
            case OCR_TOOL -> renderOcrTool(body);
            case DISPATCH -> renderDispatch(body);
            case FEISHU -> renderFeishu(body);
            case SETTINGS -> renderSettings(body);
            case RUN_LOGS -> renderLogs(body);
        }

        addBottomNav(root);
        setContentView(root);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences("shiliu_ai", MODE_PRIVATE);
        backendBaseUrl = prefs.getString("backendBaseUrl", backendBaseUrl);
        adminToken = prefs.getString("adminToken", adminToken);
        projectPath = prefs.getString("projectPath", projectPath);
    }

    private void saveSettings(String baseUrl, String token, String path) {
        backendBaseUrl = isBlank(baseUrl) ? "http://10.0.2.2:8080" : baseUrl.trim();
        adminToken = token == null ? "" : token.trim();
        projectPath = isBlank(path) ? projectPath : path.trim();
        getSharedPreferences("shiliu_ai", MODE_PRIVATE)
                .edit()
                .putString("backendBaseUrl", backendBaseUrl)
                .putString("adminToken", adminToken)
                .putString("projectPath", projectPath)
                .apply();
        statusText = "设置已保存";
        log("settings saved: " + backendBaseUrl);
        render(currentScreen, currentTab, false);
    }

    private ShiliuApiClient api() {
        return new ShiliuApiClient(backendBaseUrl, adminToken);
    }

    private void refreshOverview() {
        runNetwork("刷新工作台", () -> {
            JSONObject result = new JSONObject();
            result.put("health", api().get("/api/v1/health"));
            result.put("readiness", api().get("/api/v1/setup/readiness"));
            result.put("overview", api().get("/api/v1/workbench/overview"));
            result.put("tasks", api().get("/api/v1/tasks"));
            result.put("memory", api().get("/api/v1/memory"));
            return result;
        }, result -> {
            readiness = result.optJSONObject("readiness");
            overview = result.optJSONObject("overview");
            JSONObject taskResponse = result.optJSONObject("tasks");
            tasks = taskResponse == null ? new JSONArray() : taskResponse.optJSONArray("items");
            if (tasks == null) {
                tasks = new JSONArray();
            }
            JSONObject memoryResponse = result.optJSONObject("memory");
            memories = memoryResponse == null ? new JSONArray() : memoryResponse.optJSONArray("items");
            if (memories == null) {
                memories = new JSONArray();
            }
            statusText = "后端已连接";
            render(currentScreen, currentTab, false);
        });
    }

    private void loadTasks() {
        runNetwork("加载任务", () -> api().get("/api/v1/tasks"), result -> {
            tasks = result.optJSONArray("items");
            if (tasks == null) {
                tasks = new JSONArray();
            }
            render(Screen.TASKS, Tab.TASKS, false);
        });
    }

    private void runCommand(String command) {
        runCommand(command, "", "");
    }

    private void runCommand(String command, String contextText, String traceId) {
        commandText = isBlank(command) ? commandText : command.trim();
        JSONObject body = new JSONObject();
        try {
            body.put("command", commandText);
            body.put("projectPath", projectPath);
            body.put("source", "android");
            if (!isBlank(contextText)) {
                body.put("contextText", contextText);
            }
            if (!isBlank(traceId)) {
                body.put("traceId", traceId);
            }
        } catch (Exception exception) {
            statusText = exception.getMessage();
            render(currentScreen, currentTab, false);
            return;
        }
        statusText = "AgentRun 创建中...";
        render(Screen.AGENT_RUN, Tab.WORKBENCH, true);
        runNetwork("创建 AgentRun", () -> api().postJson("/api/v1/agent/runs", body), result -> {
            lastRun = result;
            String intent = result.optString("intent");
            if ("research".equals(intent)) {
                render(Screen.PAPER_DETAIL, Tab.KNOWLEDGE, false);
            } else if ("digest".equals(intent)) {
                render(Screen.DIGEST_RESULT, Tab.KNOWLEDGE, false);
            } else {
                render(Screen.RUN_RESULT, Tab.WORKBENCH, false);
            }
        });
    }

    private void saveRunTasks() {
        if (lastRun == null || isBlank(lastRun.optString("runId"))) {
            statusText = "没有可保存的 AgentRun";
            render(currentScreen, currentTab, false);
            return;
        }
        String runId = lastRun.optString("runId");
        runNetwork("保存任务", () -> api().postJson("/api/v1/agent/runs/" + runId + "/tasks", new JSONObject()), result -> {
            statusText = "已保存 " + result.optInt("savedCount") + " 条任务";
            loadTasks();
        });
    }

    private void createTask(String title) {
        JSONObject body = new JSONObject();
        try {
            body.put("title", title);
            body.put("source", "android");
            body.put("owner", "我");
        } catch (Exception exception) {
            statusText = exception.getMessage();
            render(currentScreen, currentTab, false);
            return;
        }
        runNetwork("创建任务", () -> api().postJson("/api/v1/tasks", body), result -> {
            statusText = "任务已创建：" + result.optString("title");
            loadTasks();
        });
    }

    private void loadMemory() {
        runNetwork("加载知识库", () -> api().get("/api/v1/memory"), result -> {
            memories = result.optJSONArray("items");
            if (memories == null) {
                memories = new JSONArray();
            }
            render(Screen.KNOWLEDGE, Tab.KNOWLEDGE, false);
        });
    }

    private void saveMemory(String title, String content) {
        JSONObject body = new JSONObject();
        try {
            body.put("title", title);
            body.put("content", content);
            body.put("category", "decision");
            body.put("source", "android");
        } catch (Exception exception) {
            statusText = exception.getMessage();
            render(currentScreen, currentTab, false);
            return;
        }
        runNetwork("保存知识", () -> api().postJson("/api/v1/memory", body), result -> {
            statusText = "已保存知识：" + result.optString("title");
            loadMemory();
        });
    }

    private void registerFeishuBot(String botName,
                                   String appId,
                                   String appSecret,
                                   String verificationToken,
                                   String encryptKey,
                                   String tenantName) {
        JSONObject body = new JSONObject();
        try {
            body.put("botName", botName);
            body.put("appId", appId);
            body.put("appSecret", appSecret);
            body.put("verificationToken", verificationToken);
            body.put("encryptKey", encryptKey);
            body.put("tenantName", tenantName);
        } catch (Exception exception) {
            statusText = exception.getMessage();
            render(currentScreen, currentTab, false);
            return;
        }
        runNetwork("注册飞书机器人", () -> api().postJson("/api/v1/bots/register", body), result -> {
            botRegistration = result;
            statusText = "机器人已注册：" + result.optString("botId");
            render(Screen.FEISHU, Tab.SETTINGS, false);
            refreshOverview();
        });
    }

    private void updateFeishuCallback(String botId, String verificationToken, String encryptKey) {
        if (isBlank(botId)) {
            statusText = "没有可更新的 botId，请先注册机器人或刷新 readiness";
            render(currentScreen, currentTab, false);
            return;
        }
        JSONObject body = new JSONObject();
        try {
            body.put("verificationToken", verificationToken);
            body.put("encryptKey", encryptKey);
        } catch (Exception exception) {
            statusText = exception.getMessage();
            render(currentScreen, currentTab, false);
            return;
        }
        runNetwork("更新飞书回调配置", () -> api().patchJson("/api/v1/bots/" + botId + "/callback-config", body), result -> {
            statusText = "回调配置已更新";
            refreshOverview();
        });
    }

    private void saveVisionTasks() {
        String traceId = lastVisionUpload == null ? "" : lastVisionUpload.optString("traceId");
        if (isBlank(traceId)) {
            statusText = "没有可保存的 OCR trace";
            render(currentScreen, currentTab, false);
            return;
        }
        runNetwork("保存 OCR 任务", () -> api().postJson("/api/v1/tasks/from-trace/" + traceId, new JSONObject()), result -> {
            statusText = "已保存 OCR 任务 " + result.optInt("savedCount") + " 条";
            loadTasks();
        });
    }

    private void refreshVisionResult() {
        String traceId = lastVisionUpload == null ? "" : lastVisionUpload.optString("traceId");
        if (isBlank(traceId)) {
            statusText = "没有可刷新的 OCR trace";
            render(currentScreen, currentTab, false);
            return;
        }
        runNetwork("刷新 OCR 结果", () -> api().get("/api/v1/vision/results/" + traceId), result -> {
            lastVisionResult = result;
            render(Screen.DISPATCH, Tab.WORKBENCH, false);
        });
    }

    private void runDigestFromVision() {
        String text = visionPlainText();
        if (isBlank(text)) {
            statusText = "没有可用于总结的 OCR 原文";
            render(currentScreen, currentTab, false);
            return;
        }
        runCommand("/digest 总结 OCR 内容", text, currentTraceId());
    }

    private void runProjectFromVision() {
        String text = visionPlainText();
        if (isBlank(text)) {
            statusText = "没有可用于项目分析的 OCR 原文";
            render(currentScreen, currentTab, false);
            return;
        }
        runCommand("/plan 基于 OCR 上下文分析 " + projectPath + " 下一步", text, currentTraceId());
    }

    private void saveVisionToMemory() {
        String text = visionPlainText();
        if (isBlank(text)) {
            statusText = "没有可保存的 OCR 原文";
            render(currentScreen, currentTab, false);
            return;
        }
        String traceId = lastVisionResult == null ? "" : lastVisionResult.optString("traceId");
        saveMemory(isBlank(traceId) ? "OCR 原文" : "OCR " + traceId, text);
    }

    private String visionPlainText() {
        JSONObject ocr = lastVisionResult == null ? null : lastVisionResult.optJSONObject("ocr");
        return ocr == null ? "" : ocr.optString("plainText", "");
    }

    private String currentTraceId() {
        if (lastVisionResult != null && !isBlank(lastVisionResult.optString("traceId"))) {
            return lastVisionResult.optString("traceId");
        }
        return lastVisionUpload == null ? "" : lastVisionUpload.optString("traceId");
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        imagePicker.launch(intent);
    }

    private void uploadImage(Uri uri) {
        statusText = "图片上传中...";
        render(Screen.OCR_TOOL, Tab.WORKBENCH, false);
        runNetwork("上传图片", () -> {
            JSONObject upload = api().uploadImage(this, uri);
            String traceId = upload.optString("traceId");
            JSONObject result = isBlank(traceId) ? new JSONObject() : pollVisionResult(traceId);
            JSONObject combined = new JSONObject();
            combined.put("upload", upload);
            combined.put("result", result);
            return combined;
        }, result -> {
            lastVisionUpload = result.optJSONObject("upload");
            lastVisionResult = result.optJSONObject("result");
            render(Screen.DISPATCH, Tab.WORKBENCH, false);
        });
    }

    private JSONObject pollVisionResult(String traceId) throws Exception {
        JSONObject latest = new JSONObject();
        for (int i = 0; i < 18; i++) {
            latest = api().get("/api/v1/vision/results/" + traceId);
            String status = latest.optString("status");
            if ("done".equals(status) || "error".equals(status)) {
                return latest;
            }
            Thread.sleep(600);
        }
        return latest;
    }

    private void runNetwork(String label, NetworkJob job, Consumer<JSONObject> success) {
        log(label + " started");
        executor.execute(() -> {
            try {
                JSONObject result = job.run();
                runOnUiThread(() -> {
                    statusText = label + "完成";
                    log(label + " ok");
                    success.accept(result);
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    statusText = label + "失败：" + exception.getMessage();
                    log(label + " failed: " + exception.getMessage());
                    render(currentScreen, currentTab, false);
                });
            }
        });
    }

    private void log(String line) {
        appLogs.add(0, "[" + java.time.LocalTime.now().withNano(0) + "] " + line);
        while (appLogs.size() > 80) {
            appLogs.remove(appLogs.size() - 1);
        }
    }

    private interface NetworkJob {
        JSONObject run() throws Exception;
    }

    private void renderWorkbench(LinearLayout body) {
        title(body, "AI 工作台", "所有输入先进入命令中心，再由 Agent 分析和执行");
        body.addView(infoCard("连接状态", statusText + "\n" + backendBaseUrl, readiness != null && readiness.optBoolean("ready") ? GREEN : PRIMARY), lpTop(16));

        LinearLayout command = card();
        rowText(command, "你想让项目助理做什么？", "分析 " + projectPath + " 下一步", PRIMARY);
        addChips(command, new String[]{"项目", "论文", "文件"});
        command.addView(primaryButton("发送", v -> runCommand("分析 " + projectPath + " 下一步")), lpTop(10));
        body.addView(command, lpTop(16));

        section(body, "快捷动作", "查看全部");
        grid(body, new Action[]{
                new Action("分析项目", PRIMARY, "分析 " + projectPath + " 下一步"),
                new Action("收集论文", PURPLE, "/paper 收集 benchmark 测评论文"),
                new Action("总结群消息", TEAL, Screen.MESSAGE_DIGEST),
                new Action("上传截图", ORANGE, Screen.OCR_TOOL),
                new Action("创建任务", GREEN, Screen.TASK_DETAIL),
                new Action("记住决策", Color.rgb(71, 85, 105), Screen.KNOWLEDGE)
        });

        section(body, "今日重点", "查看全部");
        String overviewText = overview == null
                ? "尚未同步工作台数据"
                : "今日识别 " + overview.optLong("todayTraceCount") + " 次 · 待办 " + overview.optLong("todoTaskCount")
                + " · 进行中 " + overview.optLong("inProgressTaskCount");
        body.addView(infoCard("后端工作台", overviewText, PRIMARY), lpTop(10));

        section(body, "最近运行", null);
        if (lastRun != null) {
            body.addView(runRow(lastRun.optString("module", "Agent"), lastRun.optString("summary", "AgentRun 完成"), PRIMARY, Screen.RUN_RESULT), lpTop(10));
        } else {
            body.addView(runRow("Agent", "还没有移动端运行记录", PRIMARY, Screen.COMMAND_CENTER), lpTop(10));
        }

        section(body, "并行 Agent 池", null);
        body.addView(infoCard("暂无真实 Agent 池数据", "后端还没有提供并发运行池接口；这里只显示真实 AgentRun 结果。", MUTED), lpTop(8));
        body.addView(secondaryButton("刷新后端数据", v -> refreshOverview()), lpTop(14));
    }

    private void renderCommandCenter(LinearLayout body) {
        title(body, "命令中心", "自然语言 + 斜杠命令，所有能力从这里进入");
        LinearLayout card = card();
        label(card, "输入命令");
        EditText editText = new EditText(this);
        editText.setText(commandText);
        editText.setTextSize(18);
        editText.setTextColor(TEXT);
        editText.setMinLines(3);
        editText.setGravity(Gravity.TOP);
        editText.setBackground(rounded(SURFACE, LINE, 8));
        editText.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.addView(editText, new LinearLayout.LayoutParams(-1, dp(116)));
        addChips(card, new String[]{"项目路径", "结果", "飞书群"});
        body.addView(card, lpTop(16));

        section(body, "推荐命令", null);
        String[] commands = {
                "/plan 分析项目下一步",
                "/bug 找最严重的 5 个问题",
                "/paper 收集 benchmark 论文",
                "/digest 总结群消息",
                "/remember 记住一个决策"
        };
        for (String command : commands) {
            body.addView(commandRow(command, () -> runCommand(command)), lpTop(8));
        }

        body.addView(primaryButton("创建 AgentRun", v -> runCommand(editText.getText().toString())), lpTop(18));
        twoButtons(body, "添加附件", v -> go(Screen.OCR_TOOL), "选择项目", v -> go(Screen.PROJECTS));
    }

    private void renderIntentDetail(LinearLayout body) {
        title(body, "识别意图", "Command Router 识别模块、参数和影响范围");
        LinearLayout card = card();
        rowText(card, "原始命令", commandText, PRIMARY);
        rowText(card, "系统识别", "提交后以后端 AgentRun 返回为准", TEXT);
        rowText(card, "输入", projectPath, TEXT);
        rowText(card, "模式", "只读默认，后端会校验项目路径白名单", GREEN);
        rowText(card, "输出", "没有真实执行器时不生成伪结果", PURPLE);
        body.addView(card, lpTop(16));
        body.addView(primaryButton("确认并开始分析", v -> runCommand("分析 " + projectPath + " 下一步")), lpTop(18));
        body.addView(secondaryButton("修改参数", v -> go(Screen.COMMAND_CENTER)), lpTop(10));
    }

    private void renderAgentRun(LinearLayout body) {
        title(body, "Agent 运行详情", "展示执行步骤、日志和是否需要人工确认");
        LinearLayout run = card(Color.rgb(219, 234, 254), Color.rgb(191, 219, 254));
        String runId = lastRun == null ? "等待创建" : lastRun.optString("runId");
        String runState = lastRun == null ? statusText : lastRun.optString("module") + " · " + lastRun.optString("status");
        rowText(run, runId, runState, PRIMARY);
        body.addView(run, lpTop(16));

        section(body, "执行步骤", null);
        JSONArray steps = lastRun == null ? new JSONArray() : lastRun.optJSONArray("steps");
        if (steps == null || steps.length() == 0) {
            body.addView(step("等待后端返回 AgentRun", PRIMARY, true), lpTop(8));
        } else {
            for (int i = 0; i < steps.length(); i++) {
                body.addView(step(steps.optString(i), i == steps.length() - 1 ? PRIMARY : GREEN, true), lpTop(8));
            }
        }

        section(body, "并发子任务", null);
        body.addView(infoCard("暂无真实子任务数据", "当前 AgentRun 只展示后端返回的步骤和日志。", MUTED), lpTop(8));

        section(body, "实时日志", null);
        body.addView(logBox(logText(lastRun)), lpTop(8));
        twoButtons(body, "取消运行", v -> go(Screen.WORKBENCH), "后台运行", v -> go(Screen.WORKBENCH));
        body.addView(primaryButton("查看运行结果", v -> go(Screen.RUN_RESULT)), lpTop(10));
    }

    private void renderRunResult(LinearLayout body) {
        title(body, "运行结果", "建议、风险、任务候选和继续追问入口");
        String summary = lastRun == null ? "还没有 AgentRun 结果，请先从命令中心创建。" : lastRun.optString("summary");
        body.addView(infoCard("最终结论", summary, TEXT), lpTop(16));

        section(body, "发现的问题", null);
        JSONArray risks = lastRun == null ? null : lastRun.optJSONArray("risks");
        if (risks == null || risks.length() == 0) {
            body.addView(priorityRow("INFO", "暂无风险，或结果尚未返回", PRIMARY), lpTop(8));
        } else {
            for (int i = 0; i < risks.length(); i++) {
                JSONObject risk = risks.optJSONObject(i);
                body.addView(priorityRow(risk == null ? "risk" : risk.optString("type", "risk"), risk == null ? "" : risk.optString("message"), ORANGE), lpTop(8));
            }
        }

        section(body, "任务候选", null);
        JSONArray candidates = lastRun == null ? null : lastRun.optJSONArray("tasks");
        if (candidates == null || candidates.length() == 0) {
            body.addView(checkRow("暂无任务候选"), lpTop(8));
        } else {
            for (int i = 0; i < candidates.length(); i++) {
                JSONObject task = candidates.optJSONObject(i);
                body.addView(checkRow(task == null ? "任务候选" : task.optString("title", "任务候选")), lpTop(8));
            }
        }

        body.addView(primaryButton("保存为任务", v -> saveRunTasks()), lpTop(18));
        body.addView(secondaryButton("继续追问", v -> go(Screen.COMMAND_CENTER)), lpTop(10));
        body.addView(secondaryButton("生成结构化任务分解", v -> go(Screen.DISPATCH)), lpTop(10));
    }

    private void renderProjects(LinearLayout body) {
        title(body, "项目", "管理 Codex 可分析的项目路径");
        body.addView(infoCard("当前项目路径", projectPath, GREEN), lpTop(16));
        section(body, "项目列表", null);
        body.addView(infoCard("未接入真实项目列表", "后端只会接受允许路径内的 projectPath；项目列表 API 接入后再展示多个项目。", MUTED), lpTop(10));
        section(body, "常用命令", null);
        twoButtons(body,
                "/plan 下一步",
                v -> runCommand("/plan 分析 " + projectPath + " 下一步"),
                "/bug 当前项目",
                v -> runCommand("/bug 找 " + projectPath + " 最严重的问题"));
        body.addView(secondaryButton("/review android 前端", v -> runCommand("/review android 前端 " + projectPath)), lpTop(10));
    }

    private void renderProjectDetail(LinearLayout body) {
        title(body, "项目详情", "项目路径、访问规则、常用 Codex 命令");
        LinearLayout hero = card(DARK, DARK);
        hero.addView(text("当前项目", 22, Color.WHITE, true));
        hero.addView(text(projectPath, 13, Color.rgb(203, 213, 225), false));
        addChips(hero, new String[]{"路径白名单", "只读默认"});
        body.addView(hero, lpTop(16));
        section(body, "一键分析", null);
        grid(body, new Action[]{
                new Action("下一步计划", PRIMARY, "/plan 分析 " + projectPath + " 下一步"),
                new Action("Bug Review", RED, "/bug 找 " + projectPath + " 最严重的问题"),
                new Action("架构 Review", PURPLE, "/review 架构分析 " + projectPath),
                new Action("读取日志", ORANGE, "/log 读取 " + projectPath + " 最近日志")
        });
        body.addView(infoCard("最近项目判断", lastRun == null ? "暂无真实项目分析结果" : lastRun.optString("summary", "暂无摘要"), PRIMARY), lpTop(18));
        body.addView(primaryButton("继续分析这个项目", v -> go(Screen.CODEX_CONFIRM)), lpTop(18));
    }

    private void renderCodexConfirm(LinearLayout body) {
        title(body, "Codex 执行确认", "分析默认只读，写代码前必须二次确认");
        body.addView(infoCard("权限边界", "允许读取项目并生成计划；禁止自动写代码。", ORANGE), lpTop(16));
        section(body, "本次操作", null);
        body.addView(permissionRow("读取项目结构", "允许", GREEN), lpTop(8));
        body.addView(permissionRow("读取最近日志", "允许", GREEN), lpTop(8));
        body.addView(permissionRow("运行测试命令", "需确认", ORANGE), lpTop(8));
        body.addView(permissionRow("修改代码文件", "二次确认", RED), lpTop(8));
        body.addView(primaryButton("只读分析", v -> runCommand("/plan 分析 " + projectPath + " 下一步")), lpTop(18));
        body.addView(secondaryButton("允许运行测试，但每次确认", v -> runCommand("/bug 找 " + projectPath + " 最严重的问题")), lpTop(10));
        body.addView(secondaryButton("取消", v -> go(Screen.PROJECT_DETAIL)), lpTop(10));
    }

    private void renderTasks(LinearLayout body) {
        title(body, "任务", "所有 Agent 结果最终沉淀为可执行任务");
        addChips(body, new String[]{"今日", "本周", "项目", "来源", "状态"});
        section(body, "今日任务", "新建");
        if (tasks.length() == 0) {
            body.addView(infoCard("暂无后端任务", "点击刷新，或先在命令中心生成并保存任务。", MUTED), lpTop(10));
        } else {
            for (int i = 0; i < tasks.length(); i++) {
                JSONObject task = tasks.optJSONObject(i);
                if (task == null) {
                    continue;
                }
                String priority = task.optString("priority", "medium");
                int color = "high".equals(priority) ? ORANGE : Color.rgb(234, 179, 8);
                body.addView(taskRow(task.optString("title", "未命名任务"),
                        task.optString("source", "android") + " · " + task.optString("status", "todo"),
                        priority,
                        color), lpTop(10));
            }
        }
        body.addView(primaryButton("刷新任务", v -> loadTasks()), lpTop(14));
        section(body, "来源统计", null);
        body.addView(infoCard("真实任务数量", "当前后端返回 " + tasks.length() + " 条任务。来源聚合接口尚未接入。", PRIMARY), lpTop(8));
    }

    private void renderTaskDetail(LinearLayout body) {
        title(body, "新建任务", "任务必须来自用户输入、AgentRun 结果或 OCR 结果");
        LinearLayout editor = card();
        label(editor, "任务标题");
        EditText taskInput = field("");
        editor.addView(taskInput, new LinearLayout.LayoutParams(-1, dp(52)));
        body.addView(editor, lpTop(16));
        section(body, "来源证据", null);
        body.addView(infoCard("用户原话", "暂无真实来源证据", TEXT), lpTop(8));
        section(body, "AI 建议", null);
        body.addView(infoCard("建议", "暂无真实 AI 建议，请先从命令中心创建 AgentRun。", PRIMARY), lpTop(8));
        body.addView(primaryButton("创建到后端任务", v -> createTask(taskInput.getText().toString())), lpTop(18));
        body.addView(secondaryButton("继续追问", v -> go(Screen.COMMAND_CENTER)), lpTop(10));
        body.addView(secondaryButton("查看关联运行", v -> go(Screen.AGENT_RUN)), lpTop(10));
    }

    private void renderKnowledge(LinearLayout body) {
        title(body, "知识库", "记住项目目标、决策、论文、群总结");
        LinearLayout search = card();
        EditText memoryInput = field("");
        search.addView(text("新增长期记忆", 15, MUTED, false));
        search.addView(memoryInput, new LinearLayout.LayoutParams(-1, dp(52)));
        search.addView(primaryButton("保存到知识库", v -> saveMemory("产品决策", memoryInput.getText().toString())), lpTop(8));
        body.addView(search, lpTop(16));
        section(body, "关键记忆", null);
        if (memories.length() == 0) {
            body.addView(infoCard("暂无后端知识库数据", "保存真实决策或刷新后再展示。", MUTED), lpTop(10));
        } else {
            for (int i = 0; i < memories.length(); i++) {
                JSONObject memory = memories.optJSONObject(i);
                if (memory != null) {
                    int color = i % 3 == 0 ? PRIMARY : i % 3 == 1 ? GREEN : ORANGE;
                    body.addView(memoryRow(memory.optString("title", "记忆"), memory.optString("content", ""), color), lpTop(10));
                }
            }
        }
        body.addView(primaryButton("刷新知识库", v -> loadMemory()), lpTop(18));
        body.addView(secondaryButton("/remember 通过命令保存", v -> runCommand("/remember " + memoryInput.getText().toString())), lpTop(10));
    }

    private void renderResearch(LinearLayout body) {
        title(body, "论文收集", "Research Agent：主题、排序、贡献点、阅读任务");
        body.addView(infoCard("研究主题", "benchmark 测评相关论文\n以真实检索接口返回为准", PURPLE), lpTop(16));
        body.addView(primaryButton("开始收集论文", v -> runCommand("/paper 收集 benchmark 测评相关论文，整理贡献点和阅读任务")), lpTop(12));
        section(body, "最近报告", null);
        JSONArray papers = lastRun == null ? null : lastRun.optJSONArray("papers");
        if (papers == null || papers.length() == 0) {
            body.addView(infoCard("暂无真实论文结果", lastRun == null ? "点击开始收集后展示真实检索状态。" : lastRun.optString("summary"), MUTED), lpTop(10));
        } else {
            for (int i = 0; i < papers.length(); i++) {
                JSONObject paper = papers.optJSONObject(i);
                if (paper != null) {
                    body.addView(paperRow(paper.optString("title", "论文"), paper.optString("contribution", ""), PURPLE), lpTop(10));
                }
            }
        }
    }

    private void renderPaperDetail(LinearLayout body) {
        title(body, "论文详情", "摘要、贡献点、对比结论、一键转任务");
        JSONArray papers = lastRun == null ? null : lastRun.optJSONArray("papers");
        JSONObject first = papers == null || papers.length() == 0 ? null : papers.optJSONObject(0);
        body.addView(infoCard(first == null ? "论文收集结果" : first.optString("title", "论文"),
                first == null ? (lastRun == null ? "还没有后端论文结果" : lastRun.optString("summary")) : first.optString("year") + " · " + first.optString("venue") + "\n" + first.optString("url"),
                PURPLE), lpTop(16));
        section(body, "贡献点", null);
        if (papers == null || papers.length() == 0) {
            body.addView(checkRow("点击论文收集后生成贡献点和阅读任务"), lpTop(8));
        } else {
            for (int i = 0; i < papers.length(); i++) {
                JSONObject paper = papers.optJSONObject(i);
                if (paper != null) {
                    body.addView(checkRow(paper.optString("contribution", paper.optString("title"))), lpTop(8));
                }
            }
        }
        body.addView(primaryButton("生成阅读任务", v -> saveRunTasks()), lpTop(18));
        body.addView(secondaryButton("加入知识库", v -> go(Screen.KNOWLEDGE)), lpTop(10));
        body.addView(secondaryButton("继续收集同类论文", v -> go(Screen.RESEARCH)), lpTop(10));
    }

    private void renderMessageDigest(LinearLayout body) {
        title(body, "消息总结", "飞书群消息或聊天截图，输出摘要/决策/任务/风险");
        LinearLayout source = card();
        label(source, "选择来源");
        addChips(source, new String[]{"飞书群", "上传截图", "粘贴文本"});
        body.addView(source, lpTop(16));
        body.addView(infoCard("消息来源", "飞书群内发送 /digest 会读取当前群最近文本消息；Android 端可粘贴真实文本或用 OCR 原文分发。", TEAL), lpTop(12));
        body.addView(primaryButton("去命令中心粘贴真实消息", v -> go(Screen.COMMAND_CENTER)), lpTop(18));
        section(body, "会输出什么", null);
        body.addView(commandRow("摘要：今天聊了什么", Screen.DIGEST_RESULT), lpTop(8));
        body.addView(commandRow("决策：已经定下的方向", Screen.DIGEST_RESULT), lpTop(8));
        body.addView(commandRow("任务：谁负责什么", Screen.DIGEST_RESULT), lpTop(8));
        body.addView(commandRow("风险：阻塞和争议", Screen.DIGEST_RESULT), lpTop(8));
        body.addView(commandRow("next step：下一步动作", Screen.DIGEST_RESULT), lpTop(8));
    }

    private void renderDigestResult(LinearLayout body) {
        title(body, "群总结结果", "摘要、决策、任务、风险、下一步");
        body.addView(infoCard("今日群总结", lastRun == null ? "还没有群总结结果" : lastRun.optString("summary"), TEAL), lpTop(16));
        section(body, "决策", null);
        JSONArray nextSteps = lastRun == null ? null : lastRun.optJSONArray("nextSteps");
        if (nextSteps == null || nextSteps.length() == 0) {
            body.addView(infoCard("暂无真实决策", "创建群总结 AgentRun 后再展示。", MUTED), lpTop(8));
        } else {
            for (int i = 0; i < nextSteps.length(); i++) {
                body.addView(commandRow(nextSteps.optString(i), Screen.TASK_DETAIL), lpTop(8));
            }
        }
        section(body, "任务", null);
        JSONArray candidates = lastRun == null ? null : lastRun.optJSONArray("tasks");
        if (candidates != null) {
            for (int i = 0; i < candidates.length(); i++) {
                JSONObject task = candidates.optJSONObject(i);
                body.addView(commandRow(task == null ? "任务" : task.optString("title", "任务"), Screen.TASK_DETAIL), lpTop(8));
            }
        }
        section(body, "风险", null);
        JSONArray risks = lastRun == null ? null : lastRun.optJSONArray("risks");
        if (risks == null || risks.length() == 0) {
            body.addView(infoCard("暂无真实风险", "后端没有返回风险项。", MUTED), lpTop(8));
        } else {
            for (int i = 0; i < risks.length(); i++) {
                JSONObject risk = risks.optJSONObject(i);
                body.addView(infoCard(risk == null ? "风险" : risk.optString("type", "风险"),
                        risk == null ? "" : risk.optString("message", ""), ORANGE), lpTop(8));
            }
        }
        body.addView(primaryButton("保存任务", v -> saveRunTasks()), lpTop(18));
        body.addView(secondaryButton("生成日报", v -> go(Screen.KNOWLEDGE)), lpTop(10));
    }

    private void renderOcrTool(LinearLayout body) {
        title(body, "工具 · CV / OCR", "辅助输入：图片 -> 结构化信息 -> 分发给 Agent");
        LinearLayout upload = card();
        TextView mark = text("图", 28, ORANGE, true);
        mark.setGravity(Gravity.CENTER);
        upload.addView(mark);
        upload.addView(text("上传截图、白板、论文图、任务截图", 16, TEXT, true), lpTop(8));
        body.addView(upload, lpTop(16));
        body.addView(accentButton("选择图片并上传", ORANGE, v -> pickImage()), lpTop(12));
        if (lastVisionUpload != null) {
            body.addView(infoCard("最近上传", "traceId: " + lastVisionUpload.optString("traceId") + "\nstatus: " + lastVisionUpload.optString("status"), ORANGE), lpTop(12));
        }
        section(body, "识别输出", null);
        body.addView(fieldRow("plainText", "图片中的完整文字"), lpTop(8));
        body.addView(fieldRow("blocks", "带坐标的文本块"), lpTop(8));
        body.addView(fieldRow("entities", "任务 / 日期 / 链接 / 人名"), lpTop(8));
        body.addView(fieldRow("contextType", "聊天 / 论文 / 网页 / 表格"), lpTop(8));
        section(body, "下一步分发", null);
        addChips(body, new String[]{"群总结", "论文收集", "创建任务", "项目分析"});
    }

    private void renderDispatch(LinearLayout body) {
        title(body, "截图分发", "OCR 完成后自动分发给合适 Agent");
        String dispatchInfo = lastVisionResult == null
                ? "还没有 OCR 结果"
                : "traceId: " + lastVisionResult.optString("traceId")
                + "\nstatus: " + lastVisionResult.optString("status")
                + " · progress: " + lastVisionResult.optInt("progress")
                + "\n任务候选：" + arrayLength(lastVisionResult.optJSONArray("tasks"));
        body.addView(infoCard("识别结果", dispatchInfo, GREEN), lpTop(16));
        section(body, "选择下一步", null);
        body.addView(dispatchActionRow("交给群消息总结", "把 OCR 原文作为真实输入创建 Digest AgentRun", TEAL, v -> runDigestFromVision()), lpTop(10));
        body.addView(dispatchActionRow("直接创建任务", "保存 OCR 任务候选", GREEN, v -> saveVisionTasks()), lpTop(10));
        body.addView(dispatchActionRow("交给项目分析", "把 OCR 原文作为项目证据提交 AgentRun", PRIMARY, v -> runProjectFromVision()), lpTop(10));
        body.addView(dispatchActionRow("保存到知识库", "将 OCR 原文写入长期记忆", PURPLE, v -> saveVisionToMemory()), lpTop(10));
        body.addView(secondaryButton("刷新 OCR 结果", v -> refreshVisionResult()), lpTop(14));
        body.addView(primaryButton("保存 OCR 任务候选", v -> saveVisionTasks()), lpTop(18));
    }

    private void renderFeishu(LinearLayout body) {
        title(body, "飞书连接", "Android 与飞书机器人共享同一 Command Router");
        String feishuStatus = readiness == null
                ? "尚未检测"
                : "botRegistered=" + readiness.optBoolean("botRegistered")
                + "\ntokenValid=" + readiness.optBoolean("tokenValid")
                + "\neventCallbackVerified=" + readiness.optBoolean("eventCallbackVerified");
        body.addView(infoCard("机器人状态", feishuStatus, readiness != null && readiness.optBoolean("botRegistered") ? GREEN : ORANGE), lpTop(16));
        section(body, "可用入口", null);
        body.addView(commandRow("@视流助手 命令", Screen.COMMAND_CENTER), lpTop(8));
        body.addView(commandRow("飞书图片消息", Screen.OCR_TOOL), lpTop(8));
        body.addView(commandRow("群消息总结", Screen.MESSAGE_DIGEST), lpTop(8));
        body.addView(primaryButton("检测连接", v -> refreshOverview()), lpTop(18));
        section(body, "机器人注册", null);
        LinearLayout register = card();
        label(register, "Bot 名称");
        EditText botNameInput = field("");
        register.addView(botNameInput, new LinearLayout.LayoutParams(-1, dp(52)));
        label(register, "App ID");
        EditText appIdInput = field("");
        register.addView(appIdInput, new LinearLayout.LayoutParams(-1, dp(52)));
        label(register, "App Secret");
        EditText appSecretInput = field("");
        register.addView(appSecretInput, new LinearLayout.LayoutParams(-1, dp(52)));
        label(register, "Verification Token");
        EditText verificationTokenInput = field("");
        register.addView(verificationTokenInput, new LinearLayout.LayoutParams(-1, dp(52)));
        label(register, "Encrypt Key");
        EditText encryptKeyInput = field("");
        register.addView(encryptKeyInput, new LinearLayout.LayoutParams(-1, dp(52)));
        label(register, "租户名称");
        EditText tenantNameInput = field("");
        register.addView(tenantNameInput, new LinearLayout.LayoutParams(-1, dp(52)));
        body.addView(register, lpTop(12));
        body.addView(primaryButton("注册机器人", v -> registerFeishuBot(
                botNameInput.getText().toString(),
                appIdInput.getText().toString(),
                appSecretInput.getText().toString(),
                verificationTokenInput.getText().toString(),
                encryptKeyInput.getText().toString(),
                tenantNameInput.getText().toString()
        )), lpTop(12));

        section(body, "回调地址", null);
        String botId = readiness == null ? "" : readiness.optString("botId", "");
        String callbackText = readiness == null
                ? "刷新 readiness 后显示"
                : "botId: " + botId
                + "\n事件回调: " + readiness.optString("callbackUrl")
                + "\n卡片回调: " + readiness.optString("cardCallbackUrl");
        body.addView(infoCard("开放平台填写", callbackText, PRIMARY), lpTop(8));
        body.addView(secondaryButton("更新回调 Token", v -> updateFeishuCallback(
                botId,
                verificationTokenInput.getText().toString(),
                encryptKeyInput.getText().toString()
        )), lpTop(10));
        if (botRegistration != null) {
            body.addView(infoCard("最近注册结果",
                    "botId: " + botRegistration.optString("botId")
                            + "\n事件回调: " + botRegistration.optString("callbackUrl")
                            + "\n卡片回调: " + botRegistration.optString("cardCallbackUrl"),
                    GREEN), lpTop(10));
        }
        body.addView(secondaryButton("查看事件回调日志", v -> go(Screen.RUN_LOGS)), lpTop(10));
    }

    private void renderSettings(LinearLayout body) {
        title(body, "设置", "后端地址、Codex 权限、飞书、数据清理");
        LinearLayout settings = card();
        label(settings, "后端地址");
        EditText baseUrlInput = field(backendBaseUrl);
        settings.addView(baseUrlInput, new LinearLayout.LayoutParams(-1, dp(52)));
        label(settings, "Admin Token");
        EditText tokenInput = field(adminToken);
        settings.addView(tokenInput, new LinearLayout.LayoutParams(-1, dp(52)));
        label(settings, "项目路径");
        EditText pathInput = field(projectPath);
        settings.addView(pathInput, new LinearLayout.LayoutParams(-1, dp(52)));
        body.addView(settings, lpTop(16));
        body.addView(primaryButton("保存并检测", v -> {
            saveSettings(baseUrlInput.getText().toString(), tokenInput.getText().toString(), pathInput.getText().toString());
            refreshOverview();
        }), lpTop(12));
        body.addView(infoCard("当前状态", statusText, TEXT), lpTop(10));
        section(body, "权限与连接", null);
        body.addView(checkRow("Codex 项目权限 · workspace-write / 只读默认"), lpTop(8));
        body.addView(checkRow("飞书机器人 · 以 readiness 接口为准"), lpTop(8));
        body.addView(checkRow("图片上传 · 仅用户主动选择"), lpTop(8));
        body.addView(checkRow("论文检索 · 未接入真实数据源时不展示结果"), lpTop(8));
        body.addView(secondaryButton("飞书连接配置", v -> go(Screen.FEISHU)), lpTop(12));
        section(body, "数据", null);
        body.addView(secondaryButton("清理运行日志", v -> go(Screen.RUN_LOGS)), lpTop(8));
        body.addView(secondaryButton("导出任务和知识库", v -> go(Screen.KNOWLEDGE)), lpTop(8));
    }

    private void renderLogs(LinearLayout body) {
        title(body, "运行日志", "手机侧查看后端、飞书、Codex Agent 状态");
        addChips(body, new String[]{"全部", "Codex", "飞书", "OCR"});
        body.addView(logBox(appLogs.isEmpty() ? "暂无手机侧日志" : String.join("\n", appLogs)), lpTop(16));
        twoButtons(body, "复制日志", v -> go(Screen.RUN_LOGS), "报告问题", v -> go(Screen.COMMAND_CENTER));
    }

    private void addBottomNav(LinearLayout root) {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(6), dp(8), dp(6));
        nav.setBackground(rounded(SURFACE, LINE, 0));
        root.addView(nav, new LinearLayout.LayoutParams(-1, dp(64)));
        navItem(nav, "工作台", Tab.WORKBENCH, Screen.WORKBENCH);
        navItem(nav, "任务", Tab.TASKS, Screen.TASKS);
        navItem(nav, "知识", Tab.KNOWLEDGE, Screen.KNOWLEDGE);
        navItem(nav, "项目", Tab.PROJECTS, Screen.PROJECTS);
        navItem(nav, "设置", Tab.SETTINGS, Screen.SETTINGS);
    }

    private void navItem(LinearLayout nav, String label, Tab tab, Screen screen) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setClickable(true);
        item.setOnClickListener(v -> {
            backStack.clear();
            render(screen, tab, false);
        });
        int color = currentTab == tab ? PRIMARY : Color.rgb(203, 213, 225);
        TextView dot = text("●", 18, color, true);
        dot.setGravity(Gravity.CENTER);
        TextView title = text(label, 11, currentTab == tab ? PRIMARY : MUTED, currentTab == tab);
        title.setGravity(Gravity.CENTER);
        item.addView(dot);
        item.addView(title);
        nav.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
    }

    private void title(LinearLayout body, String title, String subtitle) {
        body.addView(text(title, 28, TEXT, true));
        body.addView(text(subtitle, 13, MUTED, false), lpTop(6));
    }

    private void section(LinearLayout body, String left, String right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text(left, 17, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        if (right != null) {
            row.addView(text(right, 12, PRIMARY, true));
        }
        body.addView(row, lpTop(22));
    }

    private LinearLayout card() {
        return card(SURFACE, LINE);
    }

    private LinearLayout card(int color, int stroke) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(rounded(color, stroke, 8));
        return card;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setIncludeFontPadding(true);
        view.setLineSpacing(dp(2), 1f);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    private void label(LinearLayout parent, String value) {
        parent.addView(text(value, 13, MUTED, true), lpBottom(8));
    }

    private void rowText(LinearLayout parent, String title, String body, int accent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.addView(text(title, 12, accent, true));
        row.addView(text(body, 16, TEXT, true), lpTop(4));
        parent.addView(row, lpTop(parent.getChildCount() == 0 ? 0 : 12));
    }

    private View infoCard(String title, String detail, int accent) {
        LinearLayout card = card();
        card.addView(text(title, 14, accent, true));
        card.addView(text(detail, 15, TEXT, false), lpTop(6));
        return card;
    }

    private View commandRow(String value, Screen target) {
        LinearLayout row = card();
        row.setClickable(true);
        row.setOnClickListener(v -> go(target));
        row.addView(text(value, 14, TEXT, true));
        return row;
    }

    private View commandRow(String value, Runnable action) {
        LinearLayout row = card();
        row.setClickable(true);
        row.setOnClickListener(v -> action.run());
        row.addView(text(value, 14, TEXT, true));
        return row;
    }

    private View runRow(String type, String detail, int color, Screen target) {
        LinearLayout row = card();
        row.setClickable(true);
        row.setOnClickListener(v -> go(target));
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        TextView dot = text("●", 18, color, true);
        line.addView(dot);
        line.addView(text(type, 14, TEXT, true), lpLeft(8));
        line.addView(text(detail, 13, MUTED, false), lpLeft(14));
        row.addView(line);
        return row;
    }

    private View step(String title, int color, boolean active) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text("●", 20, color, true));
        row.addView(text(title, 15, active ? TEXT : MUTED, true), lpLeft(10));
        return row;
    }

    private View priorityRow(String tag, String title, int color) {
        LinearLayout row = card();
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.addView(chip(tag, Color.rgb(255, 237, 213), color));
        line.addView(text(title, 14, TEXT, true), lpLeft(10));
        row.addView(line);
        return row;
    }

    private View checkRow(String title) {
        LinearLayout row = card();
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.addView(text("●", 18, GREEN, true));
        line.addView(text(title, 14, TEXT, true), lpLeft(10));
        row.addView(line);
        return row;
    }

    private View permissionRow(String title, String status, int color) {
        LinearLayout row = card();
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.addView(text(title, 14, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        line.addView(chip(status, tint(color, 0.12f), color));
        row.addView(line);
        return row;
    }

    private View taskRow(String title, String meta, String priority, int color) {
        LinearLayout row = card();
        row.setClickable(true);
        row.setOnClickListener(v -> go(Screen.TASK_DETAIL));
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.TOP);
        line.addView(chip(priority, tint(color, 0.12f), color));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(title, 15, TEXT, true));
        texts.addView(text(meta, 12, MUTED, false), lpTop(3));
        line.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(line);
        return row;
    }

    private View projectCard(String title, String path, String meta, int color) {
        LinearLayout row = card();
        row.setClickable(true);
        row.setOnClickListener(v -> go(Screen.PROJECT_DETAIL));
        row.addView(text("●  " + title, 17, color, true));
        row.addView(text(path, 12, MUTED, false), lpTop(4));
        row.addView(text(meta, 12, PRIMARY, true), lpTop(4));
        return row;
    }

    private View memoryRow(String title, String detail, int color) {
        LinearLayout row = card();
        row.addView(text("●  " + title, 15, color, true));
        row.addView(text(detail, 12, MUTED, false), lpTop(4));
        return row;
    }

    private View paperRow(String title, String detail, int color) {
        LinearLayout row = card();
        row.setClickable(true);
        row.setOnClickListener(v -> go(Screen.PAPER_DETAIL));
        row.addView(text(title, 16, TEXT, true));
        row.addView(text(detail, 12, color, true), lpTop(4));
        return row;
    }

    private View fieldRow(String key, String value) {
        LinearLayout row = card();
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.addView(text(key, 13, ORANGE, true), new LinearLayout.LayoutParams(dp(96), -2));
        line.addView(text(value, 13, MUTED, false), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(line);
        return row;
    }

    private View dispatchRow(String title, String detail, int color, Screen target) {
        LinearLayout row = card();
        row.setClickable(true);
        row.setOnClickListener(v -> go(target));
        row.addView(text("●  " + title, 15, color, true));
        row.addView(text(detail, 12, MUTED, false), lpTop(4));
        return row;
    }

    private View dispatchActionRow(String title, String detail, int color, View.OnClickListener listener) {
        LinearLayout row = card();
        row.setClickable(true);
        row.setOnClickListener(listener);
        row.addView(text("●  " + title, 15, color, true));
        row.addView(text(detail, 12, MUTED, false), lpTop(4));
        return row;
    }

    private View agentLane(String name, String detail, String status, int color) {
        LinearLayout row = card();
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.addView(text("●", 18, color, true));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(name, 14, TEXT, true));
        copy.addView(text(detail, 12, MUTED, false), lpTop(2));
        line.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        line.addView(chip(status, tint(color, 0.12f), color));
        row.addView(line);
        return row;
    }

    private View logBox(String content) {
        TextView view = text(content, 13, Color.rgb(226, 232, 240), false);
        view.setTypeface(Typeface.MONOSPACE);
        view.setPadding(dp(12), dp(12), dp(12), dp(12));
        view.setBackground(rounded(DARK, DARK, 8));
        return view;
    }

    private EditText field(String value) {
        EditText editText = new EditText(this);
        editText.setText(value);
        editText.setTextSize(14);
        editText.setSingleLine(true);
        editText.setTextColor(TEXT);
        editText.setBackground(rounded(SURFACE, LINE, 8));
        editText.setPadding(dp(12), 0, dp(12), 0);
        return editText;
    }

    private String logText(JSONObject run) {
        if (run == null) {
            return appLogs.isEmpty() ? "等待创建 AgentRun..." : String.join("\n", appLogs);
        }
        JSONArray logs = run.optJSONArray("logs");
        if (logs == null || logs.length() == 0) {
            return String.join("\n", appLogs);
        }
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < logs.length(); i++) {
            lines.add(logs.optString(i));
        }
        return String.join("\n", lines);
    }

    private int arrayLength(JSONArray array) {
        return array == null ? 0 : array.length();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void addChips(LinearLayout parent, String[] values) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, 0);
        for (String value : values) {
            row.addView(chip(value, Color.rgb(239, 246, 255), PRIMARY), lpRight(8));
        }
        scroll.addView(row, new HorizontalScrollView.LayoutParams(-2, -2));
        parent.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
    }

    private TextView chip(String label, int bg, int color) {
        TextView chip = text(label, 11, color, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), dp(5), dp(10), dp(5));
        chip.setBackground(rounded(bg, bg, 16));
        return chip;
    }

    private void grid(LinearLayout body, Action[] actions) {
        for (int i = 0; i < actions.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.addView(actionCard(actions[i]), new LinearLayout.LayoutParams(0, dp(52), 1));
            if (i + 1 < actions.length) {
                row.addView(space(dp(10), 1));
                row.addView(actionCard(actions[i + 1]), new LinearLayout.LayoutParams(0, dp(52), 1));
            }
            body.addView(row, lpTop(10));
        }
    }

    private View actionCard(Action action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), 0, dp(12), 0);
        card.setBackground(rounded(SURFACE, LINE, 8));
        card.setClickable(true);
        card.setOnClickListener(v -> {
            if (!isBlank(action.command)) {
                runCommand(action.command);
            } else {
                go(action.target);
            }
        });
        card.addView(text("●", 18, action.color, true));
        card.addView(text(action.title, 13, TEXT, true), lpLeft(8));
        return card;
    }

    private MaterialButton primaryButton(String title, View.OnClickListener listener) {
        return button(title, PRIMARY, Color.WHITE, PRIMARY, listener);
    }

    private MaterialButton accentButton(String title, int color, View.OnClickListener listener) {
        return button(title, color, Color.WHITE, color, listener);
    }

    private MaterialButton secondaryButton(String title, View.OnClickListener listener) {
        return button(title, SURFACE, TEXT, LINE, listener);
    }

    private MaterialButton button(String title, int bg, int fg, int stroke, View.OnClickListener listener) {
        MaterialButton button = new MaterialButton(this);
        button.setText(title);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setCornerRadius(dp(8));
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setTextColor(fg);
        button.setBackgroundTintList(ColorStateList.valueOf(bg));
        button.setStrokeColor(ColorStateList.valueOf(stroke));
        button.setStrokeWidth(bg == SURFACE ? dp(1) : 0);
        button.setOnClickListener(listener);
        button.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(52)));
        return button;
    }

    private void twoButtons(LinearLayout body, String left, View.OnClickListener leftClick, String right, View.OnClickListener rightClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(secondaryButton(left, leftClick), new LinearLayout.LayoutParams(0, dp(48), 1));
        row.addView(space(dp(10), 1));
        row.addView(secondaryButton(right, rightClick), new LinearLayout.LayoutParams(0, dp(48), 1));
        body.addView(row, lpTop(10));
    }

    private View space(int width, int height) {
        Space space = new Space(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(width, height));
        return space;
    }

    private GradientDrawable rounded(int color, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (stroke != color) {
            drawable.setStroke(dp(1), stroke);
        }
        return drawable;
    }

    private int tint(int color, float amount) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        return Color.rgb(
                (int) (255 - (255 - r) * amount),
                (int) (255 - (255 - g) * amount),
                (int) (255 - (255 - b) * amount)
        );
    }

    private LinearLayout.LayoutParams lpTop(int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(top);
        return params;
    }

    private LinearLayout.LayoutParams lpBottom(int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(bottom);
        return params;
    }

    private LinearLayout.LayoutParams lpLeft(int left) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.leftMargin = dp(left);
        return params;
    }

    private LinearLayout.LayoutParams lpRight(int right) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.rightMargin = dp(right);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void go(Screen screen) {
        render(screen, tabFor(screen), true);
    }

    private Tab tabFor(Screen screen) {
        return switch (screen) {
            case TASKS, TASK_DETAIL -> Tab.TASKS;
            case KNOWLEDGE, RESEARCH, PAPER_DETAIL, MESSAGE_DIGEST, DIGEST_RESULT -> Tab.KNOWLEDGE;
            case PROJECTS, PROJECT_DETAIL, CODEX_CONFIRM -> Tab.PROJECTS;
            case SETTINGS, RUN_LOGS, FEISHU -> Tab.SETTINGS;
            default -> Tab.WORKBENCH;
        };
    }

    private static final class Action {
        final String title;
        final int color;
        final Screen target;
        final String command;

        Action(String title, int color, Screen target) {
            this.title = title;
            this.color = color;
            this.target = target;
            this.command = null;
        }

        Action(String title, int color, String command) {
            this.title = title;
            this.color = color;
            this.target = null;
            this.command = command;
        }
    }
}
