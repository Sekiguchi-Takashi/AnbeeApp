package com.appathy.anbee

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
        var ox = 0f
        var oy = 0f
        var mode = 0
        var saved = false
        var idle = 0f
        var reach = false
        var wi = 0
        var w1x = 0f
        var w1y = 0f
        var w2x = 0f
        var w2y = 0f
        var ccx = 0
        var ccy = 0
        var ph = 0f
        var tone = 0f
        var face = 1f
    }

    companion object {
        private const val STAY = 0
        private const val COME = 1
        private const val BACK = 2

        private const val TITLE = 0
        private const val PLAY = 1
        private const val CLEAR = 2

        private const val COLS = 21
        private const val ROWS = 31
        private const val CS = 150f
        private const val TOTAL = 10

        private const val SX = 1
        private const val SY = ROWS - 2
        private const val GX = COLS - 2
        private const val GY = 1

        private const val WW = COLS * CS
        private const val WH = ROWS * CS

        private const val COME_SPEED = 400f
        private const val BACK_SPEED = 96f
        private const val CAM_SPEED = 900f
        private const val IDLE_LIMIT = 10f
        private const val REPLAN = 0.12f
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    private var w = 0f
    private var h = 0f
    private var last = 0L
    private var running = true
    private var inited = false

    private var scene = TITLE
    private var camX = 0f
    private var camY = 0f
    private var camTX = 0f
    private var camTY = 0f
    private var camAuto = false
    private var time = 0f

    private val grid = ByteArray(COLS * ROWS)
    private val dist = IntArray(COLS * ROWS)
    private val queue = IntArray(COLS * ROWS)

    private val ants = ArrayList<Ant>()

    private var fid = -1
    private var fx = 0f
    private var fy = 0f
    private var did = -1
    private var ddir = 0

    private var planT = 0f
    private var comeN = 0
    private var farN = 0

    private var searchIdx = -1
    private var markT = 0f
    private var markA: Ant? = null

    private var msg = ""
    private var msgT = 0f

    private var antR = 30f
    private var callR = 150f

    private var dcx = 0f
    private var dcy = 0f
    private var dr = 0f
    private var sbx = 0f
    private var sby = 0f
    private var sbr = 0f

    init {
        isFocusable = true
        for (i in 0 until TOTAL) ants.add(Ant())
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
        antR = min(w, h) * 0.028f
        callR = CS * 0.95f
        layoutPad()
        if (!inited) {
            inited = true
            resetGame()
        } else {
            camX = clamp(camX, 0f, max(0f, WW - w))
            camY = clamp(camY, 0f, max(0f, WH - h))
        }
    }

    private fun layoutPad() {
        dr = min(w * 0.145f, h * 0.085f)
        dcx = w * 0.055f + dr
        dcy = h - dr - h * 0.045f
        sbr = dr * 0.62f
        sbx = dcx - dr * 0.10f
        sby = dcy - dr - sbr - h * 0.020f
    }

    private fun clamp(v: Float, lo: Float, hi: Float): Float {
        if (v < lo) return lo
        if (v > hi) return hi
        return v
    }

    private fun clampI(v: Int, lo: Int, hi: Int): Int {
        if (v < lo) return lo
        if (v > hi) return hi
        return v
    }

    // ---------------- maze ----------------

    private fun idx(cx: Int, cy: Int): Int {
        return cy * COLS + cx
    }

    private fun isWall(cx: Int, cy: Int): Boolean {
        if (cx < 0 || cy < 0 || cx >= COLS || cy >= ROWS) return true
        return grid[idx(cx, cy)].toInt() == 1
    }

    private fun cellX(x: Float): Int {
        return clampI((x / CS).toInt(), 0, COLS - 1)
    }

    private fun cellY(y: Float): Int {
        return clampI((y / CS).toInt(), 0, ROWS - 1)
    }

    private fun cxToW(cx: Int): Float {
        return (cx + 0.5f) * CS
    }

    private fun cyToW(cy: Int): Float {
        return (cy + 0.5f) * CS
    }

    private fun buildMaze() {
        for (i in grid.indices) grid[i] = 1
        val stack = IntArray(COLS * ROWS)
        var sp = 0
        grid[idx(SX, SY)] = 0
        stack[sp] = idx(SX, SY)
        sp++
        val dxs = intArrayOf(0, 0, -2, 2)
        val dys = intArrayOf(-2, 2, 0, 0)
        val ord = IntArray(4)
        while (sp > 0) {
            val cur = stack[sp - 1]
            val cx = cur % COLS
            val cy = cur / COLS
            for (i in 0 until 4) ord[i] = i
            for (i in 3 downTo 1) {
                val j = Random.nextInt(i + 1)
                val t = ord[i]
                ord[i] = ord[j]
                ord[j] = t
            }
            var moved = false
            for (k in 0 until 4) {
                val d = ord[k]
                val nx = cx + dxs[d]
                val ny = cy + dys[d]
                if (nx < 1 || ny < 1 || nx > COLS - 2 || ny > ROWS - 2) continue
                if (grid[idx(nx, ny)].toInt() == 0) continue
                grid[idx(cx + dxs[d] / 2, cy + dys[d] / 2)] = 0
                grid[idx(nx, ny)] = 0
                stack[sp] = idx(nx, ny)
                sp++
                moved = true
                break
            }
            if (!moved) sp--
        }
        // ループを増やして「2直線」で届く経路を作りやすくする
        for (cy in 1 until ROWS - 1) {
            for (cx in 1 until COLS - 1) {
                if (grid[idx(cx, cy)].toInt() == 0) continue
                val hOpen = !isWall(cx - 1, cy) && !isWall(cx + 1, cy)
                val vOpen = !isWall(cx, cy - 1) && !isWall(cx, cy + 1)
                if ((hOpen || vOpen) && Random.nextFloat() < 0.22f) grid[idx(cx, cy)] = 0
            }
        }
        grid[idx(SX, SY)] = 0
        grid[idx(GX, GY)] = 0
        // スタートとゴールの周りを少し広げる
        openAround(SX, SY)
        openAround(GX, GY)
        buildDist()
    }

    private fun openAround(cx: Int, cy: Int) {
        for (dy in -1..1) {
            for (dx in -1..1) {
                val nx = cx + dx
                val ny = cy + dy
                if (nx < 1 || ny < 1 || nx > COLS - 2 || ny > ROWS - 2) continue
                grid[idx(nx, ny)] = 0
            }
        }
    }

    private fun buildDist() {
        for (i in dist.indices) dist[i] = -1
        var head = 0
        var tail = 0
        dist[idx(SX, SY)] = 0
        queue[tail] = idx(SX, SY)
        tail++
        val dxs = intArrayOf(0, 0, -1, 1)
        val dys = intArrayOf(-1, 1, 0, 0)
        while (head < tail) {
            val cur = queue[head]
            head++
            val cx = cur % COLS
            val cy = cur / COLS
            for (d in 0 until 4) {
                val nx = cx + dxs[d]
                val ny = cy + dys[d]
                if (isWall(nx, ny)) continue
                if (dist[idx(nx, ny)] >= 0) continue
                dist[idx(nx, ny)] = dist[cur] + 1
                queue[tail] = idx(nx, ny)
                tail++
            }
        }
    }

    private fun rowClear(cy: Int, x0: Int, x1: Int): Boolean {
        val a = min(x0, x1)
        val b = max(x0, x1)
        for (cx in a..b) if (isWall(cx, cy)) return false
        return true
    }

    private fun colClear(cx: Int, y0: Int, y1: Int): Boolean {
        val a = min(y0, y1)
        val b = max(y0, y1)
        for (cy in a..b) if (isWall(cx, cy)) return false
        return true
    }

    // ---------------- game ----------------

    private fun resetGame() {
        buildMaze()
        time = 0f
        msg = ""
        msgT = 0f
        fid = -1
        did = -1
        ddir = 0
        planT = 0f
        searchIdx = -1
        markT = 0f
        markA = null
        camAuto = false

        val spots = ArrayList<Int>()
        for (i in dist.indices) if (dist[i] > 6) spots.add(i)
        spots.sortBy { dist[it] }

        for (i in 0 until TOTAL) {
            val a = ants[i]
            val t = i.toFloat() / TOTAL * 6.2832f
            a.ox = cos(t) * CS * 0.26f
            a.oy = sin(t) * CS * 0.26f
            a.mode = STAY
            a.saved = false
            a.idle = 0f
            a.reach = false
            a.ph = Random.nextFloat() * 6.2832f
            a.tone = Random.nextFloat()
            a.face = 1f
            var cell = idx(SX, SY)
            if (spots.size > 0) {
                val lo = (spots.size * (0.22f + 0.070f * i)).toInt()
                cell = spots[clampI(lo, 0, spots.size - 1)]
            }
            a.x = cxToW(cell % COLS) + a.ox
            a.y = cyToW(cell / COLS) + a.oy
        }

        camX = clamp(cxToW(SX) - w * 0.5f, 0f, max(0f, WW - w))
        camY = clamp(cyToW(SY) - h * 0.5f, 0f, max(0f, WH - h))
        camTX = camX
        camTY = camY
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
                        planT = 0f
                    } else if (id == did) {
                        ddir = dirOf(e.getX(i), e.getY(i))
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                onUp(e.getPointerId(e.actionIndex))
            }
            MotionEvent.ACTION_CANCEL -> {
                fid = -1
                did = -1
                ddir = 0
            }
        }
        return true
    }

    private fun onDown(id: Int, x: Float, y: Float) {
        if (abs(x - dcx) < dr * 1.25f && abs(y - dcy) < dr * 1.25f) {
            did = id
            ddir = dirOf(x, y)
            camAuto = false
            return
        }
        if (hypot(x - sbx, y - sby) < sbr * 1.35f) {
            if (scene == PLAY) searchNext()
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
            planT = 0f
        }
    }

    private fun onUp(id: Int) {
        if (id == fid) {
            fid = -1
            for (a in ants) if (a.mode == COME) a.mode = STAY
        }
        if (id == did) {
            did = -1
            ddir = 0
        }
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

    private fun searchNext() {
        val list = ArrayList<Ant>()
        for (a in ants) if (!a.saved) list.add(a)
        if (list.size == 0) return
        val gxw = cxToW(GX)
        val gyw = cyToW(GY)
        list.sortByDescending { hypot(it.x - gxw, it.y - gyw) }
        searchIdx = (searchIdx + 1) % list.size
        val a = list[searchIdx]
        camTX = clamp(a.x - w * 0.5f, 0f, max(0f, WW - w))
        camTY = clamp(a.y - h * 0.5f, 0f, max(0f, WH - h))
        camAuto = true
        markA = a
        markT = 3f
        msg = "とおい じゅん " + (searchIdx + 1) + " / " + list.size
        msgT = 2f
    }

    // ---------------- update ----------------

    private fun update(dt: Float) {
        if (msgT > 0f) msgT -= dt
        if (markT > 0f) markT -= dt
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
        } else if (camAuto) {
            val k = min(1f, dt * 6f)
            camX += (camTX - camX) * k
            camY += (camTY - camY) * k
            if (hypot(camTX - camX, camTY - camY) < 2f) camAuto = false
        }

        planT -= dt
        val replan = planT <= 0f
        if (replan) planT = REPLAN

        val wx = fx + camX
        val wy = fy + camY
        val tcx = cellX(wx)
        val tcy = cellY(wy)
        val fingerOk = fid >= 0 && !isWall(tcx, tcy)

        comeN = 0
        farN = 0

        for (a in ants) {
            a.ph += dt * 5.5f
            if (a.saved) continue

            if (replan) {
                if (fingerOk) plan(a, tcx, tcy) else a.reach = false
            }

            if (fingerOk && a.reach) {
                a.mode = COME
                a.idle = 0f
                comeN++
            } else {
                if (a.mode == COME) a.mode = STAY
                farN++
                a.idle += dt
                if (a.idle > IDLE_LIMIT) a.mode = BACK
            }

            when (a.mode) {
                COME -> movePath(a, dt)
                BACK -> moveBack(a, dt)
                else -> {
                    a.x += sin(a.ph * 0.31f) * 5f * dt
                    a.y += cos(a.ph * 0.27f) * 5f * dt
                }
            }

            if (cellX(a.x) == GX && cellY(a.y) == GY) {
                a.saved = true
                a.mode = STAY
                msg = "1ぴき ゴール！"
                msgT = 1.8f
            }
        }

        separate(dt)

        var saved = 0
        for (a in ants) if (a.saved) saved++
        if (saved >= TOTAL) {
            scene = CLEAR
            msg = ""
            msgT = 0f
        }
    }

    private fun plan(a: Ant, tcx: Int, tcy: Int) {
        val acx = cellX(a.x)
        val acy = cellY(a.y)
        a.reach = false
        if (isWall(acx, acy)) return

        var cnx = -1
        var cny = -1
        val hFirst = abs(tcx - acx) >= abs(tcy - acy)

        if (hFirst) {
            if (rowClear(acy, acx, tcx) && colClear(tcx, acy, tcy)) {
                cnx = tcx
                cny = acy
            } else if (colClear(acx, acy, tcy) && rowClear(tcy, acx, tcx)) {
                cnx = acx
                cny = tcy
            }
        } else {
            if (colClear(acx, acy, tcy) && rowClear(tcy, acx, tcx)) {
                cnx = acx
                cny = tcy
            } else if (rowClear(acy, acx, tcx) && colClear(tcx, acy, tcy)) {
                cnx = tcx
                cny = acy
            }
        }
        if (cnx < 0) return

        a.reach = true
        a.ccx = cnx
        a.ccy = cny
        a.w1x = cxToW(cnx) + a.ox
        a.w1y = cyToW(cny) + a.oy
        a.w2x = cxToW(tcx) + a.ox
        a.w2y = cyToW(tcy) + a.oy
        a.wi = if (hypot(a.w1x - a.x, a.w1y - a.y) < CS * 0.30f) 1 else 0
    }

    private fun movePath(a: Ant, dt: Float) {
        val tx: Float
        val ty: Float
        if (a.wi == 0) {
            tx = a.w1x
            ty = a.w1y
        } else {
            tx = a.w2x
            ty = a.w2y
        }
        val dx = tx - a.x
        val dy = ty - a.y
        val d = hypot(dx, dy)
        if (d < CS * 0.14f) {
            if (a.wi == 0) a.wi = 1
            return
        }
        val m = min(COME_SPEED * dt, d)
        a.x += dx / d * m
        a.y += dy / d * m
        if (abs(dx) > abs(dy)) a.face = if (dx > 0f) 1f else -1f
    }

    private fun moveBack(a: Ant, dt: Float) {
        val acx = cellX(a.x)
        val acy = cellY(a.y)
        val here = dist[idx(acx, acy)]
        if (here <= 0) return
        var bx = acx
        var by = acy
        var best = here
        val dxs = intArrayOf(0, 0, -1, 1)
        val dys = intArrayOf(-1, 1, 0, 0)
        for (d in 0 until 4) {
            val nx = acx + dxs[d]
            val ny = acy + dys[d]
            if (isWall(nx, ny)) continue
            val nd = dist[idx(nx, ny)]
            if (nd in 0 until best) {
                best = nd
                bx = nx
                by = ny
            }
        }
        val tx = cxToW(bx) + a.ox * 0.4f
        val ty = cyToW(by) + a.oy * 0.4f
        val dx = tx - a.x
        val dy = ty - a.y
        val d = hypot(dx, dy)
        if (d < 2f) return
        val m = min(BACK_SPEED * dt, d)
        a.x += dx / d * m
        a.y += dy / d * m
        if (abs(dx) > abs(dy)) a.face = if (dx > 0f) 1f else -1f
    }

    private fun isFree(x: Float, y: Float): Boolean {
        val m = antR * 0.5f
        if (isWall(cellX(x), cellY(y))) return false
        if (isWall(cellX(x - m), cellY(y))) return false
        if (isWall(cellX(x + m), cellY(y))) return false
        if (isWall(cellX(x), cellY(y - m))) return false
        if (isWall(cellX(x), cellY(y + m))) return false
        return true
    }

    private fun separate(dt: Float) {
        val minD = antR * 1.6f
        for (i in 0 until TOTAL) {
            val a = ants[i]
            if (a.saved) continue
            var px = 0f
            var py = 0f
            for (j in 0 until TOTAL) {
                if (i == j) continue
                val b = ants[j]
                val dx = a.x - b.x
                val dy = a.y - b.y
                val d = hypot(dx, dy)
                if (d > minD || d < 0.001f) continue
                val k = (minD - d) / minD
                px += dx / d * k
                py += dy / d * k
            }
            if (px == 0f && py == 0f) continue
            val s = 90f * dt
            val nx = a.x + px * s
            val ny = a.y + py * s
            if (isFree(nx, a.y)) a.x = nx
            if (isFree(a.x, ny)) a.y = ny
        }
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

        drawWorld(c)
        drawHud(c)
        drawPad(c)
        if (scene != PLAY) drawOverlay(c)

        if (running) postInvalidateOnAnimation()
    }

    private fun drawWorld(c: Canvas) {
        p.style = Paint.Style.FILL
        c.drawColor(Color.rgb(148, 190, 118))

        val x0 = clampI((camX / CS).toInt() - 1, 0, COLS - 1)
        val x1 = clampI(((camX + w) / CS).toInt() + 1, 0, COLS - 1)
        val y0 = clampI((camY / CS).toInt() - 1, 0, ROWS - 1)
        val y1 = clampI(((camY + h) / CS).toInt() + 1, 0, ROWS - 1)

        for (cy in y0..y1) {
            for (cx in x0..x1) {
                val sx = cx * CS - camX
                val sy = cy * CS - camY
                if (isWall(cx, cy)) {
                    p.color = Color.rgb(58, 96, 54)
                    rect.set(sx + 1f, sy + 1f, sx + CS - 1f, sy + CS - 1f)
                    c.drawRoundRect(rect, CS * 0.18f, CS * 0.18f, p)
                    p.color = Color.rgb(74, 122, 66)
                    val k = ((cx * 31 + cy * 17) % 5 + 5) % 5
                    c.drawCircle(sx + CS * 0.32f, sy + CS * 0.34f, CS * (0.15f + k * 0.012f), p)
                    c.drawCircle(sx + CS * 0.66f, sy + CS * 0.60f, CS * (0.13f + k * 0.014f), p)
                } else {
                    p.color = Color.rgb(162, 202, 130)
                    c.drawRect(sx, sy, sx + CS, sy + CS, p)
                    val k = ((cx * 13 + cy * 29) % 6 + 6) % 6
                    if (k < 2) {
                        p.color = Color.rgb(150, 192, 120)
                        c.drawCircle(sx + CS * (0.3f + k * 0.2f), sy + CS * 0.62f, CS * 0.07f, p)
                    }
                }
            }
        }

        drawHome(c, cxToW(SX) - camX, cyToW(SY) - camY)
        drawGoal(c, cxToW(GX) - camX, cyToW(GY) - camY)

        if (fid >= 0 && scene == PLAY) drawPaths(c)

        for (a in ants) drawAnt(c, a)

        if (fid >= 0 && scene == PLAY) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 5f
            p.color = Color.argb(190, 255, 244, 170)
            c.drawCircle(fx, fy, callR * (0.94f + 0.06f * sin(time * 4f)), p)
            p.strokeWidth = 3f
            p.color = Color.argb(120, 255, 244, 170)
            c.drawCircle(fx, fy, callR * 0.55f, p)
            p.style = Paint.Style.FILL
        }

        val ma = markA
        if (markT > 0f && ma != null && !ma.saved) {
            val sx = ma.x - camX
            val sy = ma.y - camY
            p.style = Paint.Style.STROKE
            p.strokeWidth = 6f
            p.color = Color.argb((220 * min(1f, markT)).toInt(), 255, 120, 90)
            c.drawCircle(sx, sy - antR * 1.1f, antR * (2.2f + 0.5f * sin(time * 7f)), p)
            p.style = Paint.Style.FILL
        }
    }

    private fun drawPaths(c: Canvas) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = 7f
        for (a in ants) {
            if (a.saved || !a.reach) continue
            p.color = Color.argb(110, 255, 250, 190)
            c.drawLine(a.x - camX, a.y - camY, a.w1x - camX, a.w1y - camY, p)
            c.drawLine(a.w1x - camX, a.w1y - camY, a.w2x - camX, a.w2y - camY, p)
        }
        p.style = Paint.Style.FILL
    }

    private fun drawHome(c: Canvas, sx: Float, sy: Float) {
        if (sx < -CS * 3f || sx > w + CS * 3f || sy < -CS * 3f || sy > h + CS * 3f) return
        p.style = Paint.Style.FILL
        p.color = Color.rgb(140, 108, 72)
        c.drawOval(sx - CS * 0.52f, sy - CS * 0.30f, sx + CS * 0.52f, sy + CS * 0.46f, p)
        p.color = Color.rgb(72, 52, 34)
        c.drawOval(sx - CS * 0.28f, sy - CS * 0.14f, sx + CS * 0.28f, sy + CS * 0.30f, p)
        p.color = Color.argb(190, 255, 255, 255)
        p.textAlign = Paint.Align.CENTER
        p.textSize = CS * 0.24f
        c.drawText("すあな", sx, sy + CS * 0.72f, p)
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawGoal(c: Canvas, sx: Float, sy: Float) {
        if (sx < -CS * 4f || sx > w + CS * 4f || sy < -CS * 4f || sy > h + CS * 4f) return
        val r = CS * 1.15f
        p.style = Paint.Style.FILL
        p.color = Color.argb(80, 20, 44, 24)
        c.drawCircle(sx, sy, r * 0.85f, p)
        p.color = Color.rgb(104, 74, 48)
        c.drawRect(sx - r * 0.13f, sy - r * 0.08f, sx + r * 0.13f, sy + r * 0.52f, p)
        p.color = Color.rgb(48, 116, 58)
        c.drawCircle(sx, sy - r * 0.26f, r * 0.52f, p)
        c.drawCircle(sx - r * 0.38f, sy, r * 0.38f, p)
        c.drawCircle(sx + r * 0.38f, sy, r * 0.38f, p)
        p.color = Color.rgb(70, 148, 80)
        c.drawCircle(sx - r * 0.15f, sy - r * 0.38f, r * 0.25f, p)
        c.drawCircle(sx + r * 0.22f, sy - r * 0.16f, r * 0.20f, p)
        p.color = Color.argb(220, 255, 255, 255)
        p.textAlign = Paint.Align.CENTER
        p.textSize = r * 0.24f
        c.drawText("ゴール", sx, sy + r * 0.82f, p)
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawAnt(c: Canvas, a: Ant) {
        val sx = a.x - camX
        val sy = a.y - camY
        val r = antR
        if (sx < -r * 6f || sx > w + r * 6f || sy < -r * 8f || sy > h + r * 6f) return

        val bob = sin(a.ph) * r * 0.10f
        val pale = a.mode == STAY && fid >= 0 && !a.reach

        p.style = Paint.Style.FILL
        p.color = Color.argb(70, 0, 0, 0)
        c.drawOval(sx - r * 0.72f, sy - r * 0.16f, sx + r * 0.72f, sy + r * 0.30f, p)

        val body = if (pale) Color.rgb(126, 150, 190) else Color.rgb(56, 104, 196)
        val dark = if (pale) Color.rgb(102, 126, 166) else Color.rgb(38, 76, 156)

        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.20f
        p.color = dark
        c.drawLine(sx - r * 0.26f, sy - r * 0.55f + bob, sx - r * 0.30f, sy, p)
        c.drawLine(sx + r * 0.26f, sy - r * 0.55f + bob, sx + r * 0.30f, sy, p)
        p.style = Paint.Style.FILL
        c.drawOval(sx - r * 0.46f, sy - r * 0.13f, sx - r * 0.12f, sy + r * 0.08f, p)
        c.drawOval(sx + r * 0.12f, sy - r * 0.13f, sx + r * 0.46f, sy + r * 0.08f, p)

        p.color = body
        c.drawOval(sx - r * 0.44f, sy - r * 1.28f + bob, sx + r * 0.44f, sy - r * 0.44f + bob, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.17f
        p.color = body
        c.drawLine(sx - r * 0.40f, sy - r * 0.98f + bob, sx - r * 0.14f, sy - r * 0.80f + bob, p)
        c.drawLine(sx + r * 0.40f, sy - r * 0.98f + bob, sx + r * 0.14f, sy - r * 0.80f + bob, p)
        p.style = Paint.Style.FILL

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

        val ey = sy - r * 1.86f + bob
        val ed = r * 0.06f * a.face
        p.color = Color.rgb(252, 252, 252)
        c.drawOval(sx - r * 0.40f, ey - r * 0.30f, sx - r * 0.02f, ey + r * 0.30f, p)
        c.drawOval(sx + r * 0.02f, ey - r * 0.30f, sx + r * 0.40f, ey + r * 0.30f, p)
        p.color = Color.rgb(20, 22, 28)
        c.drawCircle(sx - r * 0.20f + ed, ey + r * 0.02f, r * 0.13f, p)
        c.drawCircle(sx + r * 0.20f + ed, ey + r * 0.02f, r * 0.13f, p)
        p.color = Color.WHITE
        c.drawCircle(sx - r * 0.24f + ed, ey - r * 0.05f, r * 0.045f, p)
        c.drawCircle(sx + r * 0.16f + ed, ey - r * 0.05f, r * 0.045f, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.09f
        p.color = Color.rgb(24, 30, 44)
        rect.set(sx - r * 0.20f, ey + r * 0.30f, sx + r * 0.20f, ey + r * 0.74f)
        if (pale) c.drawArc(rect, 200f, 140f, false, p) else c.drawArc(rect, 20f, 140f, false, p)
        p.style = Paint.Style.FILL

        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.13f
        p.color = Color.rgb(92, 168, 72)
        val fy2 = ht - r * 1.05f
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

        val pr = r * 0.42f
        p.color = if (a.saved) Color.rgb(255, 236, 120) else if (pale) Color.rgb(206, 196, 150) else Color.rgb(250, 206, 44)
        for (i in 0 until 5) {
            val t = i / 5f * 6.2832f + a.tone * 1.2f
            c.drawCircle(sx + cos(t) * pr * 0.72f, fy2 + sin(t) * pr * 0.72f, pr * 0.62f, p)
        }
        p.color = if (pale) Color.rgb(186, 176, 136) else Color.rgb(238, 160, 32)
        c.drawCircle(sx, fy2, pr * 0.42f, p)

        if (a.mode == BACK) {
            p.color = Color.argb(210, 255, 170, 120)
            p.textAlign = Paint.Align.CENTER
            p.textSize = r * 0.95f
            c.drawText("＜", sx, ht - r * 1.55f, p)
            p.textAlign = Paint.Align.LEFT
        } else if (!a.saved && a.idle > IDLE_LIMIT * 0.6f) {
            p.color = Color.argb(190, 255, 230, 140)
            p.textAlign = Paint.Align.CENTER
            p.textSize = r * 0.85f
            c.drawText("?", sx, ht - r * 1.55f, p)
            p.textAlign = Paint.Align.LEFT
        }
    }

    private fun drawHud(c: Canvas) {
        p.style = Paint.Style.FILL
        val bh = h * 0.055f
        p.color = Color.argb(155, 0, 0, 0)
        c.drawRect(0f, 0f, w, bh, p)

        var saved = 0
        var back = 0
        for (a in ants) {
            if (a.saved) saved++
            else if (a.mode == BACK) back++
        }

        p.color = Color.WHITE
        p.textSize = h * 0.023f
        val ty = bh * 0.66f
        p.textAlign = Paint.Align.LEFT
        if (fid >= 0) {
            c.drawText("ついてきてる " + comeN + "  とどかない " + farN, w * 0.03f, ty, p)
        } else {
            c.drawText("ゆびで おして よぼう", w * 0.03f, ty, p)
        }
        p.textAlign = Paint.Align.RIGHT
        c.drawText("ゴール " + saved + "/" + TOTAL, w * 0.97f, ty, p)
        if (back > 0) {
            p.textAlign = Paint.Align.CENTER
            p.color = Color.rgb(255, 176, 120)
            c.drawText("すあなへ もどってる " + back, w * 0.5f, ty, p)
        }

        if (msgT > 0f && scene == PLAY) {
            p.textAlign = Paint.Align.CENTER
            p.textSize = h * 0.029f
            val tw = p.measureText(msg)
            p.color = Color.argb(170, 0, 0, 0)
            rect.set(w * 0.5f - tw * 0.62f, bh + h * 0.014f, w * 0.5f + tw * 0.62f, bh + h * 0.060f)
            c.drawRoundRect(rect, 16f, 16f, p)
            p.color = Color.rgb(255, 240, 160)
            c.drawText(msg, w * 0.5f, bh + h * 0.047f, p)
        }
        p.textAlign = Paint.Align.LEFT

        if (scene == PLAY) {
            val tsx = cxToW(GX) - camX
            val tsy = cyToW(GY) - camY
            if (tsx < 0f || tsx > w || tsy < 0f || tsy > h) {
                val dx = tsx - w * 0.5f
                val dy = tsy - h * 0.5f
                val d = max(1f, hypot(dx, dy))
                val rr = min(w, h) * 0.33f
                val ax = w * 0.5f + dx / d * rr
                val ay = h * 0.5f + dy / d * rr
                val ang = Math.atan2(dy.toDouble(), dx.toDouble()).toFloat()
                p.color = Color.argb(200, 255, 240, 140)
                path.reset()
                path.moveTo(ax + cos(ang) * 34f, ay + sin(ang) * 34f)
                path.lineTo(ax + cos(ang + 2.5f) * 26f, ay + sin(ang + 2.5f) * 26f)
                path.lineTo(ax + cos(ang - 2.5f) * 26f, ay + sin(ang - 2.5f) * 26f)
                path.close()
                c.drawPath(path, p)
                p.textAlign = Paint.Align.CENTER
                p.textSize = h * 0.019f
                c.drawText("ゴール", ax, ay + 56f, p)
                p.textAlign = Paint.Align.LEFT
            }
        }
    }

    private fun drawPad(c: Canvas) {
        p.style = Paint.Style.FILL
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
            val px = dcx + cos(t) * dr * 0.72f
            val py = dcy + sin(t) * dr * 0.72f
            path.reset()
            path.moveTo(px + cos(t) * a1 * 0.34f, py + sin(t) * a1 * 0.34f)
            path.lineTo(px + cos(t + 2.4f) * a1 * 0.34f, py + sin(t + 2.4f) * a1 * 0.34f)
            path.lineTo(px + cos(t - 2.4f) * a1 * 0.34f, py + sin(t - 2.4f) * a1 * 0.34f)
            path.close()
            c.drawPath(path, p)
        }

        p.color = Color.argb(90, 0, 0, 0)
        c.drawCircle(sbx, sby + 6f, sbr, p)
        p.color = Color.argb(228, 52, 126, 190)
        c.drawCircle(sbx, sby, sbr, p)
        p.color = Color.WHITE
        p.textAlign = Paint.Align.CENTER
        p.textSize = sbr * 0.60f
        c.drawText("さがす", sbx, sby + sbr * 0.22f, p)
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawOverlay(c: Canvas) {
        p.style = Paint.Style.FILL
        p.color = Color.argb(198, 8, 14, 10)
        c.drawRect(0f, 0f, w, h, p)
        p.textAlign = Paint.Align.CENTER

        if (scene == TITLE) {
            p.color = Color.rgb(250, 214, 90)
            p.textSize = h * 0.080f
            c.drawText("アンビー", w * 0.5f, h * 0.19f, p)
            p.color = Color.rgb(230, 230, 220)
            p.textSize = h * 0.026f
            c.drawText("めいろ の アンビーを ゴールへ", w * 0.5f, h * 0.25f, p)
            p.textSize = h * 0.023f
            p.color = Color.rgb(205, 214, 205)
            c.drawText("ゆびで おすと アンビーが よってくる", w * 0.5f, h * 0.34f, p)
            c.drawText("ただし まがれるのは 1かい だけ", w * 0.5f, h * 0.38f, p)
            c.drawText("かべに さえぎられると こられない", w * 0.5f, h * 0.42f, p)
            c.drawText("じゅうじキーで めいろを みわたす", w * 0.5f, h * 0.46f, p)
            c.drawText("「さがす」で とおい こから じゅんに みる", w * 0.5f, h * 0.50f, p)
            c.drawText("10びょう ほうっておくと すあなへ もどる", w * 0.5f, h * 0.54f, p)
            p.color = Color.rgb(250, 214, 90)
            p.textSize = h * 0.032f
            c.drawText("がめんを タップして スタート", w * 0.5f, h * 0.65f, p)
        } else if (scene == CLEAR) {
            p.color = Color.rgb(250, 214, 90)
            p.textSize = h * 0.068f
            c.drawText("ぜんいん ゴール！", w * 0.5f, h * 0.34f, p)
            p.color = Color.rgb(235, 235, 225)
            p.textSize = h * 0.032f
            c.drawText("タイム " + time.toInt() + " びょう", w * 0.5f, h * 0.43f, p)
            p.textSize = h * 0.028f
            c.drawText("がめんを タップして あたらしい めいろ", w * 0.5f, h * 0.56f, p)
        }
        p.textAlign = Paint.Align.LEFT
    }
}
