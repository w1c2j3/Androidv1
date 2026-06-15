package com.shiliuai.app.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * 动效工具：屏幕切换滑入、按钮按下闪光。
 *
 * 故意只用 View.animate 与 ObjectAnimator，避免引入额外动画库。
 */
public final class MotionUtil {

    private MotionUtil() {}

    public static void slideIn(View v, long delayMs) {
        v.setTranslationX(Theme.dp(v.getContext(), 24));
        v.setAlpha(0f);
        v.animate()
                .translationX(0f)
                .alpha(1f)
                .setStartDelay(delayMs)
                .setDuration(180L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    /** 按下后短闪：把背景色从信号色暗版拉回正常。 */
    public static void pressFlash(View v, int fromColor, int toColor) {
        ValueAnimator anim = ValueAnimator.ofArgb(fromColor, toColor);
        anim.setDuration(160L);
        anim.addUpdateListener(a -> v.setBackground(new ColorDrawable((int) a.getAnimatedValue())));
        anim.start();
    }

    public static void fadeIn(View v, long delayMs) {
        v.setAlpha(0f);
        ObjectAnimator a = ObjectAnimator.ofFloat(v, "alpha", 0f, 1f);
        a.setStartDelay(delayMs);
        a.setDuration(160L);
        a.start();
    }
}
