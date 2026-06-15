package com.shiliuai.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import com.shiliuai.app.net.BackendClient;
import com.shiliuai.app.ui.Components;

import java.util.concurrent.ExecutorService;

/**
 * 屏幕和 Activity 之间的桥。
 *
 * 屏幕通过这个接口拿到 Executor、网络客户端和全局状态条，不直接持有 Activity。
 */
public interface ShellHost {

    Activity activity();

    BackendClient backend();

    ExecutorService executor();

    /** 设置全局状态条文案和颜色。所有屏幕共用同一个状态条，但只显示，不再控制全局进度（修复旧版 Bug 6）。 */
    void setStatus(CharSequence text, Components.ChipState state);

    /** 启动图片选择器，回调返回到当前 Screen.onImagePicked。 */
    void pickImage(ImageResult callback);

    interface ImageResult {
        void onPicked(Uri uri);
    }

    /** 把 Intent 路由回 Activity，少量功能用。 */
    void startActivity(Intent intent);
}
