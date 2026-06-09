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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity {
    private static final int BG = Color.rgb(242, 238, 229);
    private static final int SURFACE = Color.rgb(255, 252, 246);
    private static final int TEXT = Color.rgb(27, 34, 32);
    private static final int MUTED = Color.rgb(99, 110, 106);
    private static final int LINE = Color.rgb(225, 216, 201);
    private static final int PRIMARY = Color.rgb(18, 105, 91);
    private static final int PRIMARY_DARK = Color.rgb(28, 52, 48);
    private static final int GREEN = Color.rgb(42, 137, 85);
    private static final int ORANGE = Color.rgb(190, 91, 45);
    private static final int BLUE = Color.rgb(40, 111, 150);
    private static final int RED = Color.rgb(193, 55, 55);
    private static final String DEFAULT_BACKEND_BASE_URL = BuildConfig.SHILIU_DEFAULT_BACKEND_URL;
    private static final String DEFAULT_ADMIN_TOKEN = BuildConfig.SHILIU_DEFAULT_ADMIN_TOKEN;
    private static final String DEFAULT_PROJECT_PATH = BuildConfig.SHILIU_DEFAULT_PROJECT_PATH;
    private static final String BOT_ID = "bot_20260528_dd2222539100";
    private static final String BOT_NAME = "test";
    private static final String FEISHU_TOKEN = "replace-with-feishu-verification-token";
    private static final String FEISHU_EVENT_URL = DEFAULT_BACKEND_BASE_URL + "/feishu/events/" + BOT_ID;
    private static final String FEISHU_CARD_URL = DEFAULT_BACKEND_BASE_URL + "/feishu/card-callback/" + BOT_ID;

    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final List<String> logs = new ArrayList<>();
    private ActivityResultLauncher<Intent> imagePicker;
    private Tab currentTab = Tab.HOME;
    private String backendBaseUrl = DEFAULT_BACKEND_BASE_URL;
    private String adminToken = DEFAULT_ADMIN_TOKEN;
    private String projectPath = DEFAULT_PROJECT_PATH;
    private String statusText = "准备就绪";
    private JSONObject health;
    private JSONObject readiness;
    private JSONObject overview;
    private JSONObject botHealth;
    private JSONObject lastRun;
    private JSONObject lastUpload;
    private JSONObject lastVision;
    private JSONArray tasks = new JSONArray();

    private enum Tab {
        HOME, VISION, TASKS, FEISHU, SETTINGS
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
        render(Tab.HOME);
        refreshAll(false);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences("shiliu_ai", MODE_PRIVATE);
        backendBaseUrl = prefs.getString("backendBaseUrl", DEFAULT_BACKEND_BASE_URL);
        adminToken = prefs.getString("adminToken", DEFAULT_ADMIN_TOKEN);
        projectPath = prefs.getString("projectPath", DEFAULT_PROJECT_PATH);
    }

    private void saveSettings(String baseUrl, String token, String path) {
        backendBaseUrl = blank(baseUrl) ? DEFAULT_BACKEND_BASE_URL : baseUrl.trim();
        adminToken = blank(token) ? DEFAULT_ADMIN_TOKEN : token.trim();
        projectPath = blank(path) ? DEFAULT_PROJECT_PATH : path.trim();
        getSharedPreferences("shiliu_ai", MODE_PRIVATE)
                .edit()
                .putString("backendBaseUrl", backendBaseUrl)
                .putString("adminToken", adminToken)
                .putString("projectPath", projectPath)
                .apply();
        statusText = "设置已保存";
        log("settings saved");
        render(currentTab);
        refreshAll(false);
    }

    private ShiliuApiClient api() {
        return new ShiliuApiClient(backendBaseUrl, adminToken);
    }

    private void render(Tab tab) {
        currentTab = tab;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(18), dp(18), dp(18));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        if (tab == Tab.HOME) {
            renderHome(body);
        } else if (tab == Tab.VISION) {
            renderVision(body);
        } else if (tab == Tab.TASKS) {
            renderTasks(body);
        } else if (tab == Tab.FEISHU) {
            renderFeishu(body);
        } else {
            renderSettings(body);
        }

        addBottomNav(root);
        setContentView(root);
    }

    private void renderHome(LinearLayout body) {
        title(body, "视流 AI", "一个清晰入口：项目分析、截图 OCR、任务和飞书机器人");
        body.addView(hero("内部展示版", homeSummary()), lpTop(16));
        body.addView(statusCard(), lpTop(12));

        section(body, "核心操作");
        actionGrid(body,
                action("分析项目", "只读扫描当前项目", PRIMARY, () -> runAgent("/plan 分析 " + projectPath + " 下一步")),
                action("找问题", "输出风险和任务", ORANGE, () -> runAgent("/bug 找 " + projectPath + " 最严重的问题")),
                action("上传截图", "OCR 后自动分发", BLUE, this::pickImage),
                action("刷新状态", "后端 / OCR / 飞书", GREEN, () -> refreshAll(true))
        );

        if (lastRun != null) {
            section(body, "最近结果");
            body.addView(cardText(lastRun.optString("module", "AgentRun"), lastRun.optString("summary", ""), PRIMARY), lpTop(10));
        }
    }

    private void renderVision(LinearLayout body) {
        title(body, "截图 OCR", "上传后立即返回，不再卡住等待识别完成");
        body.addView(hero("图片到任务", visionSummary()), lpTop(16));
        body.addView(primaryButton("选择图片并上传", v -> pickImage()), lpTop(14));
        body.addView(secondaryButton("刷新 OCR 结果", v -> refreshVisionResult()), lpTop(10));

        section(body, "识别结果");
        body.addView(cardText("状态", visionStatusText(), visionColor()), lpTop(10));
        if (!blank(visionText())) {
            body.addView(cardText("OCR 原文", preview(visionText(), 320), BLUE), lpTop(10));
        }
        actionGrid(body,
                action("保存任务", "保存候选任务", GREEN, this::saveVisionTasks),
                action("做总结", "把 OCR 原文交给 Agent", PRIMARY, this::digestVision),
                action("分析项目", "带 OCR 上下文", ORANGE, this::planVision),
                action("回首页", "继续演示", BLUE, () -> render(Tab.HOME))
        );
    }

    private void renderTasks(LinearLayout body) {
        title(body, "任务", "只保留真实后端任务，不展示假数据");
        body.addView(statusCard(), lpTop(16));
        body.addView(primaryButton("刷新任务", v -> loadTasks()), lpTop(12));
        section(body, "任务列表");
        if (tasks.length() == 0) {
            body.addView(cardText("暂无任务", "从项目分析或 OCR 结果保存任务后会出现在这里。", MUTED), lpTop(10));
            return;
        }
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject task = tasks.optJSONObject(i);
            if (task == null) {
                continue;
            }
            body.addView(cardText(task.optString("title", "未命名任务"),
                    task.optString("status", "todo") + " · " + task.optString("source", "android"),
                    "high".equals(task.optString("priority")) ? ORANGE : PRIMARY), lpTop(10));
        }
    }

    private void renderFeishu(LinearLayout body) {
        title(body, "飞书机器人", "固定展示配置，重点验证 /ping、图片和项目分析");
        body.addView(hero("@" + BOT_NAME, feishuSummary()), lpTop(16));
        body.addView(primaryButton("检测飞书后端", v -> loadBotHealth()), lpTop(14));
        body.addView(secondaryButton("刷新全部状态", v -> refreshAll(true)), lpTop(10));

        section(body, "开放平台配置");
        body.addView(cardText("事件配置", FEISHU_EVENT_URL, PRIMARY), lpTop(10));
        body.addView(cardText("卡片回调", FEISHU_CARD_URL, PRIMARY), lpTop(10));
        body.addView(cardText("Verification Token", FEISHU_TOKEN + "\nEncrypt Key: 未开启", ORANGE), lpTop(10));

        section(body, "群里测试");
        body.addView(cardText("第一步", "@test /ping", GREEN), lpTop(10));
        body.addView(cardText("项目分析", "@test /plan 分析 /home/chase/GitHub/shiliu-ai-v1 下一步", PRIMARY), lpTop(10));
        body.addView(cardText("找问题", "@test /bug 找当前项目最严重的问题", ORANGE), lpTop(10));
        body.addView(cardText("图片", "直接在群里发截图并 @test 整理", BLUE), lpTop(10));
    }

    private void renderSettings(LinearLayout body) {
        title(body, "设置", "默认已经填好，通常只需要点保存并检测");
        LinearLayout form = card();
        label(form, "Backend URL");
        EditText url = input(backendBaseUrl);
        form.addView(url, new LinearLayout.LayoutParams(-1, dp(58)));
        label(form, "Admin Token");
        EditText token = input(adminToken);
        form.addView(token, new LinearLayout.LayoutParams(-1, dp(58)));
        label(form, "项目路径");
        EditText path = input(projectPath);
        form.addView(path, new LinearLayout.LayoutParams(-1, dp(58)));
        body.addView(form, lpTop(16));
        body.addView(primaryButton("保存并检测", v -> saveSettings(url.getText().toString(), token.getText().toString(), path.getText().toString())), lpTop(14));
        body.addView(secondaryButton("恢复默认展示配置", v -> saveSettings(DEFAULT_BACKEND_BASE_URL, DEFAULT_ADMIN_TOKEN, DEFAULT_PROJECT_PATH)), lpTop(10));
        section(body, "最近日志");
        body.addView(logBox(), lpTop(10));
    }

    private void refreshAll(boolean show) {
        if (show) {
            statusText = "正在刷新状态...";
            render(currentTab);
        }
        runNetwork("刷新状态", () -> {
            JSONObject result = new JSONObject();
            result.put("health", api().get("/api/v1/health"));
            result.put("readiness", api().get("/api/v1/setup/readiness"));
            result.put("overview", api().get("/api/v1/workbench/overview"));
            result.put("tasks", api().get("/api/v1/tasks"));
            result.put("bot", api().get("/api/v1/bots/" + BOT_ID + "/health"));
            return result;
        }, result -> {
            health = result.optJSONObject("health");
            readiness = result.optJSONObject("readiness");
            overview = result.optJSONObject("overview");
            botHealth = result.optJSONObject("bot");
            JSONObject taskResponse = result.optJSONObject("tasks");
            tasks = taskResponse == null ? new JSONArray() : taskResponse.optJSONArray("items");
            if (tasks == null) {
                tasks = new JSONArray();
            }
            statusText = "已连接";
            render(currentTab);
        });
    }

    private void loadTasks() {
        statusText = "正在刷新任务...";
        render(Tab.TASKS);
        runNetwork("刷新任务", () -> api().get("/api/v1/tasks"), result -> {
            tasks = result.optJSONArray("items");
            if (tasks == null) {
                tasks = new JSONArray();
            }
            statusText = "任务已刷新";
            render(Tab.TASKS);
        });
    }

    private void loadBotHealth() {
        statusText = "正在检测飞书...";
        render(Tab.FEISHU);
        runNetwork("检测飞书", () -> api().get("/api/v1/bots/" + BOT_ID + "/health"), result -> {
            botHealth = result;
            statusText = "飞书状态已更新";
            render(Tab.FEISHU);
        });
    }

    private void runAgent(String command) {
        statusText = "Agent 已提交...";
        render(Tab.HOME);
        JSONObject body = new JSONObject();
        try {
            body.put("command", command);
            body.put("projectPath", projectPath);
            body.put("source", "android");
        } catch (Exception exception) {
            statusText = exception.getMessage();
            render(currentTab);
            return;
        }
        runNetwork("AgentRun", () -> api().postJson("/api/v1/agent/runs", body), result -> {
            lastRun = result;
            statusText = "Agent 完成";
            render(Tab.HOME);
        });
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        imagePicker.launch(intent);
    }

    private void uploadImage(Uri uri) {
        statusText = "图片上传中...";
        render(Tab.VISION);
        runNetwork("上传图片", () -> api().uploadImage(this, uri), upload -> {
            lastUpload = upload;
            lastVision = null;
            statusText = "图片已上传，后台 OCR 中";
            render(Tab.VISION);
            pollVision(upload.optString("traceId"));
        });
    }

    private void pollVision(String traceId) {
        if (blank(traceId)) {
            return;
        }
        executor.execute(() -> {
            try {
                for (int i = 0; i < 24; i++) {
                    JSONObject latest = api().get("/api/v1/vision/results/" + traceId);
                    runOnUiThread(() -> {
                        lastVision = latest;
                        statusText = "OCR " + latest.optString("status", "processing") + " · " + latest.optInt("progress", 0) + "%";
                        if (currentTab == Tab.VISION) {
                            render(Tab.VISION);
                        }
                    });
                    String status = latest.optString("status");
                    if ("done".equals(status) || "error".equals(status)) {
                        return;
                    }
                    Thread.sleep(700);
                }
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    statusText = "OCR 轮询失败：" + exception.getMessage();
                    log("OCR polling failed: " + exception.getMessage());
                    render(currentTab);
                });
            }
        });
    }

    private void refreshVisionResult() {
        String traceId = currentTraceId();
        if (blank(traceId)) {
            statusText = "还没有 OCR trace";
            render(Tab.VISION);
            return;
        }
        statusText = "正在刷新 OCR...";
        render(Tab.VISION);
        runNetwork("刷新 OCR", () -> api().get("/api/v1/vision/results/" + traceId), result -> {
            lastVision = result;
            statusText = "OCR 已刷新";
            render(Tab.VISION);
        });
    }

    private void saveVisionTasks() {
        String traceId = currentTraceId();
        if (blank(traceId)) {
            statusText = "没有可保存的 OCR trace";
            render(Tab.VISION);
            return;
        }
        statusText = "正在保存任务...";
        render(Tab.VISION);
        runNetwork("保存 OCR 任务", () -> api().postJson("/api/v1/tasks/from-trace/" + traceId, new JSONObject()), result -> {
            statusText = "已保存 " + result.optInt("savedCount") + " 条任务";
            loadTasks();
        });
    }

    private void digestVision() {
        runVisionAgent("/digest 总结 OCR 内容");
    }

    private void planVision() {
        runVisionAgent("/plan 基于 OCR 上下文分析 " + projectPath + " 下一步");
    }

    private void runVisionAgent(String command) {
        String text = visionText();
        if (blank(text)) {
            statusText = "OCR 原文为空，暂不能分发";
            render(Tab.VISION);
            return;
        }
        statusText = "Agent 已提交...";
        render(Tab.VISION);
        JSONObject body = new JSONObject();
        try {
            body.put("command", command);
            body.put("projectPath", projectPath);
            body.put("source", "android_ocr");
            body.put("traceId", currentTraceId());
            body.put("contextText", text);
        } catch (Exception exception) {
            statusText = exception.getMessage();
            render(Tab.VISION);
            return;
        }
        runNetwork("OCR 分发", () -> api().postJson("/api/v1/agent/runs", body), result -> {
            lastRun = result;
            statusText = "Agent 完成";
            render(Tab.HOME);
        });
    }

    private void runNetwork(String label, NetworkJob job, Consumer<JSONObject> success) {
        log(label + " started");
        executor.execute(() -> {
            try {
                JSONObject result = job.run();
                runOnUiThread(() -> {
                    log(label + " ok");
                    success.accept(result);
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    statusText = label + "失败：" + exception.getMessage();
                    log(label + " failed: " + exception.getMessage());
                    render(currentTab);
                });
            }
        });
    }

    private interface NetworkJob {
        JSONObject run() throws Exception;
    }

    private String homeSummary() {
        if (overview == null) {
            return "后端地址已预置。点击刷新状态或直接开始项目分析。";
        }
        return "今日识别 " + overview.optLong("todayTraceCount")
                + " 次 · 待办 " + overview.optLong("todoTaskCount")
                + " · 进行中 " + overview.optLong("inProgressTaskCount");
    }

    private String feishuSummary() {
        if (botHealth == null) {
            return "Bot ID: " + BOT_ID + "\n等待检测飞书状态。";
        }
        return "status=" + botHealth.optString("status")
                + " · tokenValid=" + botHealth.optBoolean("tokenValid")
                + "\neventCallbackVerified=" + botHealth.optBoolean("eventCallbackVerified")
                + "\nlastMessage=" + botHealth.optString("lastMessageText", "-");
    }

    private String visionSummary() {
        if (lastVision != null) {
            return "status=" + lastVision.optString("status") + " · progress=" + lastVision.optInt("progress") + "%";
        }
        if (lastUpload != null) {
            return "traceId=" + lastUpload.optString("traceId") + "\n" + lastUpload.optString("message");
        }
        return "选择一张截图，上传后马上显示 trace，OCR 进度后台刷新。";
    }

    private String visionStatusText() {
        if (lastVision != null) {
            return "traceId: " + lastVision.optString("traceId", currentTraceId())
                    + "\nstatus: " + lastVision.optString("status")
                    + " · stage: " + lastVision.optString("stage")
                    + " · progress: " + lastVision.optInt("progress") + "%"
                    + "\nmessage: " + lastVision.optString("message")
                    + (blank(lastVision.optString("errorCode")) ? "" : "\nerror: " + lastVision.optString("errorCode"));
        }
        if (lastUpload != null) {
            return "traceId: " + lastUpload.optString("traceId")
                    + "\nstatus: " + lastUpload.optString("status")
                    + "\nmessage: " + lastUpload.optString("message");
        }
        return "还没有上传图片。";
    }

    private int visionColor() {
        if (lastVision != null && "error".equals(lastVision.optString("status"))) {
            return RED;
        }
        if (lastVision != null && "done".equals(lastVision.optString("status"))) {
            return GREEN;
        }
        return BLUE;
    }

    private String visionText() {
        JSONObject ocr = lastVision == null ? null : lastVision.optJSONObject("ocr");
        return ocr == null ? "" : ocr.optString("plainText", "");
    }

    private String currentTraceId() {
        if (lastVision != null && !blank(lastVision.optString("traceId"))) {
            return lastVision.optString("traceId");
        }
        return lastUpload == null ? "" : lastUpload.optString("traceId");
    }

    private View statusCard() {
        String detail = statusText + "\n" + backendBaseUrl;
        if (readiness != null) {
            detail += "\nOCR=" + readiness.optBoolean("ocrHealthy") + " · Feishu=" + readiness.optString("botStatus", "unknown");
        }
        return cardText("连接状态", detail, readiness != null && readiness.optBoolean("backendOk") ? GREEN : ORANGE);
    }

    private void title(LinearLayout body, String title, String subtitle) {
        body.addView(text(title, 30, TEXT, true));
        body.addView(text(subtitle, 14, MUTED, false), lpTop(6));
    }

    private void section(LinearLayout body, String title) {
        body.addView(text(title, 19, TEXT, true), lpTop(24));
    }

    private View hero(String title, String detail) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(22), dp(22), dp(22));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{PRIMARY, PRIMARY_DARK});
        bg.setCornerRadius(dp(24));
        card.setBackground(bg);
        card.addView(text(title, 26, Color.WHITE, true));
        card.addView(text(detail, 15, Color.rgb(229, 241, 237), false), lpTop(8));
        return card;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(round(SURFACE, LINE, 20));
        return card;
    }

    private View cardText(String title, String detail, int color) {
        LinearLayout card = card();
        card.addView(text(title, 15, color, true));
        card.addView(text(detail, 15, TEXT, false), lpTop(7));
        return card;
    }

    private DemoAction action(String title, String detail, int color, Runnable runnable) {
        return new DemoAction(title, detail, color, runnable);
    }

    private void actionGrid(LinearLayout body, DemoAction... actions) {
        for (int i = 0; i < actions.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.addView(actionCard(actions[i]), new LinearLayout.LayoutParams(0, dp(112), 1));
            if (i + 1 < actions.length) {
                row.addView(space(dp(12), 1));
                row.addView(actionCard(actions[i + 1]), new LinearLayout.LayoutParams(0, dp(112), 1));
            }
            body.addView(row, lpTop(12));
        }
    }

    private View actionCard(DemoAction action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(round(tint(action.color, 0.08f), tint(action.color, 0.22f), 22));
        card.setClickable(true);
        card.setOnClickListener(v -> action.runnable.run());
        card.addView(text(action.title, 18, TEXT, true));
        card.addView(text(action.detail, 13, MUTED, false), lpTop(6));
        return card;
    }

    private MaterialButton primaryButton(String label, View.OnClickListener listener) {
        return button(label, PRIMARY, Color.WHITE, PRIMARY, listener);
    }

    private MaterialButton secondaryButton(String label, View.OnClickListener listener) {
        return button(label, SURFACE, TEXT, LINE, listener);
    }

    private MaterialButton button(String label, int bg, int fg, int stroke, View.OnClickListener listener) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setTextSize(17);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setCornerRadius(dp(12));
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setTextColor(fg);
        button.setPadding(dp(18), 0, dp(18), 0);
        button.setBackgroundTintList(ColorStateList.valueOf(bg));
        button.setStrokeColor(ColorStateList.valueOf(stroke));
        button.setStrokeWidth(bg == SURFACE ? dp(1) : 0);
        button.setOnClickListener(listener);
        button.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(64)));
        return button;
    }

    private void addBottomNav(LinearLayout root) {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(8), dp(8), dp(8));
        nav.setBackground(round(SURFACE, LINE, 0));
        root.addView(nav, new LinearLayout.LayoutParams(-1, dp(78)));
        navItem(nav, "工作台", Tab.HOME);
        navItem(nav, "截图", Tab.VISION);
        navItem(nav, "任务", Tab.TASKS);
        navItem(nav, "飞书", Tab.FEISHU);
        navItem(nav, "设置", Tab.SETTINGS);
    }

    private void navItem(LinearLayout nav, String label, Tab tab) {
        TextView item = text(label, 13, currentTab == tab ? Color.WHITE : MUTED, true);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(6), 0, dp(6), 0);
        item.setBackground(round(currentTab == tab ? PRIMARY : SURFACE, currentTab == tab ? PRIMARY : SURFACE, 18));
        item.setOnClickListener(v -> render(tab));
        nav.addView(item, new LinearLayout.LayoutParams(0, dp(54), 1));
    }

    private EditText input(String value) {
        EditText input = new EditText(this);
        input.setText(value);
        input.setTextSize(15);
        input.setSingleLine(true);
        input.setTextColor(TEXT);
        input.setPadding(dp(12), 0, dp(12), 0);
        input.setBackground(round(Color.WHITE, LINE, 14));
        return input;
    }

    private void label(LinearLayout parent, String value) {
        parent.addView(text(value, 13, MUTED, true), lpTop(parent.getChildCount() == 0 ? 0 : 12));
    }

    private View logBox() {
        TextView view = text(logs.isEmpty() ? "暂无日志" : String.join("\n", logs), 12, Color.rgb(230, 238, 235), false);
        view.setTypeface(Typeface.MONOSPACE);
        view.setPadding(dp(14), dp(14), dp(14), dp(14));
        view.setBackground(round(PRIMARY_DARK, PRIMARY_DARK, 18));
        return view;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value == null ? "" : value);
        text.setTextSize(sp);
        text.setTextColor(color);
        text.setLineSpacing(dp(2), 1f);
        text.setIncludeFontPadding(true);
        if (bold) {
            text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return text;
    }

    private View space(int width, int height) {
        Space space = new Space(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(width, height));
        return space;
    }

    private GradientDrawable round(int color, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String preview(String value, int limit) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= limit) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, limit - 1)) + "…";
    }

    private void log(String message) {
        logs.add(0, "[" + java.time.LocalTime.now().withNano(0) + "] " + message);
        while (logs.size() > 40) {
            logs.remove(logs.size() - 1);
        }
    }

    private static final class DemoAction {
        final String title;
        final String detail;
        final int color;
        final Runnable runnable;

        DemoAction(String title, String detail, int color, Runnable runnable) {
            this.title = title;
            this.detail = detail;
            this.color = color;
            this.runnable = runnable;
        }
    }
}
