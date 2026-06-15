package com.shiliuai.app.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * 卡片背景：右上角和左下角切角，自带 1dp 边框。
 *
 * 仿 ZZZ 卡片轮廓——直角硬朗，不要圆角。
 */
public final class AngularCardDrawable extends Drawable {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int notchPx;
    private final boolean drawAccentBar;

    public AngularCardDrawable(int fillColor, int strokeColor, int strokePx, int notchPx, int accentColor, boolean drawAccentBar) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(fillColor);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(strokeColor);
        stroke.setStrokeWidth(strokePx);
        accent.setStyle(Paint.Style.FILL);
        accent.setColor(accentColor);
        this.notchPx = notchPx;
        this.drawAccentBar = drawAccentBar;
    }

    private Path buildOutline(Rect b) {
        Path p = new Path();
        int n = notchPx;
        p.moveTo(b.left, b.top);
        p.lineTo(b.right - n, b.top);
        p.lineTo(b.right, b.top + n);
        p.lineTo(b.right, b.bottom);
        p.lineTo(b.left + n, b.bottom);
        p.lineTo(b.left, b.bottom - n);
        p.close();
        return p;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        Path outline = buildOutline(b);
        canvas.drawPath(outline, fill);
        canvas.drawPath(outline, stroke);
        if (drawAccentBar) {
            int barW = Math.max(stroke.getStrokeWidth() == 0 ? 4 : (int) stroke.getStrokeWidth() * 2, 4);
            canvas.drawRect(b.left, b.top, b.left + barW, b.top + Math.min(b.height(), b.width() / 3), accent);
        }
    }

    @Override public void setAlpha(int alpha) {
        fill.setAlpha(alpha);
        stroke.setAlpha(alpha);
        accent.setAlpha(alpha);
    }

    @Override public void setColorFilter(ColorFilter colorFilter) {
        fill.setColorFilter(colorFilter);
        stroke.setColorFilter(colorFilter);
        accent.setColorFilter(colorFilter);
    }

    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
