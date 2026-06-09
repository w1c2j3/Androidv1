package com.shiliuai.app;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int PICK_IMAGE_REQUEST = 8101;
    private static final String DEFAULT_BACKEND_BASE_URL = BuildConfig.SHILIU_DEFAULT_BACKEND_URL;
    private static final String DEFAULT_ADMIN_TOKEN = BuildConfig.SHILIU_DEFAULT_ADMIN_TOKEN;
    private static final String DEFAULT_PROJECT_PATH = BuildConfig.SHILIU_DEFAULT_PROJECT_PATH;
    private static final String DEFAULT_BOT_ID = BuildConfig.SHILIU_DEFAULT_BOT_ID;
    private static final String DEFAULT_BOT_NAME = BuildConfig.SHILIU_DEFAULT_BOT_NAME;
    private static final String DEFAULT_BOT_VERIFICATION_TOKEN = BuildConfig.SHILIU_DEFAULT_BOT_VERIFICATION_TOKEN;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private LinearLayout root;
    private LinearLayout navRow;
    private LinearLayout content;
    private TextView titleView;
    private TextView subtitleView;
    private TextView liveStatusView;
    private TextView phaseView;
    private ProgressBar topProgress;
    private ProgressBar topSpinner;

    private Tab currentTab = Tab.HOME;
    private FlowPanel activeFlow;
    private Uri pendingImageUri;

    private enum Tab {
        HOME, VISION, TASKS, FEISHU, AGENT
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildShell();
        renderTab(Tab.HOME);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(color("#F5F1E8"));
        setContentView(root);

        LinearLayout appBar = new LinearLayout(this);
        appBar.setOrientation(LinearLayout.VERTICAL);
        appBar.setPadding(dp(18), dp(16), dp(18), dp(12));
        appBar.setBackgroundColor(color("#253241"));
        root.addView(appBar, new LinearLayout.LayoutParams(match(), wrap()));

        titleView = text("十六 AI 移动控制台", 22, "#FFFFFF", true);
        appBar.addView(titleView);

        subtitleView = text("经典 Android 工作台 · 配置已内置 · 操作全程可视化", 13, "#D6DFEA", false);
        subtitleView.setPadding(0, dp(4), 0, dp(10));
        appBar.addView(subtitleView);

        LinearLayout liveRow = new LinearLayout(this);
        liveRow.setOrientation(LinearLayout.HORIZONTAL);
        liveRow.setGravity(Gravity.CENTER_VERTICAL);
        appBar.addView(liveRow, new LinearLayout.LayoutParams(match(), wrap()));

        topSpinner = new ProgressBar(this);
        topSpinner.setIndeterminate(true);
        topSpinner.setVisibility(View.GONE);
        liveRow.addView(topSpinner, new LinearLayout.LayoutParams(dp(28), dp(28)));

        liveStatusView = text("待命", 15, "#FFFFFF", true);
        liveStatusView.setPadding(dp(10), 0, 0, 0);
        liveRow.addView(liveStatusView, new LinearLayout.LayoutParams(0, wrap(), 1));

        phaseView = badge("READY", "#D8F3DC", "#1B5E20");
        liveRow.addView(phaseView);

        topProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        topProgress.setMax(100);
        topProgress.setProgress(0);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(match(), dp(6));
        progressLp.setMargins(0, dp(12), 0, 0);
        appBar.addView(topProgress, progressLp);

        HorizontalScrollView navScroll = new HorizontalScrollView(this);
        navScroll.setHorizontalScrollBarEnabled(false);
        navScroll.setBackgroundColor(color("#FFFDF8"));
        root.addView(navScroll, new LinearLayout.LayoutParams(match(), wrap()));

        navRow = new LinearLayout(this);
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        navRow.setPadding(dp(10), dp(8), dp(10), dp(8));
        navScroll.addView(navRow);
        addNavButton("总览", Tab.HOME);
        addNavButton("OCR", Tab.VISION);
        addNavButton("任务", Tab.TASKS);
        addNavButton("飞书", Tab.FEISHU);
        addNavButton("Agent", Tab.AGENT);

        ScrollView scrollView = new ScrollView(this);
        root.addView(scrollView, new LinearLayout.LayoutParams(match(), 0, 1));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(14), dp(14), dp(28));
        scrollView.addView(content, new ScrollView.LayoutParams(match(), wrap()));
    }

    private void addNavButton(String label, Tab tab) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setMinHeight(dp(42));
        button.setOnClickListener(v -> renderTab(tab));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(88), dp(44));
        lp.setMargins(0, 0, dp(8), 0);
        navRow.addView(button, lp);
    }

    private void renderTab(Tab tab) {
        currentTab = tab;
        activeFlow = null;
        content.removeAllViews();
        setGlobalState("待命", "READY", 0, false, "#D8F3DC", "#1B5E20");
        if (tab == Tab.HOME) {
            renderHome();
        } else if (tab == Tab.VISION) {
            renderVision();
        } else if (tab == Tab.TASKS) {
            renderTasks();
        } else if (tab == Tab.FEISHU) {
            renderFeishu();
        } else {
            renderAgent();
        }
    }

    private void renderHome() {
        content.addView(sectionTitle("系统总览"));
        content.addView(infoCard("当前模式", "APK 已内置后端地址、管理员令牌和飞书机器人配置。手机端不展示设置页，Cloudflare URL 变化后重新打包 APK。"));

        FlowPanel flow = addFlowPanel("体检进度");
        Button check = primaryButton("立即体检后端");
        check.setOnClickListener(v -> runHomeCheck(flow, check));
        content.addView(check, buttonLp());

        content.addView(actionGrid(new String[][]{
                {"健康检查", "确认后端是否可达"},
                {"队列状态", "确认 OCR/Agent 队列"},
                {"工作台", "读取任务和识别概览"}
        }));

        runHomeCheck(flow, check);
    }

    private void renderVision() {
        content.addView(sectionTitle("OCR 图片识别"));
        content.addView(infoCard("操作说明", "选择手机图片后，App 会上传到后端，显示 traceId，并持续轮询处理阶段。识别完成后结果会直接显示在下方。"));

        FlowPanel flow = addFlowPanel("OCR 进程");
        Button pick = primaryButton("选择图片并开始识别");
        pick.setOnClickListener(v -> {
            activeFlow = flow;
            openImagePicker();
        });
        content.addView(pick, buttonLp());

        Button traces = secondaryButton("刷新最近 OCR 记录");
        traces.setOnClickListener(v -> runJsonAction(flow, traces, "读取最近 OCR 记录", "GET", "/api/v1/vision/traces?limit=10", null));
        content.addView(traces, buttonLp());
    }

    private void renderTasks() {
        content.addView(sectionTitle("任务管理"));
        content.addView(infoCard("反馈设计", "创建、刷新、状态更新都会进入进度条和时间线。失败时会直接显示 HTTP 错误内容，便于答辩现场定位问题。"));

        EditText titleInput = input("输入任务标题，例如：整理 OCR 识别结果");
        content.addView(titleInput);

        FlowPanel flow = addFlowPanel("任务操作进度");
        Button create = primaryButton("创建任务");
        create.setOnClickListener(v -> createTask(flow, create, titleInput));
        content.addView(create, buttonLp());

        Button refresh = secondaryButton("刷新任务列表");
        refresh.setOnClickListener(v -> runJsonAction(flow, refresh, "读取任务列表", "GET", "/api/v1/tasks", null));
        content.addView(refresh, buttonLp());
    }

    private void renderFeishu() {
        content.addView(sectionTitle("飞书机器人"));
        content.addView(infoCard("私聊优先", "当前答辩演示建议使用私聊。群聊是否触达取决于飞书群安装、@ 事件投递和开放平台权限，不属于手机 App 页面配置。"));
        content.addView(infoCard("已内置机器人", nonBlank(DEFAULT_BOT_NAME, "bot") + " · " + nonBlank(DEFAULT_BOT_ID, "未配置 botId")));

        FlowPanel flow = addFlowPanel("机器人联通进度");
        Button health = primaryButton("检查机器人状态");
        health.setOnClickListener(v -> checkBotHealth(flow, health));
        content.addView(health, buttonLp());

        Button callbacks = secondaryButton("显示当前回调路径");
        callbacks.setOnClickListener(v -> showCallbackPaths(flow, callbacks));
        content.addView(callbacks, buttonLp());
    }

    private void renderAgent() {
        content.addView(sectionTitle("项目 Agent"));
        content.addView(infoCard("运行方式", "App 向后端创建 Agent run，后端负责项目扫描、任务抽取和大模型总结。App 负责显示 runId、状态轮询和最终 JSON。"));

        EditText commandInput = input("输入指令，例如：/bug 找到当前项目最大的bug");
        commandInput.setText("/bug 找到当前项目最大的bug");
        content.addView(commandInput);

        FlowPanel flow = addFlowPanel("Agent 运行进度");
        Button run = primaryButton("运行指令");
        run.setOnClickListener(v -> createAgentRun(flow, run, commandInput.getText().toString()));
        content.addView(run, buttonLp());

        Button summary = secondaryButton("一键项目总结");
        summary.setOnClickListener(v -> createAgentRun(flow, summary, "总结当前项目结构、关键接口和最大风险"));
        content.addView(summary, buttonLp());

        Button list = secondaryButton("刷新运行记录");
        list.setOnClickListener(v -> runJsonAction(flow, list, "读取 Agent 运行记录", "GET", "/api/v1/agent/runs", null));
        content.addView(list, buttonLp());
    }

    private void runHomeCheck(FlowPanel flow, Button button) {
        startFlow(flow, button, "开始后端体检", 5);
        executor.execute(() -> {
            try {
                step(flow, "请求 /api/v1/health", 20);
                String health = request("GET", "/api/v1/health", null);
                step(flow, "健康检查完成", 42);
                String readiness = request("GET", "/api/v1/setup/readiness", null);
                step(flow, "环境就绪检查完成", 64);
                String queues = request("GET", "/api/v1/setup/queues", null);
                step(flow, "队列状态读取完成", 82);
                String overview = request("GET", "/api/v1/workbench/overview", null);
                String body = "健康检查\n" + pretty(health) + "\n\n就绪状态\n" + pretty(readiness)
                        + "\n\n队列状态\n" + pretty(queues) + "\n\n工作台概览\n" + pretty(overview);
                finishFlow(flow, button, "后端体检完成", body);
            } catch (Exception e) {
                failFlow(flow, button, "后端体检失败", e);
            }
        });
    }

    private void createTask(FlowPanel flow, Button button, EditText titleInput) {
        String title = titleInput.getText().toString().trim();
        if (title.isEmpty()) {
            toast("请先输入任务标题");
            return;
        }
        startFlow(flow, button, "开始创建任务", 8);
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("title", title);
                body.put("source", "android_app");
                body.put("owner", "mobile_demo");
                step(flow, "发送任务创建请求", 45);
                String raw = request("POST", "/api/v1/tasks", body.toString());
                finishFlow(flow, button, "任务创建完成", pretty(raw));
            } catch (Exception e) {
                failFlow(flow, button, "任务创建失败", e);
            }
        });
    }

    private void checkBotHealth(FlowPanel flow, Button button) {
        if (isEmptyText(DEFAULT_BOT_ID)) {
            toast("APK 没有内置 botId");
            return;
        }
        runJsonAction(flow, button, "检查飞书机器人状态", "GET", "/api/v1/bots/" + DEFAULT_BOT_ID + "/health", null);
    }

    private void showCallbackPaths(FlowPanel flow, Button button) {
        startFlow(flow, button, "生成回调路径", 25);
        String base = cleanBaseUrl();
        String result = "事件订阅请求地址\n" + base + "/feishu/events/" + nonBlank(DEFAULT_BOT_ID, "<botId>")
                + "\n\n卡片回调请求地址\n" + base + "/feishu/card-callback/" + nonBlank(DEFAULT_BOT_ID, "<botId>")
                + "\n\n说明\nCloudflare Quick Tunnel 每次重启都会变化；变化后重新运行打包脚本，安装新的 APK。";
        finishFlow(flow, button, "回调路径已生成", result);
    }

    private void createAgentRun(FlowPanel flow, Button button, String command) {
        String finalCommand = command == null ? "" : command.trim();
        if (finalCommand.isEmpty()) {
            toast("请输入指令");
            return;
        }
        startFlow(flow, button, "创建 Agent run", 8);
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("command", finalCommand);
                body.put("projectPath", DEFAULT_PROJECT_PATH);
                body.put("source", "android_app");
                body.put("contextText", "mobile_ui_request");
                body.put("saveTasks", false);
                step(flow, "发送运行指令", 30);
                String raw = request("POST", "/api/v1/agent/runs", body.toString());
                JSONObject json = new JSONObject(raw);
                String runId = json.optString("runId", "");
                step(flow, runId.isEmpty() ? "运行已创建，未返回 runId" : "运行已创建：" + runId, 55);
                if (runId.isEmpty()) {
                    finishFlow(flow, button, "Agent 已返回", pretty(raw));
                } else {
                    pollAgentRun(flow, button, runId, 0);
                }
            } catch (Exception e) {
                failFlow(flow, button, "Agent 运行失败", e);
            }
        });
    }

    private void pollAgentRun(FlowPanel flow, Button button, String runId, int attempt) {
        executor.execute(() -> {
            try {
                String raw = request("GET", "/api/v1/agent/runs/" + runId, null);
                JSONObject json = new JSONObject(raw);
                String status = json.optString("status", "unknown");
                int progress = Math.min(95, 58 + attempt * 6);
                step(flow, "轮询 Agent 状态：" + status, progress);
                if (isTerminal(status) || attempt >= 7) {
                    finishFlow(flow, button, "Agent 运行结束：" + status, pretty(raw));
                } else {
                    mainHandler.postDelayed(() -> pollAgentRun(flow, button, runId, attempt + 1), 1200);
                }
            } catch (Exception e) {
                failFlow(flow, button, "Agent 轮询失败", e);
            }
        });
    }

    private void runJsonAction(FlowPanel flow, Button button, String label, String method, String path, @Nullable String body) {
        startFlow(flow, button, label, 10);
        executor.execute(() -> {
            try {
                step(flow, "发送请求：" + method + " " + path, 45);
                String raw = request(method, path, body);
                finishFlow(flow, button, label + "完成", pretty(raw));
            } catch (Exception e) {
                failFlow(flow, button, label + "失败", e);
            }
        });
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            pendingImageUri = data.getData();
            if (activeFlow == null) {
                activeFlow = addFlowPanel("OCR 进程");
            }
            uploadImage(activeFlow, pendingImageUri);
        }
    }

    private void uploadImage(FlowPanel flow, Uri uri) {
        startFlow(flow, null, "准备上传图片", 5);
        executor.execute(() -> {
            try {
                step(flow, "读取手机图片：" + displayName(uri), 18);
                byte[] bytes = readAll(uri);
                step(flow, "图片读取完成，开始上传", 35);
                String uploadRaw = uploadMultipart("/api/v1/vision/upload?source=android_app&sceneHint=auto", uri, bytes);
                JSONObject upload = new JSONObject(uploadRaw);
                String traceId = upload.optString("traceId", "");
                step(flow, traceId.isEmpty() ? "上传完成，等待结果" : "上传完成：" + traceId, 55);
                if (traceId.isEmpty()) {
                    finishFlow(flow, null, "OCR 上传完成", pretty(uploadRaw));
                } else {
                    pollVision(flow, traceId, 0);
                }
            } catch (Exception e) {
                failFlow(flow, null, "OCR 上传失败", e);
            }
        });
    }

    private void pollVision(FlowPanel flow, String traceId, int attempt) {
        executor.execute(() -> {
            try {
                String raw = request("GET", "/api/v1/vision/results/" + traceId, null);
                JSONObject json = new JSONObject(raw);
                String status = json.optString("status", "unknown");
                int progress = Math.min(96, 58 + attempt * 7);
                step(flow, "OCR 状态：" + status, progress);
                if (isTerminal(status) || attempt >= 8) {
                    finishFlow(flow, null, "OCR 处理结束：" + status, pretty(raw));
                } else {
                    mainHandler.postDelayed(() -> pollVision(flow, traceId, attempt + 1), 1200);
                }
            } catch (Exception e) {
                failFlow(flow, null, "OCR 轮询失败", e);
            }
        });
    }

    private String request(String method, String path, @Nullable String jsonBody) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(cleanBaseUrl() + path).openConnection();
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(15000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        if (!isEmptyText(DEFAULT_ADMIN_TOKEN)) {
            connection.setRequestProperty("Authorization", "Bearer " + DEFAULT_ADMIN_TOKEN);
        }
        if (jsonBody != null) {
            byte[] payload = jsonBody.getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Content-Length", String.valueOf(payload.length));
            try (OutputStream out = connection.getOutputStream()) {
                out.write(payload);
            }
        }
        return readConnection(connection);
    }

    private String uploadMultipart(String path, Uri uri, byte[] bytes) throws IOException {
        String boundary = "----ShiliuAndroid" + System.currentTimeMillis();
        HttpURLConnection connection = (HttpURLConnection) new URL(cleanBaseUrl() + path).openConnection();
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(30000);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        if (!isEmptyText(DEFAULT_ADMIN_TOKEN)) {
            connection.setRequestProperty("Authorization", "Bearer " + DEFAULT_ADMIN_TOKEN);
        }
        connection.setDoOutput(true);
        String name = displayName(uri);
        try (OutputStream out = connection.getOutputStream()) {
            writeAscii(out, "--" + boundary + "\r\n");
            writeAscii(out, "Content-Disposition: form-data; name=\"file\"; filename=\"" + safeFileName(name) + "\"\r\n");
            writeAscii(out, "Content-Type: image/jpeg\r\n\r\n");
            out.write(bytes);
            writeAscii(out, "\r\n--" + boundary + "--\r\n");
        }
        return readConnection(connection);
    }

    private String readConnection(HttpURLConnection connection) throws IOException {
        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        String body = stream == null ? "" : readString(stream);
        connection.disconnect();
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + "\n" + body);
        }
        return body;
    }

    private byte[] readAll(Uri uri) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(uri); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) {
                throw new IOException("无法读取图片内容");
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private String readString(InputStream in) throws IOException {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private void writeAscii(OutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.US_ASCII));
    }

    private void startFlow(FlowPanel flow, @Nullable Button button, String message, int progress) {
        mainHandler.post(() -> {
            activeFlow = flow;
            flow.clear();
            flow.result.setText("");
            flow.add("开始", message);
            setButtonBusy(button, true);
            setGlobalState(message, "RUNNING", progress, true, "#FFE8A3", "#5D4600");
        });
    }

    private void step(FlowPanel flow, String message, int progress) {
        mainHandler.post(() -> {
            flow.add("处理中", message);
            setGlobalState(message, "RUNNING", progress, true, "#FFE8A3", "#5D4600");
        });
    }

    private void finishFlow(FlowPanel flow, @Nullable Button button, String message, String result) {
        mainHandler.post(() -> {
            flow.add("完成", message);
            flow.result.setText(result == null ? "" : result);
            setButtonBusy(button, false);
            setGlobalState(message, "DONE", 100, false, "#D8F3DC", "#1B5E20");
        });
    }

    private void failFlow(FlowPanel flow, @Nullable Button button, String message, Exception error) {
        mainHandler.post(() -> {
            flow.add("失败", message);
            flow.result.setText(error.getMessage() == null ? error.toString() : error.getMessage());
            setButtonBusy(button, false);
            setGlobalState(message, "ERROR", 100, false, "#FFD6D6", "#7A1B1B");
        });
    }

    private void setGlobalState(String status, String phase, int progress, boolean busy, String bg, String fg) {
        liveStatusView.setText(status);
        phaseView.setText(phase);
        phaseView.setBackgroundColor(color(bg));
        phaseView.setTextColor(color(fg));
        topProgress.setProgress(progress);
        topSpinner.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void setButtonBusy(@Nullable Button button, boolean busy) {
        if (button == null) {
            return;
        }
        button.setEnabled(!busy);
        button.setText(busy ? "处理中..." : button.getTag() == null ? button.getText() : String.valueOf(button.getTag()));
    }

    private FlowPanel addFlowPanel(String title) {
        LinearLayout card = card();
        card.addView(text(title, 17, "#263238", true));
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(match(), dp(8));
        progressLp.setMargins(0, dp(10), 0, dp(10));
        card.addView(progress, progressLp);

        LinearLayout timeline = new LinearLayout(this);
        timeline.setOrientation(LinearLayout.VERTICAL);
        card.addView(timeline);

        TextView result = text("等待操作", 13, "#43505A", false);
        result.setPadding(0, dp(12), 0, 0);
        result.setTextIsSelectable(true);
        card.addView(result);

        content.addView(card, cardLp());
        return new FlowPanel(progress, timeline, result);
    }

    private LinearLayout actionGrid(String[][] rows) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        for (String[] row : rows) {
            LinearLayout item = card();
            item.setPadding(dp(14), dp(12), dp(14), dp(12));
            item.addView(text(row[0], 15, "#263238", true));
            item.addView(text(row[1], 12, "#64727D", false));
            grid.addView(item, cardLp());
        }
        return grid;
    }

    private TextView sectionTitle(String value) {
        TextView view = text(value, 21, "#1F2A33", true);
        view.setPadding(dp(2), dp(2), dp(2), dp(12));
        return view;
    }

    private LinearLayout infoCard(String title, String body) {
        LinearLayout card = card();
        card.addView(text(title, 16, "#263238", true));
        TextView bodyView = text(body, 13, "#56636D", false);
        bodyView.setPadding(0, dp(6), 0, 0);
        card.addView(bodyView);
        return card;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackgroundColor(color("#FFFDF8"));
        return card;
    }

    private Button primaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTag(label);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTextColor(color("#FFFFFF"));
        button.setBackgroundColor(color("#2F5D62"));
        button.setMinHeight(dp(52));
        return button;
    }

    private Button secondaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTag(label);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTextColor(color("#263238"));
        button.setBackgroundColor(color("#DFE7E3"));
        button.setMinHeight(dp(48));
        return button;
    }

    private EditText input(String hint) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setTextSize(15);
        editText.setSingleLine(false);
        editText.setMinLines(2);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editText.setBackgroundColor(color("#FFFFFF"));
        editText.setPadding(dp(12), dp(10), dp(12), dp(10));
        return editText;
    }

    private TextView text(String value, int sp, String color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color(color));
        if (bold) {
            view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        }
        view.setLineSpacing(dp(2), 1.0f);
        return view;
    }

    private TextView badge(String label, String bg, String fg) {
        TextView view = text(label, 12, fg, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(10), dp(5), dp(10), dp(5));
        view.setBackgroundColor(color(bg));
        return view;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(match(), wrap());
        lp.setMargins(0, 0, 0, dp(12));
        return lp;
    }

    private LinearLayout.LayoutParams buttonLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(match(), wrap());
        lp.setMargins(0, 0, 0, dp(10));
        return lp;
    }

    private int match() {
        return ViewGroup.LayoutParams.MATCH_PARENT;
    }

    private int wrap() {
        return ViewGroup.LayoutParams.WRAP_CONTENT;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int color(String value) {
        return android.graphics.Color.parseColor(value);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private String cleanBaseUrl() {
        String base = isEmptyText(DEFAULT_BACKEND_BASE_URL) ? "http://10.0.2.2:8080" : DEFAULT_BACKEND_BASE_URL;
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private String pretty(String raw) {
        if (isEmptyText(raw)) {
            return "";
        }
        try {
            String trimmed = raw.trim();
            if (trimmed.startsWith("[")) {
                return new JSONArray(trimmed).toString(2);
            }
            if (trimmed.startsWith("{")) {
                return new JSONObject(trimmed).toString(2);
            }
        } catch (Exception ignored) {
            return raw;
        }
        return raw;
    }

    private boolean isTerminal(String status) {
        if (status == null) {
            return false;
        }
        String s = status.toLowerCase();
        return s.equals("done") || s.equals("error") || s.equals("failed") || s.equals("completed") || s.equals("success");
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        } catch (Exception ignored) {
            return "image.jpg";
        }
        return "image.jpg";
    }

    private String safeFileName(String name) {
        if (isEmptyText(name)) {
            return "image.jpg";
        }
        return name.replace("\"", "").replace("\r", "").replace("\n", "");
    }

    private String nonBlank(String value, String fallback) {
        return isEmptyText(value) ? fallback : value;
    }

    private boolean isEmptyText(String value) {
        return value == null || value.trim().isEmpty();
    }

    private final class FlowPanel {
        final ProgressBar progress;
        final LinearLayout timeline;
        final TextView result;

        FlowPanel(ProgressBar progress, LinearLayout timeline, TextView result) {
            this.progress = progress;
            this.timeline = timeline;
            this.result = result;
        }

        void clear() {
            progress.setProgress(0);
            timeline.removeAllViews();
        }

        void add(String phase, String message) {
            progress.setProgress(topProgress.getProgress());
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            row.setPadding(0, dp(4), 0, dp(4));

            TextView dot = text("●", 15, phase.equals("失败") ? "#B42318" : phase.equals("完成") ? "#1B7F3A" : "#2F5D62", true);
            row.addView(dot, new LinearLayout.LayoutParams(dp(24), wrap()));

            TextView line = text(phase + " · " + message, 13, "#3C4852", false);
            row.addView(line, new LinearLayout.LayoutParams(0, wrap(), 1));
            timeline.addView(row);
            progress.setProgress(topProgress.getProgress());
        }
    }
}
