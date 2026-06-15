package com.shiliuai.app.screens;

import android.content.Context;
import android.view.View;

import com.shiliuai.app.ShellHost;

/**
 * 屏幕协议。
 *
 * - 一个 Screen 拥有自己的 View 和状态，由 ShellHost 控制生命周期。
 * - 切换 Tab 时，前一个屏幕 {@link #onPause()}，新屏幕 {@link #onResume()}。
 * - 屏幕 View 被缓存，所以 EditText 输入跨 Tab 切换不丢失（修复旧版 Bug 13）。
 */
public interface Screen {

    String title();

    View onCreate(Context c, ShellHost host);

    default void onResume() {}

    default void onPause() {}

    default void onDestroy() {}
}
