package com.appathy.anbee

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
        var tx = 0f
        var ty = 0f
        var hp = 1f
        var st = 0
        var ang = 0f
        var dist = 0f
        var spin = 0f
        var lag = 0f
        var lock = 0f
        var ph = 0f
        var tone = 0f
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

        private const val B_NONE = 0
        private const val B_CIRCLE = 1
        private const val B_DIVE = 2
        private const val B_LEAVE = 3

        private const val WW = 2400f
        private const val WH = 3600f
        private const val TOTAL = 12

        private const val ANT_SPEED = 320f
        private const val LOST_SPEED = 340f
        private const val CAM_SPEED = 660f
        private const val HIT_R = 300f
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val clearFx = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

    private var w = 0f
    private var h = 0f
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
    private var wTimer = 20f
    private var rainAmt = 0f

    private var bSt = B_NONE
    private var bT = 14f
    private var bx = 0f
    private var by = 0f
    private var tgx = 0f
    private var tgy = 0f
    private var ctrX = 0f
    private var ctrY = 0f
    private var alt = 1f
    private var orbit = 0f
    private var shake = 0f

    private var msg = ""
    private var msgT = 0f

    private val treeX = 1980f
    private val treeY = 470f
    private val treeR = 300f
    private val homeX = 340f
    private val homeY = 3220f
    private val homeR = 230f

    private var shadeR = 200f
    private var umbR = 310f
    private var antR = 30f

    private var dcx = 0f
    private var dcy = 0f
    private var dr = 0f
    private var b1x = 0f
    private var b1y = 0f
    private var b2x = 0f
    private var b2y = 0f
    private var br = 0f

    private val rnx = FloatArray(120)
    private val rny = FloatArray(120)

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
        val m = min(w, h)
        shadeR = m * 0.24f
        umbR = shadeR * 1.55f
        antR = m * 0.030f
        layoutPad()
        if (!inited) {
            inited = true
            resetGame()
        } else {
            for (i in 0 until TOTAL) ants[i].dist = ringDist(i)
            camX = clamp(camX, 0f, max(0f, WW - w))
            camY = clamp(camY, 0f, max(0f, WH - h))
        }
    }

    private fun ringDist(i: Int): Float {
        return shadeR * (0.32f + (i % 3) * 0.19f)
    }

    private fun layoutPad() {
        dr = min(w * 0.145f, h * 0.085f)
        dcx = w * 0.055f + dr
        dcy = h - dr - h * 0.045f
        br = dr * 0.62f
        b2x = dcx - dr * 0.10f
        b2y = dcy - dr - br - h * 0.018f
        b1x = b2x
        b1y = b2y - br * 2f - h * 0.020f
    }

    private fun resetGame() {
        time = 0f
        weather = SUN
        wTimer = 20f
        rainAmt = 0f
        gauge = 1f
        bSt = B_NONE
        bT = 15f
        alt = 1f
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
            a.x = homeX + cos(t) * 110f
            a.y = homeY + sin(t) * 110f
            a.tx = a.x
            a.ty = a.y
            a.hp = 1f
            a.st = FOLLOW
            a.ang = t
            a.dist = ringDist(i)
            a.spin = if (i % 2 == 0) 0.5f else -0.4f
            a.lag = 0f
            a.lock = 0f
            a.ph = Random.nextFloat() * 6.2832f
            a.tone = Random.nextFloat()
        }
        camX = clamp(homeX - w * 0.5f, 0f, max(0f, WW - w))
        camY = clamp(homeY - h * 0.5f, 0f, max(0f, WH - h))
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
                        fy = e.getY(i)
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
        if (abs(x - dcx) < dr * 1.25f && abs(y - dcy) < dr * 1.25f) {
            did = id
            ddir = dirOf(x, y)
            return
        }
        if (hypot(x - b2x, y - b2y) < br * 1.35f) {
            uid = id
            return
        }
        if (hypot(x - b1x, y - b1y) < br * 1.35f) {
            if (scene == PLAY) disband()
            return
        }
        if (scene != PLAY) {
            resetGame()
            scene = PLAY
            return
        }
        if (fid < 0) {
            fid = id
            fx = x
            fy = y
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
            if (hypot(a.x - wx, a.y - wy) > shadeR) scatter(a, wx, wy, shadeR * 0.85f)
        }
        fid = -1
    }

    private fun dirOf(x: Float, y: Float): Int {
        val dx = x - dcx
        val dy = y - dcy
        if (abs(dx) < dr * 0.26f && abs(dy) < dr * 0.26f) return 0
        return if (abs(dx) > abs(dy)) {
            if (dx < 0) 3 else 4
        } else {
            if (dy < 0) 1 else 2
        }
    }

    private fun scatter(a: Ant, ox: Float, oy: Float, dist: Float) {
        var dx = a.x - ox
        var dy = a.y - oy
        var d = hypot(dx, dy)
        if (d < 1f) {
            val t = Random.nextFloat() * 6.2832f
            dx = cos(t)
            dy = sin(t)
            d = 1f
        }
        val len = dist * (0.7f + Random.nextFloat() * 0.6f)
        val m = antR * 3.2f
        a.tx = clamp(clamp(a.x + dx / d * len, camX + m, camX + w - m), 60f, WW - 60f)
        a.ty = clamp(clamp(a.y + dy / d * len, camY + m, camY + h - m), 60f, WH - 60f)
        a.st = LOST
        a.lock = 1.6f
        a.lag = 0f
    }

    private fun disband() {
        var wx = fx + camX
        var wy = fy + camY
        if (fid < 0) {
            if (!flockCenter(false)) return
            wx = ctrX
            wy = ctrY
        }
        var slow = 0
        for (a in ants) {
            if (a.st != FOLLOW) continue
            val d = hypot(a.x - wx, a.y - wy)
            if (fid >= 0 && d < shadeR * 0.5f) {
                scatter(a, wx, wy, shadeR * 1.15f)
                a.lag = 0.6f
                slow++
            } else {
                scatter(a, wx, wy, shadeR * 1.5f)
            }
        }
        msg = if (slow > 0) "ふえ！（" + slow + "ぴき にげおくれ）" else "ふえ！ ちらばれ"
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
            camY = clamp(camY, 0f, max(0f, WH - h))
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

        updateBird(dt)

        val wx = fx + camX
        val wy = fy + camY
        var saved = 0

        for (a in ants) {
            if (a.lock > 0f) a.lock -= dt
            a.ph += dt * 5.5f

            when (a.st) {
                FOLLOW -> {
                    if (fid >= 0) {
                        a.ang += a.spin * dt
                        val tx = wx + cos(a.ang) * a.dist
                        val ty = wy + sin(a.ang) * a.dist
                        step(a, tx, ty, ANT_SPEED * dt)
                    } else {
                        a.x += sin(a.ph * 0.4f) * 10f * dt
                        a.y += cos(a.ph * 0.33f) * 10f * dt
                    }
                }
                LOST -> {
                    if (a.lag > 0f) {
                        a.lag -= dt
                    } else {
                        val d = hypot(a.tx - a.x, a.ty - a.y)
                        if (d > 8f) {
                            step(a, a.tx, a.ty, LOST_SPEED * dt)
                        } else {
                            a.x += cos(a.ph * 0.27f) * 26f * dt
                            a.y += sin(a.ph * 0.21f) * 26f * dt
                        }
                    }
                    if (fid >= 0 && a.lock <= 0f && hypot(a.x - wx, a.y - wy) < shadeR * 0.85f) {
                        a.st = FOLLOW
                    }
                }
                DOWN -> {
                    val tx = homeX + cos(a.ph * 0.13f) * homeR * 0.5f
                    val ty = homeY + sin(a.ph * 0.11f) * homeR * 0.5f
                    step(a, tx, ty, 55f * dt)
                    a.hp = min(0.55f, a.hp + 0.10f * dt)
                    if (fid >= 0 && hypot(a.x - wx, a.y - wy) < shadeR * 0.85f) {
                        a.st = FOLLOW
                        a.hp = max(a.hp, 0.5f)
                    }
                }
                SAVED -> {
                    val tx = treeX + cos(a.ang) * treeR * 0.5f
                    val ty = treeY + sin(a.ang) * treeR * 0.5f
                    step(a, tx, ty, 80f * dt)
                    a.hp = min(1f, a.hp + 0.2f * dt)
                    saved++
                }
            }

            a.x = clamp(a.x, 30f, WW - 30f)
            a.y = clamp(a.y, 30f, WH - 30f)

            if (a.st == FOLLOW || a.st == LOST) {
                if (hypot(a.x - treeX, a.y - treeY) < treeR * 0.75f) {
                    a.st = SAVED
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
                    sendHome(a)
                    msg = "1ぴき もどされた"
                    msgT = 1.6f
                }
            }
        }

        if (saved >= TOTAL) {
            scene = CLEAR
            msg = ""
            msgT = 0f
        }
    }

    private fun sendHome(a: Ant) {
        a.st = DOWN
        a.hp = 0.12f
        a.x = homeX + (Random.nextFloat() - 0.5f) * homeR
        a.y = homeY + (Random.nextFloat() - 0.5f) * homeR
        a.tx = a.x
        a.ty = a.y
        a.lag = 0f
    }

    private fun step(a: Ant, tx: Float, ty: Float, s: Float) {
        val dx = tx - a.x
        val dy = ty - a.y
        val d = hypot(dx, dy)
        if (d < 1.2f) return
        val m = min(s, d)
        a.x += dx / d * m
        a.y += dy / d * m
    }

    private fun flockCenter(onlyFollow: Boolean): Boolean {
        var n = 0
        var sx = 0f
        var sy = 0f
        for (a in ants) {
            val ok = if (onlyFollow) a.st == FOLLOW else (a.st == FOLLOW || a.st == LOST)
            if (ok) {
                sx += a.x
                sy += a.y
                n++
            }
        }
        if (n == 0) return false
        ctrX = sx / n
        ctrY = sy / n
        return true
    }

    private fun updateBird(dt: Float) {
        when (bSt) {
            B_NONE -> {
                bT -= dt
                if (bT <= 0f) {
                    if (flockCenter(false)) {
                        tgx = ctrX
                        tgy = ctrY
                        bSt = B_CIRCLE
                        bT = 4.2f
                        alt = 1f
                        orbit = Random.nextFloat() * 6.2832f
                        bx = tgx + cos(orbit) * shadeR * 2.6f
                        by = tgy + sin(orbit) * shadeR * 2.6f
                        msg = "とりが とんでいる！"
                        msgT = 2.6f
                    } else {
                        bT = 5f
                    }
                }
            }
            B_CIRCLE -> {
                bT -= dt
                if (flockCenter(true)) {
                    tgx += (ctrX - tgx) * min(1f, dt * 1.6f)
                    tgy += (ctrY - tgy) * min(1f, dt * 1.6f)
                }
                orbit += dt * 1.5f
                val r = shadeR * (1.6f + 1.1f * clamp(bT / 4.2f, 0f, 1f))
                bx += (tgx + cos(orbit) * r - bx) * min(1f, dt * 3.2f)
                by += (tgy + sin(orbit) * r - by) * min(1f, dt * 3.2f)
                alt = 1f
                if (bT <= 0f) {
                    bSt = B_DIVE
                    bT = 0.7f
                }
            }
            B_DIVE -> {
                bT -= dt
                val k = clamp(bT / 0.7f, 0f, 1f)
                alt = k
                bx += (tgx - bx) * min(1f, dt * 7f)
                by += (tgy - by) * min(1f, dt * 7f)
                if (bT <= 0f) {
                    bx = tgx
                    by = tgy
                    alt = 0f
                    shake = 1f
                    strike()
                    bSt = B_LEAVE
                    bT = 1.3f
                }
            }
            B_LEAVE -> {
                bT -= dt
                alt = min(1.6f, alt + dt * 1.5f)
                by -= 240f * dt
                if (bT <= 0f) {
                    bSt = B_NONE
                    bT = 13f + Random.nextFloat() * 9f
                }
            }
        }
    }

    private fun strike() {
        var hit = 0
        for (a in ants) {
            if (a.st != FOLLOW && a.st != LOST) continue
            val d = hypot(a.x - tgx, a.y - tgy)
            if (d < HIT_R) {
                val k = 1f - d / HIT_R
                a.hp -= 0.30f + 0.80f * k
                hit++
                if (a.hp > 0f) scatter(a, tgx, tgy, shadeR * 1.8f)
            }
        }
        for (a in ants) {
            if ((a.st == FOLLOW || a.st == LOST) && a.hp <= 0f) sendHome(a)
        }
        msg = if (hit == 0) "うまく にげた！" else "つつかれた！（" + hit + "ぴき）"
        msgT = 2.2f
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

        c.save()
        if (shake > 0f) {
            c.translate((Random.nextFloat() - 0.5f) * 28f * shake, (Random.nextFloat() - 0.5f) * 28f * shake)
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
        c.drawRect(-40f, -40f, w + 40f, h + 40f, p)

        val step = 130f
        p.color = Color.rgb(108, 156, 78)
        var gxw = Math.floor((camX / step).toDouble()).toFloat() * step
        while (gxw < camX + w + step) {
            var gyw = Math.floor((camY / step).toDouble()).toFloat() * step
            while (gyw < camY + h + step) {
                val k = (((gxw / step).toInt() * 37 + (gyw / step).toInt() * 19) % 7 + 7) % 7
                c.drawCircle(gxw - camX + k * 8f, gyw - camY + k * 7f, 10f + k * 1.6f, p)
                gyw += step
            }
            gxw += step
        }

        p.color = Color.rgb(88, 130, 64)
        c.drawRect(-camX - 60f, -camY - 60f, WW - camX + 60f, -camY, p)
        c.drawRect(-camX - 60f, WH - camY, WW - camX + 60f, WH - camY + 60f, p)
        c.drawRect(-camX - 60f, -camY, -camX, WH - camY, p)
        c.drawRect(WW - camX, -camY, WW - camX + 60f, WH - camY, p)

        drawShade(c, homeX - camX, homeY - camY, homeR, 78)
        drawShade(c, treeX - camX, treeY - camY, treeR, 92)
        if (fid >= 0 && scene == PLAY) drawShade(c, fx, fy, shadeR, 96)

        drawTree(c, homeX - camX, homeY - camY, homeR, false)
        drawTree(c, treeX - camX, treeY - camY, treeR, true)

        for (a in ants) if (a.st != FOLLOW) drawAnt(c, a)
        for (a in ants) if (a.st == FOLLOW) drawAnt(c, a)

        drawSunLayer(c)
        drawRain(c)
        drawBird(c)
    }

    private fun drawShade(c: Canvas, sx: Float, sy: Float, r: Float, a: Int) {
        p.xfermode = null
        p.style = Paint.Style.FILL
        p.color = Color.argb(a, 22, 46, 26)
        c.drawCircle(sx, sy, r, p)
    }

    private fun drawTree(c: Canvas, sx: Float, sy: Float, r: Float, big: Boolean) {
        if (sx < -r * 2f || sx > w + r * 2f || sy < -r * 2f || sy > h + r * 2f) return
        p.style = Paint.Style.FILL
        p.color = Color.rgb(104, 74, 48)
        val tw = r * 0.15f
        c.drawRect(sx - tw, sy - r * 0.1f, sx + tw, sy + r * 0.6f, p)
        p.color = if (big) Color.rgb(48, 116, 58) else Color.rgb(62, 130, 72)
        c.drawCircle(sx, sy - r * 0.30f, r * 0.60f, p)
        c.drawCircle(sx - r * 0.45f, sy - r * 0.02f, r * 0.45f, p)
        c.drawCircle(sx + r * 0.45f, sy - r * 0.02f, r * 0.45f, p)
        c.drawCircle(sx, sy - r * 0.62f, r * 0.40f, p)
        p.color = if (big) Color.rgb(70, 148, 80) else Color.rgb(82, 162, 92)
        c.drawCircle(sx - r * 0.18f, sy - r * 0.44f, r * 0.29f, p)
        c.drawCircle(sx + r * 0.26f, sy - r * 0.20f, r * 0.23f, p)
        if (big) {
            p.color = Color.argb(210, 255, 255, 255)
            p.textSize = r * 0.17f
            p.textAlign = Paint.Align.CENTER
            c.drawText("ゴール", sx, sy + r * 0.90f, p)
            p.textAlign = Paint.Align.LEFT
        }
    }

    private fun drawAnt(c: Canvas, a: Ant) {
        val sx = a.x - camX
        val sy = a.y - camY
        val r = antR * (0.5f + 0.5f * clamp(a.hp, 0f, 1f))
        if (sx < -r * 6f || sx > w + r * 6f || sy < -r * 8f || sy > h + r * 6f) return

        val bob = sin(a.ph) * r * 0.10f
        val hpk = clamp(a.hp, 0f, 1f)

        p.style = Paint.Style.FILL
        p.color = Color.argb(70, 0, 0, 0)
        c.drawOval(sx - r * 0.72f, sy - r * 0.16f, sx + r * 0.72f, sy + r * 0.30f, p)

        val body = if (hpk > 0.4f) Color.rgb(56, 104, 196) else Color.rgb(132, 158, 200)
        val dark = if (hpk > 0.4f) Color.rgb(38, 76, 156) else Color.rgb(108, 134, 178)

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
        p.color = Color.rgb(252, 252, 252)
        c.drawOval(sx - r * 0.40f, ey - r * 0.30f, sx - r * 0.02f, ey + r * 0.30f, p)
        c.drawOval(sx + r * 0.02f, ey - r * 0.30f, sx + r * 0.40f, ey + r * 0.30f, p)
        p.color = Color.rgb(20, 22, 28)
        c.drawCircle(sx - r * 0.20f, ey + r * 0.02f, r * 0.13f, p)
        c.drawCircle(sx + r * 0.20f, ey + r * 0.02f, r * 0.13f, p)
        p.color = Color.WHITE
        c.drawCircle(sx - r * 0.24f, ey - r * 0.05f, r * 0.045f, p)
        c.drawCircle(sx + r * 0.16f, ey - r * 0.05f, r * 0.045f, p)

        // くち
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.09f
        p.color = Color.rgb(24, 30, 44)
        rect.set(sx - r * 0.20f, ey + r * 0.30f, sx + r * 0.20f, ey + r * 0.74f)
        if (hpk > 0.4f) c.drawArc(rect, 20f, 140f, false, p)
        else c.drawArc(rect, 200f, 140f, false, p)
        p.style = Paint.Style.FILL

        // くき と は
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.13f
        p.color = Color.rgb(92, 168, 72)
        val fy2 = ht - r * 1.05f * (0.45f + 0.55f * hpk)
        c.drawLine(sx, ht, sx, fy2, p)
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
        val pr = r * 0.42f * (0.4f + 0.6f * hpk)
        p.color = if (hpk > 0.4f) Color.rgb(250, 206, 44) else Color.rgb(206, 190, 130)
        for (i in 0 until 5) {
            val t = i / 5f * 6.2832f + a.tone * 1.2f
            c.drawCircle(sx + cos(t) * pr * 0.72f, fy2 + sin(t) * pr * 0.72f, pr * 0.62f, p)
        }
        p.color = if (hpk > 0.4f) Color.rgb(238, 160, 32) else Color.rgb(190, 170, 120)
        c.drawCircle(sx, fy2, pr * 0.42f, p)

        if (a.st == LOST && a.lock > 0f) {
            p.color = Color.argb(200, 255, 240, 120)
            p.textSize = r * 1.1f
            p.textAlign = Paint.Align.CENTER
            c.drawText("!", sx, ht - r * 1.5f, p)
            p.textAlign = Paint.Align.LEFT
        }
    }

    private fun drawSunLayer(c: Canvas) {
        val k = 1f - rainAmt
        if (k <= 0.02f) return
        val sc = c.saveLayer(-60f, -60f, w + 60f, h + 60f, null)
        p.xfermode = null
        p.style = Paint.Style.FILL
        p.color = Color.argb((104 * k).toInt(), 255, 214, 96)
        c.drawRect(-60f, -60f, w + 60f, h + 60f, p)
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
        c.drawRect(-60f, -60f, w + 60f, h + 60f, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        p.color = Color.argb((170 * rainAmt).toInt(), 210, 230, 255)
        val n = (rnx.size * rainAmt).toInt()
        for (i in 0 until n) {
            val yy = ((rny[i] + time * 1.25f) % 1f) * (h + 80f) - 40f
            val xx = ((rnx[i] + yy / (h + 80f) * 0.16f) % 1f) * w
            c.drawLine(xx, yy, xx - 11f, yy + 36f, p)
        }
        p.style = Paint.Style.FILL

        if (uid >= 0 && gauge > 0.01f && fid >= 0 && scene == PLAY) {
            p.style = Paint.Style.FILL
            p.color = Color.argb(90, 226, 70, 70)
            c.drawCircle(fx, fy, umbR, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 7f
            p.color = Color.rgb(226, 70, 70)
            c.drawCircle(fx, fy, umbR, p)
            p.strokeWidth = 3.5f
            p.color = Color.argb(150, 255, 255, 255)
            for (i in 0 until 8) {
                val t = i / 8f * 6.2832f
                c.drawLine(fx, fy, fx + cos(t) * umbR, fy + sin(t) * umbR, p)
            }
            p.style = Paint.Style.FILL
        }
    }

    private fun drawBird(c: Canvas) {
        if (bSt == B_NONE) return
        p.xfermode = null
        val gx = bx - camX
        val gy = by - camY
        val lift = h * 0.24f * alt
        val span = min(w, h) * 0.16f * (0.85f + alt * 0.45f)

        // じめんの かげ
        p.style = Paint.Style.FILL
        p.color = Color.argb((150 * (1f - alt * 0.55f)).toInt(), 12, 20, 14)
        c.drawOval(
            gx - span * (0.85f - alt * 0.2f), gy - span * (0.34f - alt * 0.08f),
            gx + span * (0.85f - alt * 0.2f), gy + span * (0.34f - alt * 0.08f), p
        )

        if (bSt == B_CIRCLE) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 6f
            p.color = Color.argb(150, 255, 90, 70)
            c.drawCircle(tgx - camX, tgy - camY, HIT_R, p)
            p.style = Paint.Style.FILL
        } else if (bSt == B_LEAVE && bT > 0.9f) {
            val k = (bT - 0.9f) / 0.4f
            p.style = Paint.Style.STROKE
            p.strokeWidth = 16f * k
            p.color = Color.argb((220 * k).toInt(), 255, 240, 200)
            c.drawCircle(tgx - camX, tgy - camY, HIT_R * (1f + (1f - k) * 0.9f), p)
            p.style = Paint.Style.FILL
        }

        val cx = gx
        val cy = gy - lift
        val f = sin(time * 9f) * 0.5f
        p.style = Paint.Style.FILL
        p.color = Color.rgb(28, 26, 34)

        path.reset()
        path.moveTo(cx - span * 0.10f, cy)
        path.quadTo(cx - span * 0.55f, cy - span * 0.40f - f * span * 0.5f, cx - span, cy - f * span * 0.75f)
        path.quadTo(cx - span * 0.52f, cy + span * 0.14f - f * span * 0.42f, cx - span * 0.10f, cy + span * 0.18f)
        path.close()
        c.drawPath(path, p)

        path.reset()
        path.moveTo(cx + span * 0.10f, cy)
        path.quadTo(cx + span * 0.55f, cy - span * 0.40f - f * span * 0.5f, cx + span, cy - f * span * 0.75f)
        path.quadTo(cx + span * 0.52f, cy + span * 0.14f - f * span * 0.42f, cx + span * 0.10f, cy + span * 0.18f)
        path.close()
        c.drawPath(path, p)

        c.drawOval(cx - span * 0.17f, cy - span * 0.30f, cx + span * 0.17f, cy + span * 0.40f, p)
        c.drawCircle(cx, cy - span * 0.34f, span * 0.14f, p)
        path.reset()
        path.moveTo(cx, cy - span * 0.46f)
        path.lineTo(cx + span * 0.05f, cy - span * 0.58f)
        path.lineTo(cx - span * 0.05f, cy - span * 0.58f)
        path.close()
        c.drawPath(path, p)
        path.reset()
        path.moveTo(cx - span * 0.14f, cy + span * 0.30f)
        path.lineTo(cx, cy + span * 0.66f)
        path.lineTo(cx + span * 0.14f, cy + span * 0.30f)
        path.close()
        c.drawPath(path, p)

        p.color = Color.rgb(240, 190, 60)
        c.drawCircle(cx - span * 0.05f, cy - span * 0.37f, span * 0.035f, p)
        c.drawCircle(cx + span * 0.05f, cy - span * 0.37f, span * 0.035f, p)
    }

    private fun drawHud(c: Canvas) {
        p.xfermode = null
        p.style = Paint.Style.FILL
        val bh = h * 0.055f
        p.color = Color.argb(150, 0, 0, 0)
        c.drawRect(0f, 0f, w, bh, p)

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
        p.textSize = h * 0.023f
        val ty = bh * 0.66f
        p.textAlign = Paint.Align.LEFT
        c.drawText("つれてる " + follow + "  はぐれ " + lost, w * 0.03f, ty, p)
        p.textAlign = Paint.Align.RIGHT
        c.drawText("ゴール " + saved + "/" + TOTAL + "  すあな " + down, w * 0.97f, ty, p)

        p.textAlign = Paint.Align.CENTER
        p.color = if (weather == RAIN) Color.rgb(150, 200, 255) else Color.rgb(255, 226, 120)
        c.drawText(if (weather == RAIN) "あめ" else "はれ", w * 0.5f, ty, p)

        if (msgT > 0f && scene == PLAY) {
            p.textSize = h * 0.030f
            val tw = p.measureText(msg)
            p.color = Color.argb(165, 0, 0, 0)
            rect.set(w * 0.5f - tw * 0.62f, bh + h * 0.014f, w * 0.5f + tw * 0.62f, bh + h * 0.062f)
            c.drawRoundRect(rect, 16f, 16f, p)
            p.color = Color.rgb(255, 240, 160)
            c.drawText(msg, w * 0.5f, bh + h * 0.049f, p)
        }
        p.textAlign = Paint.Align.LEFT

        if (scene == PLAY) {
            val tsx = treeX - camX
            val tsy = treeY - camY
            if (tsx < 0f || tsx > w || tsy < 0f || tsy > h) {
                val dx = tsx - w * 0.5f
                val dy = tsy - h * 0.5f
                val d = max(1f, hypot(dx, dy))
                val rr = min(w, h) * 0.33f
                val ax = w * 0.5f + dx / d * rr
                val ay = h * 0.5f + dy / d * rr
                val ang = Math.atan2(dy.toDouble(), dx.toDouble()).toFloat()
                p.style = Paint.Style.FILL
                p.color = Color.argb(200, 255, 240, 140)
                path.reset()
                path.moveTo(ax + cos(ang) * 34f, ay + sin(ang) * 34f)
                path.lineTo(ax + cos(ang + 2.5f) * 26f, ay + sin(ang + 2.5f) * 26f)
                path.lineTo(ax + cos(ang - 2.5f) * 26f, ay + sin(ang - 2.5f) * 26f)
                path.close()
                c.drawPath(path, p)
                p.textAlign = Paint.Align.CENTER
                p.textSize = h * 0.020f
                c.drawText("ゴール", ax, ay + 58f, p)
                p.textAlign = Paint.Align.LEFT
            }
        }
    }

    private fun drawPad(c: Canvas) {
        p.xfermode = null
        p.style = Paint.Style.FILL

        // 十字キー（左下）
        val a1 = dr * 0.36f
        p.color = Color.argb(210, 40, 38, 42)
        rect.set(dcx - a1, dcy - dr, dcx + a1, dcy + dr)
        c.drawRoundRect(rect, 14f, 14f, p)
        rect.set(dcx - dr, dcy - a1, dcx + dr, dcy + a1)
        c.drawRoundRect(rect, 14f, 14f, p)
        p.color = Color.argb(210, 76, 74, 78)
        c.drawCircle(dcx, dcy, a1 * 0.52f, p)
        if (ddir != 0) {
            p.color = Color.argb(170, 250, 226, 120)
            when (ddir) {
                1 -> c.drawCircle(dcx, dcy - dr * 0.62f, a1 * 0.5f, p)
                2 -> c.drawCircle(dcx, dcy + dr * 0.62f, a1 * 0.5f, p)
                3 -> c.drawCircle(dcx - dr * 0.62f, dcy, a1 * 0.5f, p)
                4 -> c.drawCircle(dcx + dr * 0.62f, dcy, a1 * 0.5f, p)
            }
        }
        p.color = Color.argb(150, 255, 255, 255)
        for (i in 0 until 4) {
            val t = i / 4f * 6.2832f
            path.reset()
            val px = dcx + cos(t) * dr * 0.72f
            val py = dcy + sin(t) * dr * 0.72f
            path.moveTo(px + cos(t) * a1 * 0.34f, py + sin(t) * a1 * 0.34f)
            path.lineTo(px + cos(t + 2.4f) * a1 * 0.34f, py + sin(t + 2.4f) * a1 * 0.34f)
            path.lineTo(px + cos(t - 2.4f) * a1 * 0.34f, py + sin(t - 2.4f) * a1 * 0.34f)
            path.close()
            c.drawPath(path, p)
        }

        // 傘ボタン（十字キーの上）
        p.color = Color.argb(90, 0, 0, 0)
        c.drawCircle(b2x, b2y + 6f, br, p)
        p.color = if (uid >= 0) Color.argb(235, 120, 30, 32) else Color.argb(225, 200, 46, 50)
        c.drawCircle(b2x, b2y, br, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = br * 0.16f
        p.color = if (gauge < 0.25f) Color.rgb(255, 120, 90) else Color.rgb(120, 210, 245)
        rect.set(b2x - br * 1.22f, b2y - br * 1.22f, b2x + br * 1.22f, b2y + br * 1.22f)
        c.drawArc(rect, -90f, 360f * clamp(gauge, 0f, 1f), false, p)
        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        p.textAlign = Paint.Align.CENTER
        p.textSize = br * 0.92f
        c.drawText("傘", b2x, b2y + br * 0.33f, p)

        // 解散＝笛ボタン（傘の上）
        p.color = Color.argb(90, 0, 0, 0)
        c.drawCircle(b1x, b1y + 6f, br, p)
        p.color = Color.argb(225, 214, 148, 40)
        c.drawCircle(b1x, b1y, br, p)
        p.color = Color.WHITE
        p.textSize = br * 0.92f
        c.drawText("笛", b1x, b1y + br * 0.33f, p)
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawOverlay(c: Canvas) {
        p.xfermode = null
        p.style = Paint.Style.FILL
        p.color = Color.argb(195, 8, 14, 10)
        c.drawRect(0f, 0f, w, h, p)
        p.textAlign = Paint.Align.CENTER

        if (scene == TITLE) {
            p.color = Color.rgb(250, 214, 90)
            p.textSize = h * 0.085f
            c.drawText("アンビー", w * 0.5f, h * 0.20f, p)
            p.color = Color.rgb(230, 230, 220)
            p.textSize = h * 0.026f
            c.drawText("ゆびの かげで むれを まもる", w * 0.5f, h * 0.26f, p)
            p.textSize = h * 0.024f
            p.color = Color.rgb(205, 214, 205)
            c.drawText("ゆびで がめんを おすと むれが あつまる", w * 0.5f, h * 0.35f, p)
            c.drawText("ゆびを はなすと かげの そとの こは はぐれる", w * 0.5f, h * 0.39f, p)
            c.drawText("あめは「傘」 とりが とんだら「笛」で ちらす", w * 0.5f, h * 0.43f, p)
            c.drawText("じゅうじキーで がめんを うごかして", w * 0.5f, h * 0.47f, p)
            c.drawText("おおきな きの したへ ぜんいん はこぼう", w * 0.5f, h * 0.51f, p)
            p.color = Color.rgb(250, 214, 90)
            p.textSize = h * 0.034f
            c.drawText("がめんを タップして スタート", w * 0.5f, h * 0.62f, p)
        } else if (scene == CLEAR) {
            p.color = Color.rgb(250, 214, 90)
            p.textSize = h * 0.070f
            c.drawText("ぜんいん ゴール！", w * 0.5f, h * 0.34f, p)
            p.color = Color.rgb(235, 235, 225)
            p.textSize = h * 0.034f
            c.drawText("タイム " + time.toInt() + " びょう", w * 0.5f, h * 0.43f, p)
            p.textSize = h * 0.030f
            c.drawText("がめんを タップして もういちど", w * 0.5f, h * 0.56f, p)
        }
        p.textAlign = Paint.Align.LEFT
    }
}
