package com.shiliuai.app.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.widget.TextView;

import com.shiliuai.app.R;

/**
 * 视流 AI · 全局视觉令牌。
 *
 * 设计原则：
 * - 所有颜色、字号、间距、字体只来自本类。
 * - 屏幕代码不允许出现裸 #hex 或硬编码 dp。
 * - 字体使用系统 sans-serif / sans-serif-condensed / monospace，不打包字体文件。
 */
public final class Theme {

    private Theme() {}

    // ===== 颜色 =====
    public static int void_(Context c)        { return c.getResources().getColor(R.color.zzz_void, null); }
    public static int carbon(Context c)       { return c.getResources().getColor(R.color.zzz_carbon, null); }
    public static int carbonAlt(Context c)    { return c.getResources().getColor(R.color.zzz_carbon_alt, null); }
    public static int grid(Context c)         { return c.getResources().getColor(R.color.zzz_grid, null); }
    public static int scanline(Context c)     { return c.getResources().getColor(R.color.zzz_scanline, null); }
    public static int signal(Context c)       { return c.getResources().getColor(R.color.zzz_signal, null); }
    public static int signalDim(Context c)    { return c.getResources().getColor(R.color.zzz_signal_dim, null); }
    public static int signalSoft(Context c)   { return c.getResources().getColor(R.color.zzz_signal_soft, null); }
    public static int ok(Context c)           { return c.getResources().getColor(R.color.zzz_ok, null); }
    public static int okSoft(Context c)       { return c.getResources().getColor(R.color.zzz_ok_soft, null); }
    public static int alert(Context c)        { return c.getResources().getColor(R.color.zzz_alert, null); }
    public static int alertSoft(Context c)    { return c.getResources().getColor(R.color.zzz_alert_soft, null); }
    public static int danger(Context c)       { return c.getResources().getColor(R.color.zzz_danger, null); }
    public static int dangerSoft(Context c)   { return c.getResources().getColor(R.color.zzz_danger_soft, null); }
    public static int text(Context c)         { return c.getResources().getColor(R.color.zzz_text, null); }
    public static int textDim(Context c)      { return c.getResources().getColor(R.color.zzz_text_dim, null); }
    public static int textMute(Context c)     { return c.getResources().getColor(R.color.zzz_text_mute, null); }
    public static int textInvert(Context c)   { return c.getResources().getColor(R.color.zzz_text_invert, null); }

    // ===== 字体 =====
    public static Typeface fontDisplay()  { return Typeface.create("sans-serif-condensed", Typeface.BOLD); }
    public static Typeface fontTitle()    { return Typeface.create("sans-serif-medium", Typeface.NORMAL); }
    public static Typeface fontBody()     { return Typeface.create("sans-serif", Typeface.NORMAL); }
    public static Typeface fontMono()     { return Typeface.create("monospace", Typeface.NORMAL); }

    // ===== 单位换算 =====
    public static int dp(Context c, int dp) {
        return Math.round(dp * c.getResources().getDisplayMetrics().density);
    }

    public static int dimen(Context c, int dimenRes) {
        return c.getResources().getDimensionPixelSize(dimenRes);
    }

    public static float sp(Context c, int spRes) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX,
                c.getResources().getDimensionPixelSize(spRes),
                c.getResources().getDisplayMetrics()) / c.getResources().getDisplayMetrics().density;
    }

    // ===== TextView 工厂 =====
    public static TextView display(Context c, CharSequence txt) {
        TextView v = new TextView(c);
        v.setText(txt);
        v.setTextColor(text(c));
        v.setTypeface(fontDisplay());
        v.setAllCaps(false);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, c.getResources().getDimensionPixelSize(R.dimen.text_display));
        v.setLetterSpacing(0.02f);
        return v;
    }

    public static TextView title(Context c, CharSequence txt) {
        TextView v = new TextView(c);
        v.setText(txt);
        v.setTextColor(text(c));
        v.setTypeface(fontTitle());
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, c.getResources().getDimensionPixelSize(R.dimen.text_title));
        return v;
    }

    public static TextView section(Context c, CharSequence txt) {
        TextView v = new TextView(c);
        v.setText(txt);
        v.setTextColor(text(c));
        v.setTypeface(fontDisplay());
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, c.getResources().getDimensionPixelSize(R.dimen.text_section));
        v.setLetterSpacing(0.08f);
        return v;
    }

    public static TextView body(Context c, CharSequence txt) {
        TextView v = new TextView(c);
        v.setText(txt);
        v.setTextColor(textDim(c));
        v.setTypeface(fontBody());
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, c.getResources().getDimensionPixelSize(R.dimen.text_body));
        v.setLineSpacing(dp(c, 2), 1.0f);
        return v;
    }

    public static TextView meta(Context c, CharSequence txt) {
        TextView v = new TextView(c);
        v.setText(txt);
        v.setTextColor(textMute(c));
        v.setTypeface(fontBody());
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, c.getResources().getDimensionPixelSize(R.dimen.text_meta));
        return v;
    }

    public static TextView mono(Context c, CharSequence txt) {
        TextView v = new TextView(c);
        v.setText(txt);
        v.setTextColor(text(c));
        v.setTypeface(fontMono());
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, c.getResources().getDimensionPixelSize(R.dimen.text_meta));
        v.setLineSpacing(dp(c, 2), 1.0f);
        v.setTextIsSelectable(true);
        return v;
    }
}
