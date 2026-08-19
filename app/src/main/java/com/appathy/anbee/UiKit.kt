package com.appathy.anbee

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// 下部パネルの共通UI。めいろ／そうこばん で同じ見た目・同じ寸法にする。
object UiKit {

    fun clamp(v: Float, lo: Float, hi: Float): Float {
        if (v < lo) return lo
        if (v > hi) return hi
        return v
    }

    // パネルの高さ。フィールド高さは h - これ
    fun panelHeight(w: Float, h: Float): Float {
        return clamp(w * 0.40f, h * 0.20f, h * 0.30f)
    }

    fun drawPanelBase(c: Canvas, p: Paint, w: Float, h: Float, fh: Float, panelH: Float) {
        p.style = Paint.Style.FILL
        p.color = Color.rgb(216, 208, 192)
        c.drawRect(0f, fh, w, h, p)
        p.color = Color.rgb(150, 40, 40)
        c.drawRect(0f, fh, w, fh + panelH * 0.022f, p)
        p.color = Color.rgb(46, 44, 46)
        c.drawRect(0f, fh + panelH * 0.022f, w, fh + panelH * 0.036f, p)
    }

    fun drawDpad(
        c: Canvas, p: Paint, path: Path, rect: RectF,
        dcx: Float, dcy: Float, dr: Float, dir: Int
    ) {
        val a1 = dr * 0.36f
        p.style = Paint.Style.FILL
        p.color = Color.rgb(44, 42, 46)
        rect.set(dcx - a1, dcy - dr, dcx + a1, dcy + dr)
        c.drawRoundRect(rect, 14f, 14f, p)
        rect.set(dcx - dr, dcy - a1, dcx + dr, dcy + a1)
        c.drawRoundRect(rect, 14f, 14f, p)
        p.color = Color.rgb(78, 76, 80)
        c.drawCircle(dcx, dcy, a1 * 0.52f, p)
        if (dir != 0) {
            p.color = Color.argb(180, 250, 226, 120)
            when (dir) {
                1 -> c.drawCircle(dcx, dcy - dr * 0.62f, a1 * 0.5f, p)
                2 -> c.drawCircle(dcx, dcy + dr * 0.62f, a1 * 0.5f, p)
                3 -> c.drawCircle(dcx - dr * 0.62f, dcy, a1 * 0.5f, p)
                4 -> c.drawCircle(dcx + dr * 0.62f, dcy, a1 * 0.5f, p)
            }
        }
        p.color = Color.argb(160, 255, 255, 255)
        for (i in 0 until 4) {
            val t = i / 4f * 6.2832f
            val px = dcx + cos(t) * dr * 0.72f
            val py = dcy + sin(t) * dr * 0.72f
            path.reset()
            path.moveTo(px + cos(t) * a1 * 0.34f, py + sin(t) * a1 * 0.34f)
            path.lineTo(px + cos(t + 2.4f) * a1 * 0.34f, py + sin(t + 2.4f) * a1 * 0.34f)
            path.lineTo(px + cos(t - 2.4f) * a1 * 0.34f, py + sin(t - 2.4f) * a1 * 0.34f)
            path.close()
            c.drawPath(path, p)
        }
    }

    fun drawBtn(c: Canvas, p: Paint, x: Float, y: Float, br: Float, col: Int, label: String) {
        p.style = Paint.Style.FILL
        p.color = Color.argb(100, 0, 0, 0)
        c.drawCircle(x, y + 6f, br, p)
        p.color = col
        c.drawCircle(x, y, br, p)
        p.color = Color.WHITE
        p.textAlign = Paint.Align.CENTER
        val ts = fitText(p, label, br * 1.7f, br * 0.55f)
        p.textSize = ts
        c.drawText(label, x, y + ts * 0.36f, p)
        p.textAlign = Paint.Align.LEFT
    }

    // maxW に収まるまで縮めた文字サイズを返す
    fun fitText(p: Paint, s: String, maxW: Float, start: Float): Float {
        var ts = start
        for (k in 0 until 10) {
            p.textSize = ts
            if (p.measureText(s) <= maxW) break
            ts *= 0.91f
        }
        return ts
    }

    // 左右2つの文字を1行に収める
    fun drawStatusRow(
        c: Canvas, p: Paint, w: Float, fh: Float, panelH: Float,
        left: String, leftCol: Int, right: String, rightCol: Int
    ) {
        p.style = Paint.Style.FILL
        var ts = min(panelH * 0.115f, w * 0.031f)
        for (k in 0 until 10) {
            p.textSize = ts
            if (p.measureText(left) + p.measureText(right) + w * 0.11f <= w) break
            ts *= 0.92f
        }
        p.textSize = ts
        val ty = fh + panelH * 0.145f
        p.color = leftCol
        p.textAlign = Paint.Align.LEFT
        c.drawText(left, w * 0.035f, ty, p)
        p.textAlign = Paint.Align.RIGHT
        p.color = rightCol
        c.drawText(right, w * 0.965f, ty, p)
        p.textAlign = Paint.Align.LEFT
    }

    // フィールド左上の「メニュー」ボタン
    fun menuRect(rect: RectF, w: Float, fh: Float) {
        val m = min(w, fh)
        rect.set(m * 0.030f, m * 0.030f, m * 0.030f + m * 0.30f, m * 0.030f + m * 0.095f)
    }

    fun drawMenuBtn(c: Canvas, p: Paint, rect: RectF) {
        p.style = Paint.Style.FILL
        p.color = Color.argb(165, 20, 24, 22)
        c.drawRoundRect(rect, rect.height() * 0.42f, rect.height() * 0.42f, p)
        p.color = Color.rgb(240, 238, 230)
        p.textAlign = Paint.Align.CENTER
        val ts = fitText(p, "≡ メニュー", rect.width() * 0.80f, rect.height() * 0.52f)
        p.textSize = ts
        c.drawText("≡ メニュー", rect.centerX(), rect.centerY() + ts * 0.36f, p)
        p.textAlign = Paint.Align.LEFT
    }
}
