package com.appathy.anbee

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
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

class MazeView(ctx: Context) : View(ctx) {

    var onExit: (() -> Unit)? = null

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

        private const val COLS = 27
        private const val ROWS = 41
        private const val FW = 150f
        private const val WT = 40f
        private const val TOTAL = 10
        private const val LOOP_P = 0.22f

        private const val SX = 1
        private const val SY = ROWS - 2
        private const val GX = COLS - 2
        private const val GY = 1

        private const val COME_SPEED = 400f
        private const val BACK_SPEED = 96f
        private const val CAM_SPEED = 900f
        private const val IDLE_LIMIT = 10f
        private const val REPLAN = 0.12f
        private const val MM_K = 10f
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val srcR = Rect()
    private val dstR = RectF()

    private var w = 0f
    private var h = 0f
    private var fh = 0f
    private var panelH = 0f
    private var last = 0L
    private var running = true
    private var inited = false

    private var scene = TITLE
    private var paused = false
    private var camX = 0f
    private var camY = 0f
    private var camTX = 0f
    private var camTY = 0f
    private var camAuto = false
    private var time = 0f

    private val grid = ByteArray(COLS * ROWS)
    private val dist = IntArray(COLS * ROWS)
    private val queue = IntArray(COLS * ROWS)
    private val colX = FloatArray(COLS + 1)
    private val rowY = FloatArray(ROWS + 1)
    private var ww = 0f
    private var wh = 0f

    private var mini: Bitmap? = null

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

    private var antR = 28f
    private var callR = 140f

    private var dcx = 0f
    private var dcy = 0f
    private var dr = 0f
    private var sbx = 0f
    private var sby = 0f
    private var ssx = 0f
    private var ssy = 0f
    private var br = 0f
    private val mmR = RectF()
    private val menuR = RectF()

    init {
        isFocusable = true
        for (i in 0 until TOTAL) ants.add(Ant())
        buildAxes()
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
        antR = min(w, h) * 0.026f
        callR = FW * 0.92f
        layoutPad()
        if (!inited) {
            inited = true
            resetGame()
        } else {
            camX = clamp(camX, 0f, max(0f, ww - w))
            camY = clamp(camY, 0f, max(0f, wh - fh))
        }
    }

    private fun layoutPad() {
        panelH = clamp(w * 0.40f, h * 0.20f, h * 0.30f)
        fh = h - panelH
        val pad = w * 0.035f
        val statusH = panelH * 0.17f
        val ctrlTop = fh + statusH
        val ctrlH = panelH - statusH
        val mg = ctrlH * 0.06f

        dr = min(w * 0.142f, ctrlH * 0.40f)
        br = dr * 0.50f

        dcx = pad + dr
        dcy = ctrlTop + ctrlH * 0.5f

        val bx = dcx + dr + br + w * 0.030f
        sbx = bx
        sby = ctrlTop + mg + br
        ssx = bx
        ssy = ctrlTop + ctrlH - mg - br

        mmR.set(bx + br + w * 0.035f, ctrlTop + mg * 0.6f, w - pad, h - mg * 0.8f)
        UiKit.menuRect(menuR, w, fh)
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

    // ---------------- grid geometry ----------------

    private fun colW(cx: Int): Float {
        return if (cx % 2 == 1) FW else WT
    }

    private fun rowH(cy: Int): Float {
        return if (cy % 2 == 1) FW else WT
    }

    private fun buildAxes() {
        colX[0] = 0f
        for (i in 0 until COLS) colX[i + 1] = colX[i] + colW(i)
        rowY[0] = 0f
        for (i in 0 until ROWS) rowY[i + 1] = rowY[i] + rowH(i)
        ww = colX[COLS]
        wh = rowY[ROWS]
    }

    private fun cxToW(cx: Int): Float {
        return colX[cx] + colW(cx) * 0.5f
    }

    private fun cyToW(cy: Int): Float {
        return rowY[cy] + rowH(cy) * 0.5f
    }

    private fun cellX(x: Float): Int {
        if (x <= 0f) return 0
        if (x >= ww) return COLS - 1
        for (i in 0 until COLS) if (x < colX[i + 1]) return i
        return COLS - 1
    }

    private fun cellY(y: Float): Int {
        if (y <= 0f) return 0
        if (y >= wh) return ROWS - 1
        for (i in 0 until ROWS) if (y < rowY[i + 1]) return i
        return ROWS - 1
    }

    private fun idx(cx: Int, cy: Int): Int {
        return cy * COLS + cx
    }

    private fun isWall(cx: Int, cy: Int): Boolean {
        if (cx < 0 || cy < 0 || cx >= COLS || cy >= ROWS) return true
        return grid[idx(cx, cy)].toInt() == 1
    }

    private fun fitOff(off: Float, size: Float): Float {
        val lim = max(0f, size * 0.5f - antR * 0.75f)
        return clamp(off, -lim, lim)
    }

    private fun wpX(cx: Int, a: Ant): Float {
        return cxToW(cx) + fitOff(a.ox, colW(cx))
    }

    private fun wpY(cy: Int, a: Ant): Float {
        return cyToW(cy) + fitOff(a.oy, rowH(cy))
    }

    // ---------------- maze ----------------

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
        // 連結セル（片方だけ奇数）のみ開放してループを作る。柱(偶,偶)は絶対に開けない
        for (cy in 1 until ROWS - 1) {
            for (cx in 1 until COLS - 1) {
                if (grid[idx(cx, cy)].toInt() == 0) continue
                if ((cx % 2 == 1) == (cy % 2 == 1)) continue
                if (Random.nextFloat() < LOOP_P) grid[idx(cx, cy)] = 0
            }
        }
        openSides(SX, SY)
        openSides(GX, GY)
        buildDist()
        buildMini()
    }

