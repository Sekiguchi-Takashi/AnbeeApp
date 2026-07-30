package com.appathy.anbee

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class GameView(ctx: Context) : View(ctx) {

    private class Ant {
        var x = 0f
        var y = 0f
        var hp = 1f
        var st = 0
        var vx = 0f
        var vy = 0f
        var ang = 0f
        var dist = 0f
        var spin = 0f
        var lag = 0f
        var lock = 0f
        var ph = 0f
        var face = 0f
    }

    companion object {
        private const val FOLLOW = 0
        private const val LOST = 1
        private const val SAVED = 2
        private const val DOWN = 3

        private const val TITLE = 0
        private const val PLAY = 1
        private const val CLEAR = 2

        private const val SUN = 0
        private const val RAIN = 1

        private const val WW = 2400f
        private const val WH = 3600f
        private const val TOTAL = 12

        private const val ANT_SPEED = 300f
        private const val CAM_SPEED = 620f
        private const val STOMP_R = 260f
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val clearFx = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

    private var w = 0f
    private var h = 0f
    private var padY = 0f
    private var last = 0L
    private var running = true
    private var inited = false

    private var scene = TITLE
    private var camX = 0f
    private var camY = 0f
    private var time = 0f

    private val ants = ArrayList<Ant>()

    private var fid = -1
    private var fx = 0f
    private var fy = 0f
    private var did = -1
    private var ddir = 0
    private var uid = -1

    private var gauge = 1f
    private var weather = SUN
    private var wTimer = 18f
    private var rainAmt = 0f

    private var gSt = 0
    private var gT = 14f
    private var gx = 0f
    private var gy = 0f
    private var shake = 0f

    private var msg = ""
    private var msgT = 0f

    private val treeX = 1980f
    private val treeY = 470f
    private val treeR = 235f
    private val homeX = 340f
    private val homeY = 3220f
    private val homeR = 175f

    private var shadeR = 150f
    private var umbR = 235f

    private var dcx = 0f
    private var dcy = 0f
    private var dr = 0f
    private var b1x = 0f
    private var b1y = 0f
    private var b2x = 0f
    private var b2y = 0f
    private var br = 0f
    private val stRect = RectF()
    private val seRect = RectF()

    private val rnx = FloatArray(110)
    private val rny = FloatArray(110)

    init {
        isFocusable = true
        for (i in 0 until TOTAL) ants.add(Ant())
        for (i in rnx.indices) {
            rnx[i] = Random.nextFloat()
            rny[i] = Random.nextFloat()
        }
    }

    fun resumeGame() {
        running = true
        last = 0L
        postInvalidateOnAnimation()
    }

    fun pauseGame() {
        running = false
    }

    override fun onSizeChanged(nw: Int, nh: Int, ow: Int, oh: Int) {
        w = nw.toFloat()
        h = nh.toFloat()
        padY = h * 0.65f
        shadeR = min(w, padY) * 0.19f
        umbR = shadeR * 1.55f
        layoutPad()
        if (!inited) {
            inited = true
            resetGame()
        } else {
            for (i in 0 until TOTAL) {
                ants[i].dist = shadeR * (0.30f + (i % 3) * 0.21f)
            }
            camX = clamp(camX, 0f, max(0f, WW - w))
            camY = clamp(camY, 0f, max(0f, WH - padY))
        }
    }

    private fun layoutPad() {
        val ph = h - padY
        dcx = w * 0.21f
        dcy = padY + ph * 0.42f
        dr = min(w * 0.135f, ph * 0.27f)
        br = dr * 0.55f
        b1x = w * 0.63f
        b1y = padY + ph * 0.50f
        b2x = w * 0.85f
        b2y = padY + ph * 0.34f
        val bw = w * 0.17f
        val bh = ph * 0.11f
        val by = padY + ph * 0.80f
        seRect.set(w * 0.5f - bw - 10f, by, w * 0.5f - 10f, by + bh)
        stRect.set(w * 0.5f + 10f, by, w * 0.5f + bw + 10f, by + bh)
    }

    private fun resetGame() {
        time = 0f
        weather = SUN
        wTimer = 20f
        rainAmt = 0f
        gauge = 1f
        gSt = 0
        gT = 16f
        shake = 0f
        msg = ""
        msgT = 0f
        fid = -1
        did = -1
        uid = -1
        ddir = 0
        for (i in 0 until TOTAL) {
            val a = ants[i]
            val t = i.toFloat() / TOTAL * 6.2832f
            a.x = homeX + cos(t) * 90f
            a.y = homeY + sin(t) * 90f
            a.hp = 1f
            a.st = FOLLOW
            a.vx = 0f
            a.vy = 0f
            a.ang = t
            a.dist = shadeR * (0.30f + (i % 3) * 0.21f)
            a.spin = if (i % 2 == 0) 0.55f else -0.45f
            a.lag = 0f
            a.lock = 0f
            a.ph = Random.nextFloat() * 6.2832f
            a.face = 0f
        }
        camX = clamp(homeX - w * 0.5f, 0f, max(0f, WW - w))
        camY = clamp(homeY - padY * 0.5f, 0f, max(0f, WH - padY))
    }

    private fun clamp(v: Float, lo: Float, hi: Float): Float {
        if (v < lo) return lo
        if (v > hi) return hi
        return v
    }

    // ---------------- touch ----------------

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                onDown(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val id = e.getPointerId(i)
                    if (id == fid) {
                        fx = e.getX(i)
                        fy = min(e.getY(i), padY - 2f)
                    } else if (id == did) {
                        ddir = dirOf(e.getX(i), e.getY(i))
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                onUp(e.getPointerId(e.actionIndex))
            }
            MotionEvent.ACTION_CANCEL -> {
                releaseFinger()
                did = -1
                ddir = 0
                uid = -1
            }
        }
        return true
    }

    private fun onDown(id: Int, x: Float, y: Float) {
        if (y < padY) {
            if (scene != PLAY) return
            if (fid < 0) {
                fid = id
                fx = x
                fy = y
            }
            return
        }
        if (abs(x - dcx) < dr * 1.3f && abs(y - dcy) < dr * 1.3f) {
            did = id
            ddir = dirOf(x, y)
            return
        }
        if (hypot(x - b1x, y - b1y) < br * 1.35f) {
            if (scene == PLAY) disband()
            return
        }
        if (hypot(x - b2x, y - b2y) < br * 1.35f) {
            uid = id
            return
        }
        if (stRect.contains(x, y)) {
            if (scene == PLAY) return
            resetGame()
            scene = PLAY
            return
        }
        if (seRect.contains(x, y)) {
            (context as? Activity)?.finish()
        }
    }

    private fun onUp(id: Int) {
        if (id == fid) releaseFinger()
        if (id == did) {
            did = -1
            ddir = 0
        }
        if (id == uid) uid = -1
    }

    private fun releaseFinger() {
        if (fid < 0) return
        val wx = fx + camX
        val wy = fy + camY
        for (a in ants) {
            if (a.st != FOLLOW) continue
            if (hypot(a.x - wx, a.y - wy) > shadeR) scatter(a, wx, wy, 0.9f)
        }
        fid = -1
    }

    private fun dirOf(x: Float, y: Float): Int {
        val dx = x - dcx
        val dy = y - dcy
        if (abs(dx) < dr * 0.28f && abs(dy) < dr * 0.28f) return 0
        return if (abs(dx) > abs(dy)) {
            if (dx < 0) 3 else 4
        } else {
            if (dy < 0) 1 else 2
        }
    }

    private fun scatter(a: Ant, ox: Float, oy: Float, power: Float) {
        var dx = a.x - ox
        var dy = a.y - oy
        var d = hypot(dx, dy)
        if (d < 1f) {
            val t = Random.nextFloat() * 6.2832f
            dx = cos(t)
            dy = sin(t)
            d = 1f
        }
        val s = (330f + Random.nextFloat() * 190f) * power
        a.vx = dx / d * s
        a.vy = dy / d * s
        a.st = LOST
        a.lock = 1.7f
        a.lag = 0f
    }

    private fun disband() {
        val wx = fx + camX
        val wy = fy + camY
        var slow = 0
        for (a in ants) {
            if (a.st != FOLLOW) continue
            val d = hypot(a.x - wx, a.y - wy)
            if (fid >= 0 && d < shadeR * 0.5f) {
                scatter(a, wx, wy, 1.15f)
                a.lag = 0.6f
                slow++
            } else {
                scatter(a, wx, wy, 1.35f)
            }
        }
        msg = if (slow > 0) "かいさん！（" + slow + "ぴき にげおくれ）" else "かいさん！"
        msgT = 1.4f
    }

    // ---------------- update ----------------

    private fun update(dt: Float) {
        if (msgT > 0f) msgT -= dt
        if (shake > 0f) shake = max(0f, shake - dt * 1.8f)
        if (scene != PLAY) return
        time += dt

        if (ddir != 0) {
            val s = CAM_SPEED * dt
            when (ddir) {
                1 -> camY -= s
                2 -> camY += s
                3 -> camX -= s
                4 -> camX += s
            }
            camX = clamp(camX, 0f, max(0f, WW - w))
            camY = clamp(camY, 0f, max(0f, WH - padY))
        }

        wTimer -= dt
        if (wTimer <= 0f) {
            if (weather == SUN) {
                weather = RAIN
                wTimer = 9f + Random.nextFloat() * 5f
                msg = "あめが ふってきた"
                msgT = 2f
            } else {
                weather = SUN
                wTimer = 15f + Random.nextFloat() * 8f
                msg = "はれた"
                msgT = 1.6f
            }
        }
        rainAmt = clamp(rainAmt + (if (weather == RAIN) dt * 1.4f else -dt * 1.4f), 0f, 1f)

        val umbOn = uid >= 0 && gauge > 0.01f && fid >= 0
        if (umbOn && weather == RAIN) {
            gauge = max(0f, gauge - 0.135f * dt)
        } else if (uid < 0) {
            gauge = min(1f, gauge + 0.055f * dt)
        }

        updateGiant(dt)

        val wx = fx + camX
        val wy = fy + camY
        var saved = 0

        for (a in ants) {
            if (a.lock > 0f) a.lock -= dt
            a.ph += dt * 7f

            when (a.st) {
                FOLLOW -> {
                    if (fid >= 0) {
                        a.ang += a.spin * dt
                        val tx = wx + cos(a.ang) * a.dist
                        val ty = wy + sin(a.ang) * a.dist
                        step(a, tx, ty, ANT_SPEED * dt)
                    } else {
                        a.x += sin(a.ph * 0.4f) * 9f * dt
                        a.y += cos(a.ph * 0.33f) * 9f * dt
                    }
                }
                LOST -> {
                    if (a.lag > 0f) {
                        a.lag -= dt
                    } else {
                        a.x += a.vx * dt
                        a.y += a.vy * dt
                        a.vx *= 0.955f
                        a.vy *= 0.955f
                        if (hypot(a.vx, a.vy) < 26f) {
                            a.x += cos(a.ph * 0.21f) * 26f * dt
                            a.y += sin(a.ph * 0.17f) * 26f * dt
                        }
                    }
                    if (fid >= 0 && a.lock <= 0f && hypot(a.x - wx, a.y - wy) < shadeR * 0.85f) {
                        a.st = FOLLOW
                        a.vx = 0f
                        a.vy = 0f
                    }
                }
                DOWN -> {
                    val tx = homeX + cos(a.ph * 0.13f) * homeR * 0.55f
                    val ty = homeY + sin(a.ph * 0.11f) * homeR * 0.55f
                    step(a, tx, ty, 46f * dt)
                    a.hp = min(0.55f, a.hp + 0.10f * dt)
                    if (fid >= 0 && hypot(a.x - wx, a.y - wy) < shadeR * 0.85f) {
                        a.st = FOLLOW
                        a.hp = max(a.hp, 0.5f)
                    }
                }
                SAVED -> {
                    val tx = treeX + cos(a.ang) * treeR * 0.55f
                    val ty = treeY + sin(a.ang) * treeR * 0.55f
                    step(a, tx, ty, 70f * dt)
                    a.hp = min(1f, a.hp + 0.2f * dt)
                    saved++
                }
            }

            a.x = clamp(a.x, 20f, WW - 20f)
            a.y = clamp(a.y, 20f, WH - 20f)

            if (a.st == FOLLOW || a.st == LOST) {
                if (hypot(a.x - treeX, a.y - treeY) < treeR * 0.8f) {
                    a.st = SAVED
                    a.vx = 0f
                    a.vy = 0f
                    continue
                }
                val inTree = hypot(a.x - treeX, a.y - treeY) < treeR
                val inHome = hypot(a.x - homeX, a.y - homeY) < homeR
                val inShade = fid >= 0 && hypot(a.x - wx, a.y - wy) < shadeR
                val inUmb = umbOn && hypot(a.x - wx, a.y - wy) < umbR
                val safe = if (weather == RAIN) inTree || inHome || inUmb
                else inTree || inHome || inShade
                if (safe) {
                    a.hp = min(1f, a.hp + 0.075f * dt)
                } else {
                    a.hp -= (if (weather == RAIN) 0.165f else 0.105f) * dt
                }
                if (a.hp <= 0f) {
                    a.st = DOWN
                    a.hp = 0.12f
                    a.x = homeX + (Random.nextFloat() - 0.5f) * homeR
                    a.y = homeY + (Random.nextFloat() - 0.5f) * homeR
                    a.vx = 0f
                    a.vy = 0f
                    msg = "1ぴき もどされた"
                    msgT = 1.6f
                }
            }
        }

        if (saved >= TOTAL) {
            scene = CLEAR
            msg = "ぜんいん ゴール！"
            msgT = 6f
        }
    }

    private fun step(a: Ant, tx: Float, ty: Float, s: Float) {
        val dx = tx - a.x
        val dy = ty - a.y
        val d = hypot(dx, dy)
        if (d < 1.2f) return
        val m = min(s, d)
        a.x += dx / d * m
        a.y += dy / d * m
        a.face = dx
    }

    private fun updateGiant(dt: Float) {
        when (gSt) {
            0 -> {
                gT -= dt
                if (gT <= 0f) {
                    var n = 0
                    var sx = 0f
                    var sy = 0f
                    for (a in ants) {
                        if (a.st == FOLLOW || a.st == LOST) {
                            sx += a.x
                            sy += a.y
                            n++
                        }
                    }
                    if (n >= 2) {
                        gx = sx / n
                        gy = sy / n
                        gSt = 1
                        gT = 2.6f
                        msg = "なにか くる！"
                        msgT = 2.2f
                    } else {
                        gT = 5f
                    }
                }
            }
            1 -> {
                gT -= dt
                var n = 0
                var sx = 0f
                var sy = 0f
                for (a in ants) {
                    if (a.st == FOLLOW) {
                        sx += a.x
                        sy += a.y
                        n++
                    }
                }
                if (n > 0) {
                    gx += (sx / n - gx) * min(1f, dt * 1.1f)
                    gy += (sy / n - gy) * min(1f, dt * 1.1f)
                }
                if (gT <= 0f) {
                    gSt = 2
                    gT = 0.6f
                    shake = 1f
                    stomp()
                }
            }
            2 -> {
                gT -= dt
                if (gT <= 0f) {
                    gSt = 0
                    gT = 13f + Random.nextFloat() * 9f
                }
            }
        }
    }

    private fun stomp() {
        var hit = 0
        for (a in ants) {
            if (a.st != FOLLOW && a.st != LOST) continue
            val d = hypot(a.x - gx, a.y - gy)
            if (d < STOMP_R) {
                val k = 1f - d / STOMP_R
                a.hp -= 0.35f + 0.75f * k
                hit++
                if (a.hp > 0f) scatter(a, gx, gy, 1.2f)
            }
        }
        for (a in ants) {
            if ((a.st == FOLLOW || a.st == LOST) && a.hp <= 0f) {
                a.st = DOWN
                a.hp = 0.12f
                a.x = homeX + (Random.nextFloat() - 0.5f) * homeR
                a.y = homeY + (Random.nextFloat() - 0.5f) * homeR
                a.vx = 0f
                a.vy = 0f
            }
        }
        msg = if (hit == 0) "うまく にげた！" else "ふまれた！（" + hit + "ぴき）"
        msgT = 2f
    }

    // ---------------- draw ----------------

    override fun onDraw(c: Canvas) {
        val now = System.nanoTime()
        if (last == 0L) last = now
        var dt = (now - last) / 1e9f
        last = now
        if (dt > 0.05f) dt = 0.05f
        if (dt < 0f) dt = 0f
        update(dt)

        c.drawColor(Color.rgb(24, 26, 30))

        c.save()
        c.clipRect(0f, 0f, w, padY)
        if (shake > 0f) {
            c.translate((Random.nextFloat() - 0.5f) * 26f * shake, (Random.nextFloat() - 0.5f) * 26f * shake)
        }
        drawWorld(c)
        c.restore()

        drawHud(c)
        drawPad(c)
        if (scene != PLAY) drawOverlay(c)

        if (running) postInvalidateOnAnimation()
    }

    private fun drawWorld(c: Canvas) {
        p.xfermode = null
        p.style = Paint.Style.FILL

        p.color = Color.rgb(122, 172, 90)
        c.drawRect(-40f, -40f, w + 40f, padY + 40f, p)

        val step = 115f
        p.color = Color.rgb(110, 158, 80)
        var gxw = (Math.floor((camX / step).toDouble()).toFloat()) * step
        while (gxw < camX + w + step) {
            var gyw = (Math.floor((camY / step).toDouble()).toFloat()) * step
            while (gyw < camY + padY + step) {
                val k = (((gxw / step).toInt() * 37 + (gyw / step).toInt() * 19) % 7 + 7) % 7
                c.drawCircle(gxw - camX + k * 7f, gyw - camY + k * 6f, 8f + k, p)
                gyw += step
            }
            gxw += step
        }

        p.color = Color.rgb(96, 142, 70)
        c.drawRect(-camX, -camY - 30f, WW - camX, -camY, p)
        c.drawRect(-camX, WH - camY, WW - camX, WH - camY + 30f, p)

        drawShade(c, homeX - camX, homeY - camY, homeR, 78)
        drawShade(c, treeX - camX, treeY - camY, treeR, 92)
        if (fid >= 0 && scene == PLAY) drawShade(c, fx, fy, shadeR, 96)

        drawTree(c, homeX - camX, homeY - camY, homeR, false)
        drawTree(c, treeX - camX, treeY - camY, treeR, true)

        for (a in ants) drawAnt(c, a)

        drawSunLayer(c)
        drawRain(c)
        drawGiant(c)
    }

    private fun drawShade(c: Canvas, sx: Float, sy: Float, r: Float, a: Int) {
        p.xfermode = null
        p.style = Paint.Style.FILL
        p.color = Color.argb(a, 22, 46, 26)
        c.drawCircle(sx, sy, r, p)
    }

    private fun drawTree(c: Canvas, sx: Float, sy: Float, r: Float, big: Boolean) {
        if (sx < -r * 2 || sx > w + r * 2 || sy < -r * 2 || sy > padY + r * 2) return
        p.style = Paint.Style.FILL
        p.color = Color.rgb(104, 74, 48)
        val tw = r * 0.16f
        c.drawRect(sx - tw, sy - r * 0.1f, sx + tw, sy + r * 0.62f, p)
        p.color = if (big) Color.rgb(52, 122, 62) else Color.rgb(64, 134, 74)
        c.drawCircle(sx, sy - r * 0.30f, r * 0.62f, p)
        c.drawCircle(sx - r * 0.46f, sy - r * 0.02f, r * 0.46f, p)
        c.drawCircle(sx + r * 0.46f, sy - r * 0.02f, r * 0.46f, p)
        c.drawCircle(sx, sy - r * 0.62f, r * 0.42f, p)
        p.color = if (big) Color.rgb(72, 152, 82) else Color.rgb(84, 166, 94)
        c.drawCircle(sx - r * 0.18f, sy - r * 0.44f, r * 0.30f, p)
        c.drawCircle(sx + r * 0.26f, sy - r * 0.20f, r * 0.24f, p)
        if (big) {
            p.color = Color.argb(200, 255, 255, 255)
            p.textSize = r * 0.20f
            p.textAlign = Paint.Align.CENTER
            c.drawText("ゴール", sx, sy + r * 0.92f, p)
            p.textAlign = Paint.Align.LEFT
        }
    }

    private fun drawAnt(c: Canvas, a: Ant) {
        val sx = a.x - camX
        val sy = a.y - camY
        if (sx < -40f || sx > w + 40f || sy < -40f || sy > padY + 40f) return
        val r = 4.5f + 10f * clamp(a.hp, 0f, 1f)
        val wob = sin(a.ph) * r * 0.16f

        p.style = Paint.Style.FILL
        p.color = Color.argb(60, 0, 0, 0)
        c.drawOval(sx - r, sy + r * 0.55f, sx + r, sy + r * 1.25f, p)

        p.color = if (a.hp < 0.35f) Color.rgb(150, 116, 70) else Color.rgb(74, 48, 26)
        c.drawCircle(sx, sy + wob * 0.3f, r * 0.78f, p)
        c.drawCircle(sx, sy - r * 0.85f + wob, r * 0.58f, p)
        p.color = Color.rgb(240, 196, 66)
        c.drawCircle(sx, sy + wob * 0.3f, r * 0.40f, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1.4f, r * 0.13f)
        p.color = Color.rgb(52, 34, 18)
        c.drawLine(sx - r * 0.30f, sy - r * 1.20f + wob, sx - r * 0.70f, sy - r * 1.85f + wob, p)
        c.drawLine(sx + r * 0.30f, sy - r * 1.20f + wob, sx + r * 0.70f, sy - r * 1.85f + wob, p)
        p.style = Paint.Style.FILL

        p.color = Color.rgb(250, 250, 250)
        c.drawCircle(sx - r * 0.22f, sy - r * 0.92f + wob, r * 0.15f, p)
        c.drawCircle(sx + r * 0.22f, sy - r * 0.92f + wob, r * 0.15f, p)
    }

    private fun drawSunLayer(c: Canvas) {
        val k = 1f - rainAmt
        if (k <= 0.02f) return
        val sc = c.saveLayer(-40f, -40f, w + 40f, padY + 40f, null)
        p.xfermode = null
        p.style = Paint.Style.FILL
        p.color = Color.argb((104 * k).toInt(), 255, 214, 96)
        c.drawRect(-40f, -40f, w + 40f, padY + 40f, p)
        p.xfermode = clearFx
        c.drawCircle(treeX - camX, treeY - camY, treeR, p)
        c.drawCircle(homeX - camX, homeY - camY, homeR, p)
        if (fid >= 0 && scene == PLAY) c.drawCircle(fx, fy, shadeR, p)
        p.xfermode = null
        c.restoreToCount(sc)
    }

    private fun drawRain(c: Canvas) {
        if (rainAmt <= 0.02f) return
        p.xfermode = null
        p.style = Paint.Style.FILL
        p.color = Color.argb((70 * rainAmt).toInt(), 40, 60, 110)
        c.drawRect(-40f, -40f, w + 40f, padY + 40f, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = 2.4f
        p.color = Color.argb((170 * rainAmt).toInt(), 210, 230, 255)
        val n = (rnx.size * rainAmt).toInt()
        for (i in 0 until n) {
            val yy = ((rny[i] + time * 1.25f) % 1f) * (padY + 60f) - 30f
            val xx = ((rnx[i] + yy / (padY + 60f) * 0.16f) % 1f) * w
            c.drawLine(xx, yy, xx - 9f, yy + 30f, p)
        }
        p.style = Paint.Style.FILL

        val umbOn = uid >= 0 && gauge > 0.01f && fid >= 0 && scene == PLAY
        if (umbOn) {
            p.style = Paint.Style.FILL
            p.color = Color.argb(96, 226, 70, 70)
            c.drawCircle(fx, fy, umbR, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 6f
            p.color = Color.rgb(226, 70, 70)
            c.drawCircle(fx, fy, umbR, p)
            p.strokeWidth = 3f
            p.color = Color.argb(150, 255, 255, 255)
            for (i in 0 until 8) {
                val t = i / 8f * 6.2832f
                c.drawLine(fx, fy, fx + cos(t) * umbR, fy + sin(t) * umbR, p)
            }
            p.style = Paint.Style.FILL
        }
    }

    private fun drawGiant(c: Canvas) {
        p.xfermode = null
        val sx = gx - camX
        val sy = gy - camY
        if (gSt == 1) {
            val k = clamp(gT / 2.6f, 0f, 1f)
            val r = STOMP_R * (1f + k * 2.3f)
            p.style = Paint.Style.FILL
            p.color = Color.argb((150 * (1f - k)).toInt() + 40, 10, 10, 14)
            c.drawCircle(sx, sy, r, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 7f
            p.color = Color.argb(230, 255, 70, 70)
            c.drawCircle(sx, sy, STOMP_R, p)
            p.strokeWidth = 3f
            for (i in 0 until 6) {
                val t = i / 6f * 6.2832f + time * 2f
                c.drawLine(
                    sx + cos(t) * STOMP_R * 0.75f, sy + sin(t) * STOMP_R * 0.75f,
                    sx + cos(t) * STOMP_R, sy + sin(t) * STOMP_R, p
                )
            }
            p.style = Paint.Style.FILL
        } else if (gSt == 2) {
            val k = clamp(gT / 0.6f, 0f, 1f)
            p.style = Paint.Style.FILL
            p.color = Color.argb((200 * k).toInt(), 20, 16, 14)
            c.drawCircle(sx, sy, STOMP_R * (0.6f + 0.4f * k), p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 14f * k
            p.color = Color.argb((220 * k).toInt(), 255, 240, 200)
            c.drawCircle(sx, sy, STOMP_R * (1f + (1f - k) * 1.4f), p)
            p.style = Paint.Style.FILL
        }
    }

    private fun drawHud(c: Canvas) {
        p.xfermode = null
        p.style = Paint.Style.FILL
        p.color = Color.argb(150, 0, 0, 0)
        c.drawRect(0f, 0f, w, h * 0.062f, p)

        var follow = 0
        var lost = 0
        var saved = 0
        var down = 0
        for (a in ants) {
            when (a.st) {
                FOLLOW -> follow++
                LOST -> lost++
                SAVED -> saved++
                DOWN -> down++
            }
        }

        p.color = Color.WHITE
        p.textSize = h * 0.026f
        p.textAlign = Paint.Align.LEFT
        val ty = h * 0.042f
        c.drawText("つれてる " + follow + "  はぐれ " + lost, w * 0.03f, ty, p)
        p.textAlign = Paint.Align.RIGHT
        c.drawText("ゴール " + saved + "/" + TOTAL + "  すあな " + down, w * 0.97f, ty, p)
        p.textAlign = Paint.Align.LEFT

        p.textSize = h * 0.024f
        p.color = if (weather == RAIN) Color.rgb(150, 200, 255) else Color.rgb(255, 226, 120)
        p.textAlign = Paint.Align.CENTER
        c.drawText(if (weather == RAIN) "あめ" else "はれ", w * 0.5f, ty, p)

        if (msgT > 0f && scene == PLAY) {
            p.color = Color.argb(150, 0, 0, 0)
            p.textAlign = Paint.Align.CENTER
            p.textSize = h * 0.033f
            val tw = p.measureText(msg)
            rect.set(w * 0.5f - tw * 0.6f, padY - h * 0.075f, w * 0.5f + tw * 0.6f, padY - h * 0.022f)
            c.drawRoundRect(rect, 14f, 14f, p)
            p.color = Color.rgb(255, 240, 160)
            c.drawText(msg, w * 0.5f, padY - h * 0.036f, p)
        }
        p.textAlign = Paint.Align.LEFT

        // ゴール方向のガイド矢印
        if (scene == PLAY) {
            val tsx = treeX - camX
            val tsy = treeY - camY
            if (tsx < -treeR || tsx > w + treeR || tsy < -treeR || tsy > padY + treeR) {
                val dx = tsx - w * 0.5f
                val dy = tsy - padY * 0.5f
                val d = max(1f, hypot(dx, dy))
                val ax = w * 0.5f + dx / d * min(w, padY) * 0.36f
                val ay = padY * 0.5f + dy / d * min(w, padY) * 0.36f
                p.color = Color.argb(190, 255, 240, 140)
                path.reset()
                val ang = Math.atan2(dy.toDouble(), dx.toDouble()).toFloat()
                path.moveTo(ax + cos(ang) * 26f, ay + sin(ang) * 26f)
                path.lineTo(ax + cos(ang + 2.5f) * 20f, ay + sin(ang + 2.5f) * 20f)
                path.lineTo(ax + cos(ang - 2.5f) * 20f, ay + sin(ang - 2.5f) * 20f)
                path.close()
                c.drawPath(path, p)
            }
        }
    }

    private fun drawPad(c: Canvas) {
        p.xfermode = null
        p.style = Paint.Style.FILL
        p.color = Color.rgb(214, 206, 190)
        c.drawRect(0f, padY, w, h, p)
        p.color = Color.rgb(150, 40, 40)
        c.drawRect(0f, padY, w, padY + (h - padY) * 0.055f, p)
        p.color = Color.rgb(46, 44, 46)
        c.drawRect(0f, padY + (h - padY) * 0.055f, w, padY + (h - padY) * 0.075f, p)

        // 十字キー
        p.color = Color.rgb(38, 36, 38)
        val a1 = dr * 0.36f
        rect.set(dcx - a1, dcy - dr, dcx + a1, dcy + dr)
        c.drawRoundRect(rect, 10f, 10f, p)
        rect.set(dcx - dr, dcy - a1, dcx + dr, dcy + a1)
        c.drawRoundRect(rect, 10f, 10f, p)
        p.color = Color.rgb(70, 68, 70)
        c.drawCircle(dcx, dcy, a1 * 0.55f, p)
        if (ddir != 0) {
            p.color = Color.argb(140, 250, 230, 120)
            when (ddir) {
                1 -> c.drawCircle(dcx, dcy - dr * 0.62f, a1 * 0.5f, p)
                2 -> c.drawCircle(dcx, dcy + dr * 0.62f, a1 * 0.5f, p)
                3 -> c.drawCircle(dcx - dr * 0.62f, dcy, a1 * 0.5f, p)
                4 -> c.drawCircle(dcx + dr * 0.62f, dcy, a1 * 0.5f, p)
            }
        }
        p.color = Color.rgb(90, 88, 88)
        p.textSize = (h - padY) * 0.075f
        p.textAlign = Paint.Align.CENTER
        c.drawText("がめん スクロール", dcx, dcy + dr * 1.42f, p)

        // 解散ボタン
        p.color = Color.rgb(58, 56, 58)
        c.drawCircle(b1x, b1y + 5f, br, p)
        p.color = Color.rgb(196, 42, 46)
        c.drawCircle(b1x, b1y, br, p)
        p.color = Color.rgb(255, 255, 255)
        p.textSize = br * 0.52f
        c.drawText("かいさん", b1x, b1y + br * 0.19f, p)

        // 傘ボタン
        p.color = Color.rgb(58, 56, 58)
        c.drawCircle(b2x, b2y + 5f, br, p)
        p.color = if (uid >= 0) Color.rgb(120, 30, 32) else Color.rgb(196, 42, 46)
        c.drawCircle(b2x, b2y, br, p)
        p.color = Color.rgb(255, 255, 255)
        p.textSize = br * 0.62f
        c.drawText("かさ", b2x, b2y + br * 0.22f, p)

        // 傘ゲージ
        val gw = br * 2.2f
        val gh = (h - padY) * 0.048f
        rect.set(b2x - gw * 0.5f, b2y + br * 1.35f, b2x + gw * 0.5f, b2y + br * 1.35f + gh)
        p.color = Color.rgb(80, 78, 78)
        c.drawRoundRect(rect, gh * 0.5f, gh * 0.5f, p)
        rect.set(
            b2x - gw * 0.5f, b2y + br * 1.35f,
            b2x - gw * 0.5f + gw * clamp(gauge, 0f, 1f), b2y + br * 1.35f + gh
        )
        p.color = if (gauge < 0.25f) Color.rgb(230, 90, 60) else Color.rgb(90, 190, 230)
        c.drawRoundRect(rect, gh * 0.5f, gh * 0.5f, p)

        // スタート / セレクト
        p.color = Color.rgb(60, 58, 58)
        c.drawRoundRect(seRect, seRect.height() * 0.5f, seRect.height() * 0.5f, p)
        c.drawRoundRect(stRect, stRect.height() * 0.5f, stRect.height() * 0.5f, p)
        p.color = Color.rgb(220, 216, 210)
        p.textSize = seRect.height() * 0.62f
        c.drawText("しゅうりょう", seRect.centerX(), seRect.centerY() + seRect.height() * 0.22f, p)
        c.drawText("スタート", stRect.centerX(), stRect.centerY() + stRect.height() * 0.22f, p)
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawOverlay(c: Canvas) {
        p.xfermode = null
        p.style = Paint.Style.FILL
        p.color = Color.argb(190, 8, 14, 10)
        c.drawRect(0f, 0f, w, padY, p)
        p.textAlign = Paint.Align.CENTER

        if (scene == TITLE) {
            p.color = Color.rgb(250, 214, 90)
            p.textSize = padY * 0.13f
            c.drawText("アンビー", w * 0.5f, padY * 0.30f, p)
            p.color = Color.rgb(230, 230, 220)
            p.textSize = padY * 0.040f
            c.drawText("ゆびの かげで むれを まもる", w * 0.5f, padY * 0.40f, p)
            p.textSize = padY * 0.036f
            p.color = Color.rgb(200, 210, 200)
            c.drawText("ゆびで がめんを おすと むれが あつまる", w * 0.5f, padY * 0.52f, p)
            c.drawText("ゆびを はなすと かげの そとの こは はぐれる", w * 0.5f, padY * 0.58f, p)
            c.drawText("あめは「かさ」  きょだいせいぶつは「かいさん」", w * 0.5f, padY * 0.64f, p)
            c.drawText("じゅうじキーで がめんを うごかして", w * 0.5f, padY * 0.70f, p)
            c.drawText("おおきな きの したへ ぜんいん はこぼう", w * 0.5f, padY * 0.76f, p)
            p.color = Color.rgb(250, 214, 90)
            p.textSize = padY * 0.05f
            c.drawText("スタート を おす", w * 0.5f, padY * 0.90f, p)
        } else if (scene == CLEAR) {
            p.color = Color.rgb(250, 214, 90)
            p.textSize = padY * 0.11f
            c.drawText("ぜんいん ゴール！", w * 0.5f, padY * 0.38f, p)
            p.color = Color.rgb(235, 235, 225)
            p.textSize = padY * 0.05f
            c.drawText("タイム " + time.toInt() + " びょう", w * 0.5f, padY * 0.52f, p)
            p.textSize = padY * 0.045f
            c.drawText("スタート で もういちど", w * 0.5f, padY * 0.68f, p)
        }
        p.textAlign = Paint.Align.LEFT
    }
}
