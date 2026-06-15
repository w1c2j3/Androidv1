package com.shiliuai.app.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

/**
 * 滚动雪佛龙条：用于替代默认 indeterminate ProgressBar。
 *
 * 横向 >>>>> 图案匀速向右滚动；停止时画静态背景，不画前景图案。
 */
public final class ChevronStripDrawable extends Drawable {

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chevron = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int chevronWidthPx;
    private final int gapPx;
    private boolean running = false;
    private long startedAt = 0L;

    public ChevronStripDrawable(int bgColor, int chevronColor, int chevronWidthPx, int gapPx) {
        bg.setStyle(Paint.Style.FILL);
        bg.setColor(bgColor);
        chevron.setStyle(Paint.Style.FILL);
        chevron.setColor(chevronColor);
        this.chevronWidthPx = chevronWidthPx;
        this.gapPx = gapPx;
    }

    public void start() {
        if (running) return;
        running = true;
        startedAt = SystemClock.uptimeMillis();
        invalidateSelf();
    }

    public void stop() {
        running = false;
        invalidateSelf();
    }

    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        canvas.drawRect(b, bg);
        if (!running) return;

        int height = b.height();
        int step = chevronWidthPx + gapPx;
        long t = SystemClock.uptimeMillis() - startedAt;
        int offset = (int) ((t / 8L) % step);

        for (int x = b.left - step + offset; x <= b.right + step; x += step) {
            Path p = new Path();
            p.moveTo(x, b.top);
            p.lineTo(x + chevronWidthPx / 2f, b.top + height / 2f);
            p.lineTo(x, b.bottom);
            p.lineTo(x + 4, b.bottom);
            p.lineTo(x + chevronWidthPx / 2f + 4, b.top + height / 2f);
            p.lineTo(x + 4, b.top);
            p.close();
            canvas.drawPath(p, chevron);
        }
        scheduleSelf(this::invalidateSelf, SystemClock.uptimeMillis() + 16L);
    }

    @Override public void setAlpha(int alpha) { bg.setAlpha(alpha); chevron.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter colorFilter) {
        bg.setColorFilter(colorFilter); chevron.setColorFilter(colorFilter);
    }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
