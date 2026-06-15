package com.shiliuai.app.ui;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.shiliuai.app.R;

/**
 * 视流 AI · 声明式组件工厂。
 *
 * 屏幕代码只调用本类返回的 View，不允许手写嵌套 LinearLayout。
 */
public final class Components {

    private Components() {}

    // ===== 容器 =====

    /** 标准列容器：内边距、间距、深底。 */
    public static LinearLayout column(Context c) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        int p = Theme.dimen(c, R.dimen.spacing_4);
        v.setPadding(p, p, p, p);
        v.setBackgroundColor(Theme.void_(c));
        v.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        v.setDividerDrawable(new GapDivider(Theme.dimen(c, R.dimen.spacing_3)));
        return v;
    }

    /** 横向行容器，子项间留 spacing_2。 */
    public static LinearLayout row(Context c) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        v.setDividerDrawable(new GapDivider(Theme.dimen(c, R.dimen.spacing_2)));
        return v;
    }

    // ===== 卡片 =====

    /** 切角卡片，自带 1dp 边和左侧信号色重音条。 */
    public static LinearLayout angularCard(Context c) {
        return angularCard(c, Theme.signal(c), true);
    }

    public static LinearLayout angularCard(Context c, int accentColor, boolean drawAccent) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        int p = Theme.dimen(c, R.dimen.spacing_4);
        v.setPadding(p, p, p, p);
        v.setBackground(new AngularCardDrawable(
                Theme.carbonAlt(c),
                Theme.grid(c),
                Theme.dimen(c, R.dimen.stroke_hairline),
                Theme.dimen(c, R.dimen.notch_card),
                accentColor,
                drawAccent));
        v.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        v.setDividerDrawable(new GapDivider(Theme.dimen(c, R.dimen.spacing_2)));
        return v;
    }

    // ===== 区段头 =====

    /** 段标题：信号色斜杠 + 加粗压扁字体。 */
    public static LinearLayout sectionHeader(Context c, CharSequence label) {
        LinearLayout row = row(c);
        View slash = new View(c);
        slash.setBackgroundColor(Theme.signal(c));
        LinearLayout.LayoutParams slashLp = new LinearLayout.LayoutParams(
                Theme.dp(c, 4), Theme.dp(c, 18));
        slashLp.rightMargin = Theme.dp(c, 10);
        row.addView(slash, slashLp);

        TextView t = Theme.section(c, label);
        row.addView(t);
        return row;
    }

    // ===== 文本块 =====

    public static TextView title(Context c, CharSequence txt) { return Theme.title(c, txt); }
    public static TextView body(Context c, CharSequence txt) { return Theme.body(c, txt); }
    public static TextView meta(Context c, CharSequence txt) { return Theme.meta(c, txt); }
    public static TextView display(Context c, CharSequence txt) { return Theme.display(c, txt); }

    /** 等宽数据块，深底，可滚动选择。 */
    public static TextView monoBlock(Context c, CharSequence txt) {
        TextView v = Theme.mono(c, txt);
        int p = Theme.dimen(c, R.dimen.spacing_3);
        v.setPadding(p, p, p, p);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.carbon(c));
        bg.setStroke(Theme.dimen(c, R.dimen.stroke_hairline), Theme.grid(c));
        v.setBackground(bg);
        return v;
    }

    // ===== 键值行 =====

    public static LinearLayout kvRow(Context c, CharSequence key, CharSequence value) {
        LinearLayout row = row(c);
        TextView k = Theme.meta(c, key);
        k.setAllCaps(false);
        k.setTextColor(Theme.textDim(c));
        TextView v = Theme.mono(c, value);
        v.setTextColor(Theme.text(c));

        LinearLayout.LayoutParams kLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        LinearLayout.LayoutParams vLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2);
        row.addView(k, kLp);
        row.addView(v, vLp);
        return row;
    }

    // ===== 状态芯片 =====

    public static TextView statusChip(Context c, CharSequence label, ChipState state) {
        int fg, bg, border;
        switch (state) {
            case OK:      fg = Theme.ok(c);      bg = Theme.okSoft(c);      border = Theme.ok(c); break;
            case RUNNING: fg = Theme.alert(c);   bg = Theme.alertSoft(c);   border = Theme.alert(c); break;
            case ERROR:   fg = Theme.danger(c);  bg = Theme.dangerSoft(c);  border = Theme.danger(c); break;
            case READY:
            default:      fg = Theme.signal(c);  bg = Theme.signalSoft(c);  border = Theme.signal(c);
        }
        TextView v = new TextView(c);
        v.setText(label);
        v.setAllCaps(false);
        v.setTextColor(fg);
        v.setTypeface(Theme.fontDisplay());
        v.setLetterSpacing(0.12f);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, c.getResources().getDimensionPixelSize(R.dimen.text_micro));
        int px = Theme.dp(c, 10);
        int py = Theme.dp(c, 5);
        v.setPadding(px, py, px, py);
        v.setGravity(Gravity.CENTER);
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setStroke(Theme.dimen(c, R.dimen.stroke_hairline), border);
        v.setBackground(d);
        return v;
    }

    public enum ChipState { READY, RUNNING, OK, ERROR }

    // ===== 按钮 =====

    /** 主操作：信号色实底 + 黑字 + 按下变深。 */
    public static TextView primaryButton(Context c, CharSequence label, Runnable onClick) {
        TextView v = buildButton(c, label, Theme.signal(c), Theme.textInvert(c), Theme.signal(c));
        applyPressBg(v, Theme.signal(c), Theme.signalDim(c), Theme.signal(c));
        v.setOnClickListener(view -> { MotionUtil.pressFlash(view, Theme.signalDim(c), Theme.signal(c)); onClick.run(); });
        v.setTag(R.id.tag_button_label, label);
        return v;
    }

    /** 次要操作：透明底 + 信号色边 + 信号色字。 */
    public static TextView ghostButton(Context c, CharSequence label, Runnable onClick) {
        TextView v = buildButton(c, label, 0, Theme.signal(c), Theme.signal(c));
        applyPressBg(v, 0, Theme.signalSoft(c), Theme.signal(c));
        v.setOnClickListener(view -> onClick.run());
        v.setTag(R.id.tag_button_label, label);
        return v;
    }

    /** 紧凑次要按钮：用于筛选条。 */
    public static TextView slimChipButton(Context c, CharSequence label, Runnable onClick) {
        TextView v = new TextView(c);
        v.setText(label);
        v.setAllCaps(false);
        v.setTextColor(Theme.signal(c));
        v.setTypeface(Theme.fontDisplay());
        v.setLetterSpacing(0.08f);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, c.getResources().getDimensionPixelSize(R.dimen.text_meta));
        int px = Theme.dp(c, 12);
        int py = Theme.dp(c, 6);
        v.setPadding(px, py, px, py);
        v.setGravity(Gravity.CENTER);
        v.setClickable(true);
        v.setFocusable(true);
        applyPressBg(v, 0, Theme.signalSoft(c), Theme.signal(c));
        v.setOnClickListener(view -> onClick.run());
        v.setTag(R.id.tag_button_label, label);
        return v;
    }

    /** 危险/重置：红边红字。 */
    public static TextView dangerButton(Context c, CharSequence label, Runnable onClick) {
        TextView v = buildButton(c, label, 0, Theme.danger(c), Theme.danger(c));
        applyPressBg(v, 0, Theme.dangerSoft(c), Theme.danger(c));
        v.setOnClickListener(view -> onClick.run());
        v.setTag(R.id.tag_button_label, label);
        return v;
    }

    private static TextView buildButton(Context c, CharSequence label, int fill, int fg, int border) {
        TextView v = new TextView(c);
        v.setText(label);
        v.setAllCaps(false);
        v.setTextColor(fg);
        v.setTypeface(Theme.fontDisplay());
        v.setLetterSpacing(0.08f);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, c.getResources().getDimensionPixelSize(R.dimen.text_section));
        v.setGravity(Gravity.CENTER);
        int px = Theme.dp(c, 18);
        int py = Theme.dp(c, 14);
        v.setPadding(px, py, px, py);
        v.setMinHeight(Theme.dimen(c, R.dimen.touch_button));
        v.setFocusable(true);
        v.setClickable(true);
        return v;
    }

    private static void applyPressBg(TextView v, int fill, int pressFill, int border) {
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(fill);
        normal.setStroke(Theme.dimen(v.getContext(), R.dimen.stroke_neon), border);

        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(pressFill);
        pressed.setStroke(Theme.dimen(v.getContext(), R.dimen.stroke_neon), border);

        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
        sld.addState(new int[]{-android.R.attr.state_enabled},
                muted(v.getContext(), border));
        sld.addState(new int[]{}, normal);

        // 加波纹（API 26+ 默认可用）
        RippleDrawable ripple = new RippleDrawable(
                android.content.res.ColorStateList.valueOf(Theme.signalSoft(v.getContext())),
                sld, null);
        v.setBackground(ripple);
    }

    private static GradientDrawable muted(Context c, int border) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Theme.carbon(c));
        d.setStroke(Theme.dimen(c, R.dimen.stroke_hairline), Theme.textMute(c));
        return d;
    }

    /** 切换按钮繁忙状态：标签从 R.id.tag_button_label 取，避免读取已被改写的 text。 */
    public static void setBusy(TextView button, boolean busy, CharSequence busyLabel) {
        if (button == null) return;
        button.setEnabled(!busy);
        Object label = button.getTag(R.id.tag_button_label);
        button.setText(busy ? busyLabel : (label == null ? button.getText() : label.toString()));
    }

    // ===== 输入框 =====

    public static EditText input(Context c, CharSequence hint) {
        EditText v = new EditText(c);
        v.setHint(hint);
        v.setHintTextColor(Theme.textMute(c));
        v.setTextColor(Theme.text(c));
        v.setTypeface(Theme.fontBody());
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, c.getResources().getDimensionPixelSize(R.dimen.text_body));
        v.setSingleLine(false);
        v.setMinLines(2);
        v.setBackground(null);
        int p = Theme.dimen(c, R.dimen.spacing_3);
        v.setPadding(p, p, p, p);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.carbon(c));
        bg.setStroke(Theme.dimen(c, R.dimen.stroke_hairline), Theme.grid(c));
        v.setBackground(bg);
        return v;
    }

    // ===== 任务卡专用 · 团队化工作台视觉元素 =====

    /**
     * 任务卡：左侧 4dp 信号色竖条 + 右侧内容区。
     * 状态色按 chip 状态映射。
     */
    public static LinearLayout taskCard(Context c, ChipState accent) {
        int accentColor;
        switch (accent) {
            case OK:      accentColor = Theme.ok(c); break;
            case RUNNING: accentColor = Theme.alert(c); break;
            case ERROR:   accentColor = Theme.danger(c); break;
            case READY:
            default:      accentColor = Theme.signal(c);
        }
        LinearLayout outer = new LinearLayout(c);
        outer.setOrientation(LinearLayout.HORIZONTAL);
        outer.setBackground(new AngularCardDrawable(
                Theme.carbonAlt(c),
                Theme.grid(c),
                Theme.dimen(c, R.dimen.stroke_hairline),
                Theme.dimen(c, R.dimen.notch_card),
                accentColor,
                false));

        View bar = new View(c);
        bar.setBackgroundColor(accentColor);
        outer.addView(bar, new LinearLayout.LayoutParams(Theme.dp(c, 4), ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        int p = Theme.dimen(c, R.dimen.spacing_4);
        body.setPadding(p, p, p, p);
        body.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        body.setDividerDrawable(new GapDivider(Theme.dimen(c, R.dimen.spacing_2)));
        outer.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        outer.setTag(R.id.tag_card_body, body);
        return outer;
    }

    /** 取任务卡内部 body，用于追加内容。 */
    public static LinearLayout taskCardBody(LinearLayout taskCard) {
        Object tag = taskCard.getTag(R.id.tag_card_body);
        return tag instanceof LinearLayout ? (LinearLayout) tag : taskCard;
    }

    /**
     * 负责人圆角徽章：首字 + 信号色描边 + 深底。
     * 在任务卡右上角与标题同行排布。
     */
    public static TextView ownerBadge(Context c, String owner) {
        String text = owner == null || owner.isEmpty() ? "?" : owner.substring(0, 1);
        TextView v = new TextView(c);
        v.setText(text);
        v.setAllCaps(false);
        v.setTextColor(Theme.signal(c));
        v.setTypeface(Theme.fontDisplay());
        v.setLetterSpacing(0.04f);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                c.getResources().getDimensionPixelSize(R.dimen.text_section));
        v.setGravity(Gravity.CENTER);
        int side = Theme.dp(c, 32);
        v.setMinWidth(side);
        v.setMinHeight(side);
        v.setPadding(Theme.dp(c, 6), Theme.dp(c, 4), Theme.dp(c, 6), Theme.dp(c, 4));
        GradientDrawable d = new GradientDrawable();
        d.setColor(Theme.carbon(c));
        d.setStroke(Theme.dimen(c, R.dimen.stroke_neon), Theme.signal(c));
        d.setCornerRadius(Theme.dp(c, 4));
        v.setBackground(d);
        return v;
    }

    /** 标题 + 右上角徽章的行。 */
    public static LinearLayout titleWithBadge(Context c, CharSequence title, TextView badge) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = Theme.title(c, title);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(badge);
        return row;
    }

    /** 单像素分割线，用于卡内分组。 */
    public static View hairline(Context c) {
        View v = new View(c);
        v.setBackgroundColor(Theme.grid(c));
        return v;
    }

    public static LinearLayout.LayoutParams hairlineLp(Context c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dimen(c, R.dimen.stroke_hairline));
        int m = Theme.dp(c, 6);
        lp.topMargin = m;
        lp.bottomMargin = m;
        return lp;
    }

    /** 元数据脚行：key｜key｜key，自适应换行。 */
    public static TextView metaLine(Context c, CharSequence text) {
        TextView v = Theme.meta(c, text);
        v.setTextColor(Theme.textDim(c));
        return v;
    }

    /** 卡内小按钮：用于"改派"等卡上动作。 */
    public static TextView cardActionButton(Context c, CharSequence label, Runnable onClick) {
        TextView v = new TextView(c);
        v.setText(label);
        v.setAllCaps(false);
        v.setTextColor(Theme.signal(c));
        v.setTypeface(Theme.fontDisplay());
        v.setLetterSpacing(0.08f);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, c.getResources().getDimensionPixelSize(R.dimen.text_meta));
        int px = Theme.dp(c, 12);
        int py = Theme.dp(c, 6);
        v.setPadding(px, py, px, py);
        v.setGravity(Gravity.CENTER);
        v.setClickable(true);
        v.setFocusable(true);
        applyPressBg(v, 0, Theme.signalSoft(c), Theme.signal(c));
        v.setOnClickListener(view -> onClick.run());
        v.setTag(R.id.tag_button_label, label);
        return v;
    }

    /** 单行输入框（用于负责人筛选/改派）。 */
    public static EditText singleLineInput(Context c, CharSequence hint) {
        EditText v = new EditText(c);
        v.setHint(hint);
        v.setHintTextColor(Theme.textMute(c));
        v.setTextColor(Theme.text(c));
        v.setTypeface(Theme.fontBody());
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, c.getResources().getDimensionPixelSize(R.dimen.text_body));
        v.setSingleLine(true);
        v.setMaxLines(1);
        int p = Theme.dimen(c, R.dimen.spacing_3);
        v.setPadding(p, p, p, p);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.carbon(c));
        bg.setStroke(Theme.dimen(c, R.dimen.stroke_hairline), Theme.grid(c));
        v.setBackground(bg);
        return v;
    }

    /** 活动筛选条：标签 + ✕ 关闭按钮。 */
    public static LinearLayout activeFilterChip(Context c, CharSequence label, Runnable onClear) {
        LinearLayout chip = new LinearLayout(c);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        int px = Theme.dp(c, 10);
        int py = Theme.dp(c, 4);
        chip.setPadding(px, py, px, py);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.signalSoft(c));
        bg.setStroke(Theme.dimen(c, R.dimen.stroke_hairline), Theme.signal(c));
        chip.setBackground(bg);

        TextView t = new TextView(c);
        t.setText(label);
        t.setAllCaps(false);
        t.setTextColor(Theme.signal(c));
        t.setTypeface(Theme.fontDisplay());
        t.setLetterSpacing(0.08f);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, c.getResources().getDimensionPixelSize(R.dimen.text_micro));
        chip.addView(t);

        TextView close = new TextView(c);
        close.setText("  ✕");
        close.setTextColor(Theme.signal(c));
        close.setTypeface(Theme.fontDisplay());
        close.setTextSize(TypedValue.COMPLEX_UNIT_PX, c.getResources().getDimensionPixelSize(R.dimen.text_micro));
        close.setClickable(true);
        close.setFocusable(true);
        close.setOnClickListener(v -> onClear.run());
        chip.addView(close);

        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setOnClickListener(v -> onClear.run());
        return chip;
    }

    // ===== 滚动雪佛龙 =====

    public static View chevronStrip(Context c) {
        View v = new View(c);
        ChevronStripDrawable d = new ChevronStripDrawable(
                Theme.carbon(c),
                Theme.signal(c),
                Theme.dp(c, 10),
                Theme.dp(c, 6));
        v.setBackground(d);
        v.setTag(R.id.tag_chevron, d);
        return v;
    }

    public static void setChevronRunning(View v, boolean running) {
        Object tag = v.getTag(R.id.tag_chevron);
        if (tag instanceof ChevronStripDrawable) {
            if (running) ((ChevronStripDrawable) tag).start();
            else ((ChevronStripDrawable) tag).stop();
        }
    }

    // ===== LayoutParams 简化 =====

    public static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams matchHeight(int heightPx) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx);
    }

    public static LinearLayout.LayoutParams weighted(int weight) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
    }

    // ===== 内部工具：纯间距 Divider =====

    private static final class GapDivider extends ColorDrawable {
        private final int sizePx;
        GapDivider(int sizePx) { super(0); this.sizePx = sizePx; }
        @Override public int getIntrinsicHeight() { return sizePx; }
        @Override public int getIntrinsicWidth() { return sizePx; }
    }
}