    private fun openSides(cx: Int, cy: Int) {
        val dxs = intArrayOf(0, 0, -1, 1)
        val dys = intArrayOf(-1, 1, 0, 0)
        grid[idx(cx, cy)] = 0
        for (d in 0 until 4) {
            val nx = cx + dxs[d]
            val ny = cy + dys[d]
            if (nx < 1 || ny < 1 || nx > COLS - 2 || ny > ROWS - 2) continue
            grid[idx(nx, ny)] = 0
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

    private fun buildMini() {
        val mw = max(1, (ww / MM_K).toInt())
        val mh = max(1, (wh / MM_K).toInt())
        var bmp = mini
        if (bmp == null || bmp.width != mw || bmp.height != mh) {
            bmp?.recycle()
            bmp = Bitmap.createBitmap(mw, mh, Bitmap.Config.ARGB_8888)
            mini = bmp
        }
        val mc = Canvas(bmp)
        mc.drawColor(Color.rgb(206, 224, 186))
        val q = Paint()
        q.style = Paint.Style.FILL
        q.color = Color.rgb(28, 36, 30)
        for (cy in 0 until ROWS) {
            for (cx in 0 until COLS) {
                if (!isWall(cx, cy)) continue
                mc.drawRect(
                    colX[cx] / MM_K, rowY[cy] / MM_K,
                    colX[cx + 1] / MM_K, rowY[cy + 1] / MM_K, q
                )
            }
        }
        q.color = Color.rgb(238, 196, 60)
        mc.drawCircle(cxToW(GX) / MM_K, cyToW(GY) / MM_K, FW / MM_K * 0.9f, q)
        q.color = Color.rgb(150, 112, 74)
        mc.drawCircle(cxToW(SX) / MM_K, cyToW(SY) / MM_K, FW / MM_K * 0.8f, q)
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
        paused = false
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
        for (i in dist.indices) {
            if (dist[i] < 8) continue
            val cx = i % COLS
            val cy = i / COLS
            if (cx % 2 == 0 || cy % 2 == 0) continue
            spots.add(i)
        }
        spots.sortBy { dist[it] }

        for (i in 0 until TOTAL) {
            val a = ants[i]
            val t = i.toFloat() / TOTAL * 6.2832f
            a.ox = cos(t) * FW * 0.24f
            a.oy = sin(t) * FW * 0.24f
            a.mode = STAY
            a.saved = false
            a.idle = 0f
            a.reach = false
            a.ph = Random.nextFloat() * 6.2832f
            a.tone = Random.nextFloat()
            a.face = 1f
            var cell = idx(SX, SY)
            if (spots.size > 0) {
                val lo = (spots.size * (0.20f + 0.072f * i)).toInt()
                cell = spots[clampI(lo, 0, spots.size - 1)]
            }
            a.x = cxToW(cell % COLS) + a.ox
            a.y = cyToW(cell / COLS) + a.oy
        }

        camX = clamp(cxToW(SX) - w * 0.5f, 0f, max(0f, ww - w))
        camY = clamp(cyToW(SY) - fh * 0.5f, 0f, max(0f, wh - fh))
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
                        fy = min(e.getY(i), fh - 2f)
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
        if (y >= fh) {
            if (abs(x - dcx) < dr * 1.25f && abs(y - dcy) < dr * 1.25f) {
                did = id
                ddir = dirOf(x, y)
                camAuto = false
                return
            }
            if (hypot(x - sbx, y - sby) < br * 1.35f) {
                if (scene == PLAY && !paused) searchNext()
                return
            }
            if (hypot(x - ssx, y - ssy) < br * 1.35f) {
                onStartStop()
                return
            }
            if (mmR.contains(x, y)) tapMini(x, y)
            return
        }
        if (menuR.contains(x, y)) {
            fid = -1
            did = -1
            ddir = 0
            onExit?.invoke()
            return
        }
        if (scene != PLAY || paused) return
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

    private fun onStartStop() {
        if (scene == TITLE) {
            resetGame()
            scene = PLAY
            return
        }
        if (scene == CLEAR) {
            resetGame()
            scene = PLAY
            msg = "あたらしい めいろ"
            msgT = 2f
            return
        }
        paused = !paused
        if (paused) {
            fid = -1
            for (a in ants) if (a.mode == COME) a.mode = STAY
        }
    }

    private fun tapMini(x: Float, y: Float) {
        val bmp = mini ?: return
        fitMini(bmp)
        if (!dstR.contains(x, y)) return
        val u = (x - dstR.left) / dstR.width()
        val v = (y - dstR.top) / dstR.height()
        camTX = clamp(u * ww - w * 0.5f, 0f, max(0f, ww - w))
        camTY = clamp(v * wh - fh * 0.5f, 0f, max(0f, wh - fh))
        camAuto = true
    }

    private fun fitMini(bmp: Bitmap) {
        val sw = mmR.width()
        val sh = mmR.height()
        val ar = bmp.width.toFloat() / bmp.height.toFloat()
        var dw = sh * ar
        var dh = sh
        if (dw > sw) {
            dw = sw
            dh = sw / ar
        }
        val cx = mmR.centerX()
        val cy = mmR.centerY()
        dstR.set(cx - dw * 0.5f, cy - dh * 0.5f, cx + dw * 0.5f, cy + dh * 0.5f)
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
        camTX = clamp(a.x - w * 0.5f, 0f, max(0f, ww - w))
        camTY = clamp(a.y - fh * 0.5f, 0f, max(0f, wh - fh))
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
        if (scene != PLAY || paused) return
        time += dt

        if (ddir != 0) {
            val s = CAM_SPEED * dt
            when (ddir) {
                1 -> camY -= s
                2 -> camY += s
                3 -> camX -= s
                4 -> camX += s
            }
            camX = clamp(camX, 0f, max(0f, ww - w))
            camY = clamp(camY, 0f, max(0f, wh - fh))
            camAuto = false
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
        a.w1x = wpX(cnx, a)
        a.w1y = wpY(cny, a)
        a.w2x = wpX(tcx, a)
        a.w2y = wpY(tcy, a)
        a.wi = if (hypot(a.w1x - a.x, a.w1y - a.y) < FW * 0.28f) 1 else 0
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
        if (d < FW * 0.13f) {
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
        if (isWall(acx, acy)) return
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
        val tx = cxToW(bx) + fitOff(a.ox * 0.4f, colW(bx))
        val ty = cyToW(by) + fitOff(a.oy * 0.4f, rowH(by))
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
        val m = antR * 0.42f
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

        c.save()
        c.clipRect(0f, 0f, w, fh)
        drawWorld(c)
        drawFieldMsg(c)
        if (scene != PLAY) drawOverlay(c)
        else if (paused) drawPausedMask(c)
        c.restore()

        drawPanel(c)

        if (running) postInvalidateOnAnimation()
    }

    private fun drawWorld(c: Canvas) {
        p.style = Paint.Style.FILL
        c.drawColor(Color.rgb(196, 214, 168))

        val x0 = clampI(cellX(camX) - 1, 0, COLS - 1)
        val x1 = clampI(cellX(camX + w) + 1, 0, COLS - 1)
        val y0 = clampI(cellY(camY) - 1, 0, ROWS - 1)
        val y1 = clampI(cellY(camY + fh) + 1, 0, ROWS - 1)

        // 床
        p.color = Color.rgb(170, 206, 140)
        for (cy in y0..y1) {
            for (cx in x0..x1) {
                if (isWall(cx, cy)) continue
                c.drawRect(
                    colX[cx] - camX, rowY[cy] - camY,
                    colX[cx + 1] - camX, rowY[cy + 1] - camY, p
                )
            }
        }

        // 壁（濃い黒の太線）
        p.color = Color.rgb(22, 26, 24)
        for (cy in y0..y1) {
            for (cx in x0..x1) {
                if (!isWall(cx, cy)) continue
                c.drawRect(
                    colX[cx] - camX, rowY[cy] - camY,
                    colX[cx + 1] - camX, rowY[cy + 1] - camY, p
                )
            }
        }

        drawHome(c, cxToW(SX) - camX, cyToW(SY) - camY)
        drawGoal(c, cxToW(GX) - camX, cyToW(GY) - camY)

        if (fid >= 0 && scene == PLAY && !paused) drawPaths(c)

        for (a in ants) drawAnt(c, a)

        if (fid >= 0 && scene == PLAY && !paused) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 5f
            p.color = Color.argb(200, 255, 244, 170)
            c.drawCircle(fx, fy, callR * (0.94f + 0.06f * sin(time * 4f)), p)
            p.strokeWidth = 3f
            p.color = Color.argb(120, 255, 244, 170)
            c.drawCircle(fx, fy, callR * 0.52f, p)
            p.style = Paint.Style.FILL
        }

        val ma = markA
        if (markT > 0f && ma != null && !ma.saved) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 6f
            p.color = Color.argb((220 * min(1f, markT)).toInt(), 255, 110, 80)
            c.drawCircle(
                ma.x - camX, ma.y - camY - antR * 1.1f,
                antR * (2.2f + 0.5f * sin(time * 7f)), p
            )
            p.style = Paint.Style.FILL
        }

        drawGoalArrow(c)
        UiKit.drawMenuBtn(c, p, menuR)
    }

    private fun drawPaths(c: Canvas) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = 7f
        p.color = Color.argb(115, 255, 250, 180)
        for (a in ants) {
            if (a.saved || !a.reach) continue
            c.drawLine(a.x - camX, a.y - camY, a.w1x - camX, a.w1y - camY, p)
            c.drawLine(a.w1x - camX, a.w1y - camY, a.w2x - camX, a.w2y - camY, p)
        }
        p.style = Paint.Style.FILL
    }

    private fun drawHome(c: Canvas, sx: Float, sy: Float) {
        if (sx < -FW * 3f || sx > w + FW * 3f || sy < -FW * 3f || sy > fh + FW * 3f) return
        p.style = Paint.Style.FILL
        p.color = Color.rgb(146, 112, 74)
        c.drawOval(sx - FW * 0.40f, sy - FW * 0.24f, sx + FW * 0.40f, sy + FW * 0.34f, p)
        p.color = Color.rgb(66, 48, 32)
        c.drawOval(sx - FW * 0.22f, sy - FW * 0.10f, sx + FW * 0.22f, sy + FW * 0.24f, p)
        p.color = Color.argb(210, 255, 255, 255)
        p.textAlign = Paint.Align.CENTER
        p.textSize = FW * 0.20f
        c.drawText("すあな", sx, sy + FW * 0.56f, p)
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawGoal(c: Canvas, sx: Float, sy: Float) {
        if (sx < -FW * 3f || sx > w + FW * 3f || sy < -FW * 3f || sy > fh + FW * 3f) return
        val r = FW * 0.72f
        p.style = Paint.Style.FILL
        p.color = Color.argb(70, 20, 44, 24)
        c.drawCircle(sx, sy, r * 0.9f, p)
        p.color = Color.rgb(104, 74, 48)
        c.drawRect(sx - r * 0.13f, sy - r * 0.06f, sx + r * 0.13f, sy + r * 0.52f, p)
        p.color = Color.rgb(48, 116, 58)
        c.drawCircle(sx, sy - r * 0.26f, r * 0.50f, p)
        c.drawCircle(sx - r * 0.36f, sy, r * 0.36f, p)
        c.drawCircle(sx + r * 0.36f, sy, r * 0.36f, p)
        p.color = Color.rgb(70, 148, 80)
        c.drawCircle(sx - r * 0.14f, sy - r * 0.38f, r * 0.24f, p)
        c.drawCircle(sx + r * 0.22f, sy - r * 0.15f, r * 0.19f, p)
        p.color = Color.argb(230, 255, 255, 255)
        p.textAlign = Paint.Align.CENTER
        p.textSize = r * 0.32f
        c.drawText("ゴール", sx, sy + r * 0.90f, p)
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawAnt(c: Canvas, a: Ant) {
        val sx = a.x - camX
        val sy = a.y - camY
        val r = antR
        if (sx < -r * 6f || sx > w + r * 6f || sy < -r * 8f || sy > fh + r * 6f) return

        val pale = !a.saved && fid >= 0 && !a.reach
        Chara.draw(c, p, path, rect, sx, sy, r, pale, a.saved, a.face, a.ph)

        if (a.mode == BACK) {
            Chara.mark(c, p, sx, sy, r, "\uff1c", Color.argb(215, 255, 170, 120))
        } else if (!a.saved && a.idle > IDLE_LIMIT * 0.6f) {
            Chara.mark(c, p, sx, sy, r, "?", Color.argb(195, 255, 230, 140))
        }
    }

    private fun drawGoalArrow(c: Canvas) {
        if (scene != PLAY) return
        val tsx = cxToW(GX) - camX
        val tsy = cyToW(GY) - camY
        if (tsx >= 0f && tsx <= w && tsy >= 0f && tsy <= fh) return
        val dx = tsx - w * 0.5f
        val dy = tsy - fh * 0.5f
        val d = max(1f, hypot(dx, dy))
        val rr = min(w, fh) * 0.34f
        val ax = w * 0.5f + dx / d * rr
        val ay = fh * 0.5f + dy / d * rr
        val ang = Math.atan2(dy.toDouble(), dx.toDouble()).toFloat()
        p.style = Paint.Style.FILL
        p.color = Color.argb(205, 255, 226, 100)
        path.reset()
        path.moveTo(ax + cos(ang) * 34f, ay + sin(ang) * 34f)
        path.lineTo(ax + cos(ang + 2.5f) * 26f, ay + sin(ang + 2.5f) * 26f)
        path.lineTo(ax + cos(ang - 2.5f) * 26f, ay + sin(ang - 2.5f) * 26f)
        path.close()
        c.drawPath(path, p)
        p.textAlign = Paint.Align.CENTER
        p.textSize = min(w, fh) * 0.030f
        c.drawText("ゴール", ax, ay + 56f, p)
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawFieldMsg(c: Canvas) {
        if (msgT <= 0f) return
        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER
        p.textSize = h * 0.028f
        val tw = p.measureText(msg)
        p.color = Color.argb(175, 0, 0, 0)
        rect.set(w * 0.5f - tw * 0.62f, fh - h * 0.070f, w * 0.5f + tw * 0.62f, fh - h * 0.024f)
        c.drawRoundRect(rect, 16f, 16f, p)
        p.color = Color.rgb(255, 240, 160)
        c.drawText(msg, w * 0.5f, fh - h * 0.036f, p)
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawPausedMask(c: Canvas) {
        p.style = Paint.Style.FILL
        p.color = Color.argb(140, 8, 14, 10)
        c.drawRect(0f, 0f, w, fh, p)
        p.color = Color.rgb(250, 224, 130)
        p.textAlign = Paint.Align.CENTER
        p.textSize = h * 0.052f
        c.drawText("ストップ", w * 0.5f, fh * 0.5f, p)
        p.textSize = h * 0.024f
        p.color = Color.rgb(225, 225, 215)
        c.drawText("スタート で つづき", w * 0.5f, fh * 0.5f + h * 0.048f, p)
        p.textAlign = Paint.Align.LEFT
    }

    // ---------------- panel ----------------

    private fun drawPanel(c: Canvas) {
        UiKit.drawPanelBase(c, p, w, h, fh, panelH)
        drawStatus(c)
        UiKit.drawDpad(c, p, path, rect, dcx, dcy, dr, ddir)

        drawBtn(c, sbx, sby, Color.rgb(52, 126, 190), "さがす", 0.58f)
        val ssLabel = if (scene == PLAY && !paused) "ストップ" else "スタート"
        val ssColor = if (scene == PLAY && !paused) Color.rgb(180, 92, 40) else Color.rgb(60, 150, 88)
        drawBtn(c, ssx, ssy, ssColor, ssLabel, 0.42f)

        drawMini(c)
    }

    private fun drawBtn(c: Canvas, x: Float, y: Float, col: Int, label: String, ts: Float) {
        UiKit.drawBtn(c, p, x, y, br, col, label)
    }

    private fun drawStatus(c: Canvas) {
        var saved = 0
        var back = 0
        for (a in ants) {
            if (a.saved) saved++
            else if (a.mode == BACK) back++
        }
        var left = when {
            fid >= 0 -> "よんでる " + comeN + "  とどかない " + farN
            scene == TITLE -> "スタート を おしてね"
            paused -> "ストップちゅう"
            else -> "ゆびで おして よぼう"
        }
        if (back > 0 && scene == PLAY) left = left + "  もどり " + back
        val lc = if (back > 0 && scene == PLAY) Color.rgb(168, 74, 26) else Color.rgb(40, 40, 44)
        UiKit.drawStatusRow(
            c, p, w, fh, panelH,
            left, lc,
            "ゴール " + saved + "/" + TOTAL, Color.rgb(28, 92, 48)
        )
    }

    private fun drawMini(c: Canvas) {
        val bmp = mini ?: return
        fitMini(bmp)

        p.style = Paint.Style.FILL
        p.color = Color.rgb(58, 56, 58)
        rect.set(dstR.left - 5f, dstR.top - 5f, dstR.right + 5f, dstR.bottom + 5f)
        c.drawRoundRect(rect, 7f, 7f, p)

        srcR.set(0, 0, bmp.width, bmp.height)
        c.drawBitmap(bmp, srcR, dstR, null)

        val kx = dstR.width() / ww
        val ky = dstR.height() / wh

        // アンビー
        for (a in ants) {
            p.color = if (a.saved) Color.rgb(240, 200, 50) else Color.rgb(50, 100, 210)
            c.drawCircle(dstR.left + a.x * kx, dstR.top + a.y * ky, 3.4f, p)
        }

        // 現在地（赤）
        val vx = dstR.left + (camX + w * 0.5f) * kx
        val vy = dstR.top + (camY + fh * 0.5f) * ky
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f
        p.color = Color.argb(200, 226, 40, 40)
        c.drawRect(
            dstR.left + camX * kx, dstR.top + camY * ky,
            dstR.left + (camX + w) * kx, dstR.top + (camY + fh) * ky, p
        )
        p.style = Paint.Style.FILL
        p.color = Color.rgb(226, 40, 40)
        c.drawCircle(vx, vy, 5f, p)
        p.color = Color.argb(160, 255, 255, 255)
        c.drawCircle(vx, vy, 2f, p)
    }

    private fun drawOverlay(c: Canvas) {
        p.style = Paint.Style.FILL
        p.color = Color.argb(200, 8, 14, 10)
        c.drawRect(0f, 0f, w, fh, p)
        p.textAlign = Paint.Align.CENTER

        if (scene == TITLE) {
            p.color = Color.rgb(250, 214, 90)
            p.textSize = fh * 0.10f
            c.drawText("アンビー", w * 0.5f, fh * 0.20f, p)
            p.color = Color.rgb(230, 230, 220)
            p.textSize = fh * 0.033f
            c.drawText("めいろ の アンビーを ゴールへ", w * 0.5f, fh * 0.27f, p)
            p.textSize = fh * 0.029f
            p.color = Color.rgb(205, 214, 205)
            c.drawText("ゆびで おすと アンビーが よってくる", w * 0.5f, fh * 0.38f, p)
            c.drawText("ただし まがれるのは 1かい だけ", w * 0.5f, fh * 0.43f, p)
            c.drawText("かべに さえぎられると こられない", w * 0.5f, fh * 0.48f, p)
            c.drawText("じゅうじキーで めいろを みわたす", w * 0.5f, fh * 0.53f, p)
            c.drawText("「さがす」で とおい こから じゅんに みる", w * 0.5f, fh * 0.58f, p)
            c.drawText("10びょう ほうっておくと すあなへ もどる", w * 0.5f, fh * 0.63f, p)
            p.color = Color.rgb(250, 214, 90)
            p.textSize = fh * 0.038f
            c.drawText("した の スタート で はじめる", w * 0.5f, fh * 0.76f, p)
        } else if (scene == CLEAR) {
            p.color = Color.rgb(250, 214, 90)
            p.textSize = fh * 0.082f
            c.drawText("ぜんいん ゴール！", w * 0.5f, fh * 0.36f, p)
            p.color = Color.rgb(235, 235, 225)
            p.textSize = fh * 0.040f
            c.drawText("タイム " + time.toInt() + " びょう", w * 0.5f, fh * 0.47f, p)
            p.textSize = fh * 0.034f
            c.drawText("スタート で あたらしい めいろ", w * 0.5f, fh * 0.62f, p)
        }
        p.textAlign = Paint.Align.LEFT
    }
}
