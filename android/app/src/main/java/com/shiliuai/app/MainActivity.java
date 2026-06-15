package com.shiliuai.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.shiliuai.app.net.BackendClient;
import com.shiliuai.app.screens.AgentScreen;
import com.shiliuai.app.screens.FeishuScreen;
import com.shiliuai.app.screens.HomeScreen;
import com.shiliuai.app.screens.PapersScreen;
import com.shiliuai.app.screens.Screen;
import com.shiliuai.app.screens.TasksScreen;
import com.shiliuai.app.screens.VisionScreen;
import com.shiliuai.app.ui.Components;
import com.shiliuai.app.ui.MotionUtil;
import com.shiliuai.app.ui.Theme;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 视流 AI · 主活动 / 外壳。
 *
 * 这里只做：
 * - 顶部状态条
 * - 标签栏路由
 * - 屏幕缓存
 * - 图片选择回调
 *
 * 业务逻辑全部下沉到 {@code screens/*}，网络逻辑在 {@code net/*}，视觉在 {@code ui/*}。
 */
public final class MainActivity extends Activity implements ShellHost {

    private static final int PICK_IMAGE_REQUEST = 8101;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private BackendClient backend;

    private TextView appTitle;
    private TextView statusChip;

    private FrameLayout screenHost;
    private LinearLayout tabBar;

    private final LinkedHashMap<String, Screen> screens = new LinkedHashMap<>();
    private final Map<String, View> screenViews = new LinkedHashMap<>();
    private final Map<String, TextView> tabButtons = new LinkedHashMap<>();
    private Screen currentScreen;
    private ImageResult pendingImageCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        backend = BackendClient.fromBuildConfig();

        screens.put("home", new HomeScreen());
        screens.put("vision", new VisionScreen());
        screens.put("tasks", new TasksScreen());
        screens.put("agent", new AgentScreen());
        screens.put("papers", new PapersScreen());
        screens.put("feishu", new FeishuScreen());

        setContentView(buildShell());
        selectScreen("home");
    }

    @Override
    protected void onDestroy() {
        for (Screen s : screens.values()) s.onDestroy();
        executor.shutdownNow();
        super.onDestroy();
    }

    // ===== ShellHost 接口 =====

    @Override public Activity activity() { return this; }
    @Override public BackendClient backend() { return backend; }
    @Override public ExecutorService executor() { return executor; }

    @Override
    public void setStatus(CharSequence text, Components.ChipState state) {
        TextView fresh = Components.statusChip(this, text, state);
        statusChip.setText(fresh.getText());
        statusChip.setTextColor(fresh.getCurrentTextColor());
        statusChip.setBackground(fresh.getBackground());
    }

    @Override
    public void pickImage(ImageResult callback) {
        pendingImageCallback = callback;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK
                && data != null && data.getData() != null
                && pendingImageCallback != null) {
            Uri uri = data.getData();
            ImageResult cb = pendingImageCallback;
            pendingImageCallback = null;
            cb.onPicked(uri);
        }
    }

    // ===== Shell 构建 =====

    private View buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.void_(this));

        // 顶部应用栏
        LinearLayout appBar = new LinearLayout(this);
        appBar.setOrientation(LinearLayout.VERTICAL);
        int pad = Theme.dp(this, 16);
        appBar.setPadding(pad, pad, pad, Theme.dp(this, 12));
        appBar.setBackgroundColor(Theme.void_(this));

        appTitle = Theme.display(this, "视流 AI");
        appBar.addView(appTitle);

        TextView subtitle = Theme.meta(this, "OCR 截图 · 任务 · 论文 · 飞书 · 一体化移动控制台");
        appBar.addView(subtitle);

        LinearLayout statusRow = Components.row(this);
        statusRow.setPadding(0, Theme.dp(this, 10), 0, 0);
        statusChip = Components.statusChip(this, "系统 待命", Components.ChipState.READY);
        statusRow.addView(statusChip);
        appBar.addView(statusRow);

        // 信号色分隔线
        View seam = new View(this);
        seam.setBackgroundColor(Theme.signal(this));
        appBar.addView(seam, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 2)));

        root.addView(appBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 屏幕宿主
        screenHost = new FrameLayout(this);
        screenHost.setId(R.id.screen_host);
        screenHost.setBackgroundColor(Theme.void_(this));
        root.addView(screenHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // 底部 Tab：6 个等宽按钮平铺填满底部
        tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setBackgroundColor(Theme.carbon(this));
        tabBar.setWeightSum(6f);

        // 顶部 1px 信号色细线，强调 tab 区域
        View tabSeam = new View(this);
        tabSeam.setBackgroundColor(Theme.grid(this));
        root.addView(tabSeam, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 1)));

        addTab("home", "总览", R.drawable.ic_tab_home);
        addTab("vision", "识图", R.drawable.ic_tab_vision);
        addTab("tasks", "任务", R.drawable.ic_tab_tasks);
        addTab("agent", "Codex", R.drawable.ic_tab_agent);
        addTab("papers", "论文", R.drawable.ic_tab_papers);
        addTab("feishu", "飞书", R.drawable.ic_tab_feishu);

        root.addView(tabBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dimen(this, R.dimen.touch_tab)));

        return root;
    }

    private void addTab(String key, String label, int iconRes) {
        LinearLayout tab = new LinearLayout(this);
        tab.setOrientation(LinearLayout.VERTICAL);
        tab.setGravity(Gravity.CENTER);
        int p = Theme.dp(this, 6);
        tab.setPadding(p, p, p, p);
        tab.setClickable(true);
        tab.setFocusable(true);
        tab.setBackgroundResource(android.R.color.transparent);
        tab.setOnClickListener(v -> selectScreen(key));

        android.widget.ImageView icon = new android.widget.ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(Theme.textDim(this));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                Theme.dp(this, 22), Theme.dp(this, 22));
        iconLp.bottomMargin = Theme.dp(this, 2);
        tab.addView(icon, iconLp);

        TextView txt = Theme.meta(this, label);
        txt.setTextColor(Theme.textDim(this));
        txt.setTypeface(Theme.fontDisplay());
        txt.setLetterSpacing(0.08f);
        txt.setGravity(Gravity.CENTER);
        tab.addView(txt);

        // weight = 1：5 个 tab 等宽，平铺填满底部
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        tabBar.addView(tab, lp);
        tabButtons.put(key, txt);
        tabButtons.put(key + ":icon", null); // placeholder so we can recolor if needed later
        tab.setTag(R.id.tag_button_label, label);
    }

    private void selectScreen(String key) {
        Screen target = screens.get(key);
        if (target == null) return;
        if (currentScreen == target) return;

        if (currentScreen != null) currentScreen.onPause();
        screenHost.removeAllViews();

        View view = screenViews.get(key);
        if (view == null) {
            view = target.onCreate(this, this);
            screenViews.put(key, view);
        }
        screenHost.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        MotionUtil.slideIn(view, 0);

        currentScreen = target;
        target.onResume();
        appTitle.setText(target.title());
        highlightTab(key);
    }

    private void highlightTab(String key) {
        for (Map.Entry<String, TextView> e : tabButtons.entrySet()) {
            if (e.getValue() == null) continue;
            boolean active = e.getKey().equals(key);
            e.getValue().setTextColor(active ? Theme.signal(this) : Theme.textDim(this));
            View parent = (View) e.getValue().getParent();
            if (parent != null) {
                if (active) {
                    View bar = parent.findViewWithTag("activebar");
                    if (bar == null) {
                        bar = new View(this);
                        bar.setBackgroundColor(Theme.signal(this));
                        bar.setTag("activebar");
                        ((LinearLayout) parent).addView(bar, 0,
                                new LinearLayout.LayoutParams(Theme.dp(this, 24), Theme.dp(this, 2)));
                    }
                } else {
                    View bar = parent.findViewWithTag("activebar");
                    if (bar != null) ((LinearLayout) parent).removeView(bar);
                }
            }
        }
    }
}
