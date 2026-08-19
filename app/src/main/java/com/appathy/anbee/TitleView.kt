package com.appathy.anbee

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.min
import kotlin.math.sin

class TitleView(ctx: Context) : View(ctx) {

    var onPick: ((Int) -> Unit)? = null

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val cardA = RectF()
    private val cardB = RectF()

    private var w = 0f
    private var h = 0f
    private var last = 0L
    private var time = 0f
    private var pressed = -1

    override fun onSizeChanged(nw: Int, nh: Int, ow: Int, oh: Int) {
        w = nw.toFloat()
        h = nh.toFloat()
        val m = w * 0.08f
        val top = h * 0.36f
        val gap = h * 0.035f
        val ch = (h * 0.90f - top - gap) * 0.5f
        cardA.set(m, top, w - m, top + ch)
        cardB.set(m, top + ch + gap, w - m, top + ch * 2f + gap)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = when {
                    cardA.contains(e.x, e.y) -> 0
                    cardB.contains(e.x, e.y) -> 1
                    else -> -1
                }
            }
            MotionEvent.ACTION_UP -> {
                val hit = when {
                    cardA.contains(e.x, e.y) -> 0
                    cardB.contains(e.x, e.y) -> 1
                    else -> -1
                }
                if (hit >= 0 && hit == pressed) onPick?.invoke(hit)
                pressed = -1
            }
            MotionEvent.ACTION_CANCEL -> pressed = -1
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        val now = System.nanoTime()
        if (last == 0L) last = now
        var dt = (now - last) / 1e9f
        last = now
        if (dt > 0.05f) dt = 0.05f
        time += dt

        c.drawColor(Color.rgb(150, 190, 128))

        // そうげん
        p.style = Paint.Style.FILL
        p.color = Color.rgb(138, 178, 116)
        val step = w * 0.13f
        var gx = 0f
        while (gx < w + step) {
            var gy = 0f
            var k = 0
            while (gy < h + step) {
                c.drawCircle(gx + (k % 3) * step * 0.2f, gy, step * 0.16f, p)
                gy += step
                k++
            }
            gx += step
        }

        p.color = Color.argb(60, 0, 0, 0)
        c.drawRect(0f, 0f, w, h * 0.30f, p)

        // タイトル
        p.textAlign = Paint.Align.CENTER
        p.color = Color.rgb(252, 220, 96)
        p.textSize = h * 0.072f
        c.drawText("アンビー", w * 0.5f, h * 0.115f, p)
        p.color = Color.rgb(238, 240, 232)
        p.textSize = h * 0.024f
        c.drawText("あそびかたを えらんでね", w * 0.5f, h * 0.155f, p)
        p.textAlign = Paint.Align.LEFT

        // 見本のアンビー
        val r = min(w, h) * 0.055f
        val bx = w * 0.5f
        val by = h * 0.285f
        Chara.draw(c, p, path, rect, bx, by, r, false, false, sin(time * 0.9f), time * 4f)

        drawCard(c, cardA, 0, "めいろ", "ゆびで よんで ゴールへ はこぶ", Color.rgb(58, 132, 92))
        drawCard(c, cardB, 1, "そうこばん", "にもつを おして ゴールに そろえる", Color.rgb(58, 104, 156))

        postInvalidateOnAnimation()
    }

    private fun drawCard(c: Canvas, rc: RectF, id: Int, title: String, sub: String, col: Int) {
        val down = pressed == id
        val off = if (down) 3f else 0f
        p.style = Paint.Style.FILL
        p.color = Color.argb(90, 0, 0, 0)
        rect.set(rc.left, rc.top + 9f, rc.right, rc.bottom + 9f)
        c.drawRoundRect(rect, rc.height() * 0.16f, rc.height() * 0.16f, p)
        p.color = if (down) darken(col) else col
        rect.set(rc.left, rc.top + off, rc.right, rc.bottom + off)
        c.drawRoundRect(rect, rc.height() * 0.16f, rc.height() * 0.16f, p)

        p.color = Color.argb(45, 255, 255, 255)
        c.drawCircle(rc.right - rc.height() * 0.42f, rc.centerY() + off, rc.height() * 0.34f, p)

        val cx = rc.right - rc.height() * 0.42f
        val cy = rc.centerY() + off
        val s = rc.height() * 0.26f
        if (id == 0) drawMazeIcon(c, cx, cy, s) else drawBoxIcon(c, cx, cy, s)

        p.textAlign = Paint.Align.LEFT
        p.color = Color.WHITE
        p.textSize = rc.height() * 0.30f
        c.drawText(title, rc.left + rc.height() * 0.24f, rc.centerY() + off - rc.height() * 0.02f, p)
        p.color = Color.argb(225, 245, 250, 240)
        p.textSize = rc.height() * 0.155f
        c.drawText(sub, rc.left + rc.height() * 0.25f, rc.centerY() + off + rc.height() * 0.24f, p)
    }

    private fun darken(col: Int): Int {
        return Color.rgb(
            (Color.red(col) * 0.78f).toInt(),
            (Color.green(col) * 0.78f).toInt(),
            (Color.blue(col) * 0.78f).toInt()
        )
    }

    private fun drawMazeIcon(c: Canvas, cx: Float, cy: Float, s: Float) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.20f
        p.color = Color.rgb(28, 34, 30)
        p.strokeCap = Paint.Cap.ROUND
        c.drawLine(cx - s, cy - s, cx + s * 0.3f, cy - s, p)
        c.drawLine(cx + s * 0.3f, cy - s, cx + s * 0.3f, cy + s * 0.2f, p)
        c.drawLine(cx - s, cy - s, cx - s, cy + s, p)
        c.drawLine(cx - s * 0.3f, cy - s * 0.3f, cx - s * 0.3f, cy + s, p)
        c.drawLine(cx + s, cy - s * 0.4f, cx + s, cy + s, p)
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
        p.color = Color.rgb(250, 206, 44)
        c.drawCircle(cx + s * 0.65f, cy + s * 0.55f, s * 0.20f, p)
    }

    private fun drawBoxIcon(c: Canvas, cx: Float, cy: Float, s: Float) {
        p.style = Paint.Style.FILL
        p.color = Color.rgb(176, 122, 62)
        rect.set(cx - s * 0.8f, cy - s * 0.8f, cx + s * 0.8f, cy + s * 0.8f)
        c.drawRoundRect(rect, s * 0.16f, s * 0.16f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.16f
        p.color = Color.rgb(120, 78, 36)
        c.drawRoundRect(rect, s * 0.16f, s * 0.16f, p)
        c.drawLine(cx - s * 0.8f, cy - s * 0.8f, cx + s * 0.8f, cy + s * 0.8f, p)
        c.drawLine(cx + s * 0.8f, cy - s * 0.8f, cx - s * 0.8f, cy + s * 0.8f, p)
        p.style = Paint.Style.FILL
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        last = 0L
        postInvalidateOnAnimation()
    }
}
