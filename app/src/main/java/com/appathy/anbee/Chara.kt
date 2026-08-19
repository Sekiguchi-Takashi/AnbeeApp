package com.appathy.anbee

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

// アンビーの描画。めいろ／そうこばん の両モードで共有する。
// ここを直すと両方に反映されるので、片方だけ変えたいときは引数を増やすこと。
object Chara {

    // sx, sy はキャラの足元。r は基準サイズ（全高はおよそ 4.1r）
    fun draw(
        c: Canvas,
        p: Paint,
        path: Path,
        rect: RectF,
        sx: Float,
        sy: Float,
        r: Float,
        pale: Boolean,
        gold: Boolean,
        face: Float,
        ph: Float
    ) {
        val bob = sin(ph) * r * 0.10f

        p.style = Paint.Style.FILL
        p.color = Color.argb(70, 0, 0, 0)
        c.drawOval(sx - r * 0.72f, sy - r * 0.16f, sx + r * 0.72f, sy + r * 0.30f, p)

        val body = if (pale) Color.rgb(126, 150, 190) else Color.rgb(56, 104, 196)
        val dark = if (pale) Color.rgb(102, 126, 166) else Color.rgb(38, 76, 156)

        // あし
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.20f
        p.color = dark
        c.drawLine(sx - r * 0.26f, sy - r * 0.55f + bob, sx - r * 0.30f, sy, p)
        c.drawLine(sx + r * 0.26f, sy - r * 0.55f + bob, sx + r * 0.30f, sy, p)
        p.style = Paint.Style.FILL
        c.drawOval(sx - r * 0.46f, sy - r * 0.13f, sx - r * 0.12f, sy + r * 0.08f, p)
        c.drawOval(sx + r * 0.12f, sy - r * 0.13f, sx + r * 0.46f, sy + r * 0.08f, p)

        // どうたい
        p.color = body
        c.drawOval(sx - r * 0.44f, sy - r * 1.28f + bob, sx + r * 0.44f, sy - r * 0.44f + bob, p)

        // うで
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.17f
        p.color = body
        c.drawLine(sx - r * 0.40f, sy - r * 0.98f + bob, sx - r * 0.14f, sy - r * 0.80f + bob, p)
        c.drawLine(sx + r * 0.40f, sy - r * 0.98f + bob, sx + r * 0.14f, sy - r * 0.80f + bob, p)
        p.style = Paint.Style.FILL

        // あたま（たまねぎ型）
        val hb = sy - r * 1.18f + bob
        val ht = sy - r * 2.62f + bob
        path.reset()
        path.moveTo(sx, ht)
        path.cubicTo(sx + r * 0.62f, ht + r * 0.42f, sx + r * 0.80f, hb - r * 0.42f, sx, hb)
        path.cubicTo(sx - r * 0.80f, hb - r * 0.42f, sx - r * 0.62f, ht + r * 0.42f, sx, ht)
        path.close()
        p.color = body
        c.drawPath(path, p)
        p.color = Color.argb(60, 150, 90, 200)
        c.drawCircle(sx + r * 0.26f, hb - r * 0.52f, r * 0.26f, p)

        // め
        val ey = sy - r * 1.86f + bob
        val ed = r * 0.06f * face
        p.color = Color.rgb(252, 252, 252)
        c.drawOval(sx - r * 0.40f, ey - r * 0.30f, sx - r * 0.02f, ey + r * 0.30f, p)
        c.drawOval(sx + r * 0.02f, ey - r * 0.30f, sx + r * 0.40f, ey + r * 0.30f, p)
        p.color = Color.rgb(20, 22, 28)
        c.drawCircle(sx - r * 0.20f + ed, ey + r * 0.02f, r * 0.13f, p)
        c.drawCircle(sx + r * 0.20f + ed, ey + r * 0.02f, r * 0.13f, p)
        p.color = Color.WHITE
        c.drawCircle(sx - r * 0.24f + ed, ey - r * 0.05f, r * 0.045f, p)
        c.drawCircle(sx + r * 0.16f + ed, ey - r * 0.05f, r * 0.045f, p)

        // くち
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.09f
        p.color = Color.rgb(24, 30, 44)
        rect.set(sx - r * 0.20f, ey + r * 0.30f, sx + r * 0.20f, ey + r * 0.74f)
        if (pale) c.drawArc(rect, 200f, 140f, false, p) else c.drawArc(rect, 20f, 140f, false, p)
        p.style = Paint.Style.FILL

        // くき と は
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.13f
        p.color = Color.rgb(92, 168, 72)
        val fy = ht - r * 1.05f
        c.drawLine(sx, ht, sx, fy, p)
        p.style = Paint.Style.FILL
        p.color = Color.rgb(104, 182, 82)
        path.reset()
        path.moveTo(sx - r * 0.02f, ht - r * 0.10f)
        path.quadTo(sx - r * 0.62f, ht - r * 0.46f, sx - r * 0.34f, ht + r * 0.12f)
        path.quadTo(sx - r * 0.16f, ht - r * 0.02f, sx - r * 0.02f, ht - r * 0.10f)
        path.close()
        c.drawPath(path, p)
        path.reset()
        path.moveTo(sx + r * 0.02f, ht - r * 0.10f)
        path.quadTo(sx + r * 0.62f, ht - r * 0.46f, sx + r * 0.34f, ht + r * 0.12f)
        path.quadTo(sx + r * 0.16f, ht - r * 0.02f, sx + r * 0.02f, ht - r * 0.10f)
        path.close()
        c.drawPath(path, p)

        // はな
        val pr = r * 0.42f
        p.color = when {
            gold -> Color.rgb(255, 236, 120)
            pale -> Color.rgb(206, 196, 150)
            else -> Color.rgb(250, 206, 44)
        }
        for (i in 0 until 5) {
            val t = i / 5f * 6.2832f
            c.drawCircle(sx + cos(t) * pr * 0.72f, fy + sin(t) * pr * 0.72f, pr * 0.62f, p)
        }
        p.color = if (pale) Color.rgb(186, 176, 136) else Color.rgb(238, 160, 32)
        c.drawCircle(sx, fy, pr * 0.42f, p)
    }

    // 頭の上に出す一文字（「!」「?」「＜」など）
    fun mark(c: Canvas, p: Paint, sx: Float, sy: Float, r: Float, s: String, col: Int) {
        p.style = Paint.Style.FILL
        p.color = col
        p.textAlign = Paint.Align.CENTER
        p.textSize = r * 0.95f
        c.drawText(s, sx, sy - r * 4.2f, p)
        p.textAlign = Paint.Align.LEFT
    }
}
