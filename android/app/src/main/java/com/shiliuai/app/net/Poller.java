package com.shiliuai.app.net;

import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

/**
 * 生命周期感知的轮询器。
 *
 * - 屏幕销毁 / 切换 Tab 时调用 {@link #cancel()}。
 * - 取消之后的回调不会再触达 UI，修复旧版 Bug 4 / 5 / 7（旧轮询继续更新全局进度）。
 */
public final class Poller {

    public interface PollStep<T> {
        T fetch() throws IOException;
        boolean isTerminal(T result);
        void onProgress(T partial, int attempt);
        void onTerminal(T finalResult);
        void onError(IOException error);
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService executor;
    private final long intervalMs;
    private final int maxAttempts;
    private volatile boolean cancelled = false;

    public Poller(ExecutorService executor, long intervalMs, int maxAttempts) {
        this.executor = executor;
        this.intervalMs = intervalMs;
        this.maxAttempts = maxAttempts;
    }

    public boolean isCancelled() { return cancelled; }

    public void cancel() { cancelled = true; ui.removeCallbacksAndMessages(null); }

    public <T> void start(PollStep<T> step) { tick(step, 0); }

    private <T> void tick(PollStep<T> step, int attempt) {
        if (cancelled) return;
        executor.execute(() -> {
            if (cancelled) return;
            try {
                T result = step.fetch();
                if (cancelled) return;
                ui.post(() -> {
                    if (cancelled) return;
                    if (step.isTerminal(result) || attempt >= maxAttempts) {
                        step.onTerminal(result);
                    } else {
                        step.onProgress(result, attempt);
                        ui.postDelayed(() -> tick(step, attempt + 1), intervalMs);
                    }
                });
            } catch (IOException e) {
                if (cancelled) return;
                ui.post(() -> { if (!cancelled) step.onError(e); });
            }
        });
    }
}
