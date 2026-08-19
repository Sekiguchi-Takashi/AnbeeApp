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
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class SokoView(ctx: Context) : View(ctx) {

    var onExit: (() -> Unit)? = null

    private class Box {
        var cx = 0
        var cy = 0
        var dx = 0f
        var dy = 0f
    }

    private class Move {
        var pcx = 0
        var pcy = 0
        var box = -1
        var bcx = 0
        var bcy = 0
    }

    companion object {
        private const val WALL = 1
        private const val GOAL = 2

        private const val PREF = "soko"
        private const val KEY_CLEARED = "cleared"

        private const val REPEAT_DELAY = 0.30f
        private const val REPEAT_EVERY = 0.11f
        private const val LERP = 20f
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    private var w = 0f
    private var h = 0f
    private var fh = 0f
    private var panelH = 0f
    private var last = 0L
    private var running = true
    private var inited = false

    private var level = 0
    private var cols = 0
    private var rows = 0
    private var cell = ByteArray(0)
    private val boxes = ArrayList<Box>()
    private val undo = ArrayList<Move>()
    private val cleared = BooleanArray(Levels.count)

    private var pcx = 0
    private var pcy = 0
    private var pdx = 0f
    private var pdy = 0f
    private var face = 1f

    private var moves = 0
    private var pushes = 0
    private var done = false
    private var doneT = 0f
    private var time = 0f

    private var cs = 60f
    private var ox = 0f
    private var oy = 0f

    private var did = -1
    private var ddir = 0
    private var holdT = 0f
    private var repT = 0f

    private var sid = -1
    private var sx0 = 0f
    private var sy0 = 0f

    private var msg = ""
    private var msgT = 0f

    private var dcx = 0f
    private var dcy = 0f
    private var dr = 0f
    private var b1x = 0f
    private var b1y = 0f
    private var b2x = 0f
    private var b2y = 0f
    private var br = 0f
    private val gridR = RectF()
    private val menuR = RectF()

    init {
        isFocusable = true
        loadProgress()
    }

    fun resumeGame() {
        running = true
        last = 0L
        postInvalidateOnAnimation()
    }

    fun pauseGame() {
        running = false
        saveProgress()
    }

    private fun loadProgress() {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val s = sp.getString(KEY_CLEARED, "") ?: ""
        for (i in cleared.indices) {
            cleared[i] = i < s.length && s[i] == '1'
        }
        var next = 0
        for (i in cleared.indices) {
            if (!cleared[i]) {
                next = i
                break
            }
            next = min(i + 1, Levels.count - 1)
        }
        level = next
    }

    private fun saveProgress() {
        val sb = StringBuilder()
        for (b in cleared) sb.append(if (b) '1' else '0')
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_CLEARED, sb.toString()).apply()
    }

    override fun onSizeChanged(nw: Int, nh: Int, oldw: Int, oldh: Int) {
        w = nw.toFloat()
        h = nh.toFloat()
        layoutPad()
        if (!inited) {
            inited = true
            loadLevel(level)
        } else {
            fitBoard()
        }
    }

    private fun layoutPad() {
        panelH = UiKit.panelHeight(w, h)
        fh = h - panelH
        val pad = w * 0.035f
        val statusH = panelH * 0.17f
        val top = fh + statusH
        val ch = panelH - statusH
        val mg = ch * 0.06f

        dr = min(w * 0.142f, ch * 0.40f)
        br = dr * 0.50f

        dcx = pad + dr
        dcy = top + ch * 0.5f

        val bx = dcx + dr + br + w * 0.030f
        b1x = bx
        b1y = top + mg + br
        b2x = bx
        b2y = top + ch - mg - br

        gridR.set(bx + br + w * 0.035f, top + mg * 0.6f, w - pad, h - mg * 0.8f)
        UiKit.menuRect(menuR, w, fh)
    }

    private fun clamp(v: Float, lo: Float, hi: Float): Float {
        if (v < lo) return lo
        if (v > hi) return hi
        return v
    }

    // ---------------- level ----------------

    private fun at(cx: Int, cy: Int): Int {
        if (cx < 0 || cy < 0 || cx >= cols || cy >= rows) return WALL
        return cell[cy * cols + cx].toInt()
    }

    private fun isWall(cx: Int, cy: Int): Boolean {
        return (at(cx, cy) and WALL) != 0
    }

    private fun isGoal(cx: Int, cy: Int): Boolean {
        return (at(cx, cy) and GOAL) != 0
    }

    private fun boxAt(cx: Int, cy: Int): Int {
        for (i in boxes.indices) {
            val b = boxes[i]
            if (b.cx == cx && b.cy == cy) return i
        }
        return -1
    }

    private fun loadLevel(n: Int) {
        level = ((n % Levels.count) + Levels.count) % Levels.count
        val src = Levels.data[level]
        rows = src.size
        cols = 0
        for (r in src) cols = max(cols, r.length)
        cell = ByteArray(cols * rows)
        boxes.clear()
        undo.clear()
        moves = 0
        pushes = 0
        done = false
        doneT = 0f
        face = 1f
        ddir = 0
        did = -1
        sid = -1

        for (y in 0 until rows) {
            val row = src[y]
            for (x in 0 until cols) {
                val ch = if (x < row.length) row[x] else ' '
                var v = 0
                when (ch) {
                    '#' -> v = WALL
                    '.' -> v = GOAL
                    '*' -> {
                        v = GOAL
                        val b = Box()
                        b.cx = x
                        b.cy = y
                        boxes.add(b)
                    }
                    '$' -> {
                        val b = Box()
                        b.cx = x
                        b.cy = y
                        boxes.add(b)
                    }
                    '@' -> {
                        pcx = x
                        pcy = y
                    }
                    '+' -> {
                        v = GOAL
                        pcx = x
                        pcy = y
                    }
                }
                cell[y * cols + x] = v.toByte()
            }
        }
        fitBoard()
        pdx = pcx.toFloat()
        pdy = pcy.toFloat()
        for (b in boxes) {
            b.dx = b.cx.toFloat()
            b.dy = b.cy.toFloat()
        }
        msg = "レベル " + (level + 1)
        msgT = 1.6f
    }

    private fun fitBoard() {
        if (cols == 0 || rows == 0) return
        cs = min(w * 0.92f / cols, fh * 0.82f / rows)
        ox = (w - cols * cs) * 0.5f
        oy = fh * 0.5f - rows * cs * 0.5f
    }

    private fun checkDone(): Boolean {
        for (b in boxes) if (!isGoal(b.cx, b.cy)) return false
        return true
    }

    private fun tryMove(dx: Int, dy: Int) {
        if (done) return
        val nx = pcx + dx
        val ny = pcy + dy
        if (dx != 0) face = if (dx > 0) 1f else -1f
        if (isWall(nx, ny)) return

        val m = Move()
        m.pcx = pcx
        m.pcy = pcy
        m.box = -1

        val bi = boxAt(nx, ny)
        if (bi >= 0) {
            val bx = nx + dx
            val by = ny + dy
            if (isWall(bx, by)) return
            if (boxAt(bx, by) >= 0) return
            val b = boxes[bi]
            m.box = bi
            m.bcx = b.cx
            m.bcy = b.cy
            b.cx = bx
            b.cy = by
            pushes++
        }
        pcx = nx
        pcy = ny
        moves++
        undo.add(m)

        if (checkDone()) {
            done = true
            doneT = 0f
            if (!cleared[level]) {
                cleared[level] = true
                saveProgress()
            }
        }
    }

    private fun undoOne() {
        if (undo.isEmpty()) return
        val m = undo.removeAt(undo.size - 1)
        pcx = m.pcx
        pcy = m.pcy
        if (m.box >= 0) {
            val b = boxes[m.box]
            b.cx = m.bcx
            b.cy = m.bcy
            if (pushes > 0) pushes--
        }
        if (moves > 0) moves--
        done = false
        msg = "もどした"
        msgT = 0.9f
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
                    if (id == did) {
                        val nd = dirOf(e.getX(i), e.getY(i))
                        if (nd != ddir) {
                            ddir = nd
                            holdT = 0f
                            repT = 0f
                            if (nd != 0) stepDir(nd)
                        }
                    } else if (id == sid) {
                        swipeMove(e.getX(i), e.getY(i))
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val id = e.getPointerId(e.actionIndex)
                if (id == did) {
                    did = -1
                    ddir = 0
                }
                if (id == sid) sid = -1
            }
            MotionEvent.ACTION_CANCEL -> {
                did = -1
                ddir = 0
                sid = -1
            }
        }
        return true
    }

    private fun onDown(id: Int, x: Float, y: Float) {
        if (y >= fh) {
            if (abs(x - dcx) < dr * 1.25f && abs(y - dcy) < dr * 1.25f) {
                did = id
                ddir = dirOf(x, y)
                holdT = 0f
                repT = 0f
                if (ddir != 0) stepDir(ddir)
                return
            }
            if (hypot(x - b1x, y - b1y) < br * 1.35f) {
                undoOne()
                return
            }
            if (hypot(x - b2x, y - b2y) < br * 1.35f) {
                loadLevel(level)
                msg = "やりなおし"
                msgT = 1.2f
                return
            }
            tapGrid(x, y)
            return
        }
        if (menuR.contains(x, y)) {
            did = -1
            ddir = 0
            sid = -1
            saveProgress()
            onExit?.invoke()
            return
        }
        if (done) {
            loadLevel(level + 1)
            return
        }
        if (sid < 0) {
            sid = id
            sx0 = x
            sy0 = y
        }
    }

    private fun swipeMove(x: Float, y: Float) {
        val th = max(cs * 0.55f, 26f)
        var dx = x - sx0
        var dy = y - sy0
        if (abs(dx) < th && abs(dy) < th) return
        if (abs(dx) > abs(dy)) {
            tryMove(if (dx > 0) 1 else -1, 0)
            sx0 = if (dx > 0) sx0 + th else sx0 - th
            sy0 = y
        } else {
            tryMove(0, if (dy > 0) 1 else -1)
            sy0 = if (dy > 0) sy0 + th else sy0 - th
            sx0 = x
        }
    }

    private fun stepDir(d: Int) {
        when (d) {
            1 -> tryMove(0, -1)
            2 -> tryMove(0, 1)
            3 -> tryMove(-1, 0)
            4 -> tryMove(1, 0)
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

    private fun tapGrid(x: Float, y: Float) {
        if (!gridR.contains(x, y)) return
        val gc = 5
        val gr = (Levels.count + gc - 1) / gc
        val cw = gridR.width() / gc
        val chh = gridR.height() / gr
        val gx = ((x - gridR.left) / cw).toInt()
        val gy = ((y - gridR.top) / chh).toInt()
        val n = gy * gc + gx
        if (gx < 0 || gx >= gc || n < 0 || n >= Levels.count) return
        loadLevel(n)
    }

    // ---------------- update ----------------

    private fun update(dt: Float) {
        time += dt
        if (msgT > 0f) msgT -= dt
        if (done) doneT += dt

        if (ddir != 0) {
            holdT += dt
            if (holdT >= REPEAT_DELAY) {
                repT += dt
                if (repT >= REPEAT_EVERY) {
                    repT = 0f
                    stepDir(ddir)
                }
            }
        }

        val k = min(1f, dt * LERP)
        pdx += (pcx - pdx) * k
        pdy += (pcy - pdy) * k
        for (b in boxes) {
            b.dx += (b.cx - b.dx) * k
            b.dy += (b.cy - b.dy) * k
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
        drawBoard(c)
        UiKit.drawMenuBtn(c, p, menuR)
        drawMsg(c)
        if (done) drawDone(c)
        c.restore()

        drawPanel(c)

        if (running) postInvalidateOnAnimation()
    }

    private fun sxOf(fx: Float): Float {
        return ox + fx * cs
    }

    private fun syOf(fy: Float): Float {
        return oy + fy * cs
    }

    private fun drawBoard(c: Canvas) {
        p.style = Paint.Style.FILL
        c.drawColor(Color.rgb(196, 210, 178))

        // ゆか
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                if (isWall(x, y)) continue
                val sx = sxOf(x.toFloat())
                val sy = syOf(y.toFloat())
                p.color = if ((x + y) % 2 == 0) Color.rgb(228, 222, 200) else Color.rgb(218, 212, 190)
                c.drawRect(sx, sy, sx + cs, sy + cs, p)
            }
        }

        // ゴール
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                if (!isGoal(x, y) || isWall(x, y)) continue
                val sx = sxOf(x.toFloat()) + cs * 0.5f
                val sy = syOf(y.toFloat()) + cs * 0.5f
                p.style = Paint.Style.STROKE
                p.strokeWidth = cs * 0.055f
                p.color = Color.rgb(214, 132, 40)
                rect.set(sx - cs * 0.26f, sy - cs * 0.26f, sx + cs * 0.26f, sy + cs * 0.26f)
                c.drawRoundRect(rect, cs * 0.08f, cs * 0.08f, p)
                p.style = Paint.Style.FILL
                c.drawCircle(sx, sy, cs * 0.08f, p)
            }
        }

        // かべ
        p.style = Paint.Style.FILL
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                if (!isWall(x, y)) continue
                val sx = sxOf(x.toFloat())
                val sy = syOf(y.toFloat())
                p.color = Color.rgb(28, 32, 30)
                c.drawRect(sx, sy, sx + cs, sy + cs, p)
                p.color = Color.rgb(48, 54, 50)
                c.drawRect(sx + cs * 0.10f, sy + cs * 0.10f, sx + cs * 0.90f, sy + cs * 0.42f, p)
                c.drawRect(sx + cs * 0.10f, sy + cs * 0.56f, sx + cs * 0.90f, sy + cs * 0.88f, p)
            }
        }

        for (b in boxes) drawBox(c, b)
        drawPlayer(c)
    }

    private fun drawBox(c: Canvas, b: Box) {
        val sx = sxOf(b.dx)
        val sy = syOf(b.dy)
        val on = isGoal(b.cx, b.cy)
        val m = cs * 0.09f

        p.style = Paint.Style.FILL
        p.color = Color.argb(60, 0, 0, 0)
        c.drawRect(sx + m + cs * 0.04f, sy + m + cs * 0.06f, sx + cs - m + cs * 0.04f, sy + cs - m + cs * 0.06f, p)

        p.color = if (on) Color.rgb(214, 158, 48) else Color.rgb(176, 122, 62)
        rect.set(sx + m, sy + m, sx + cs - m, sy + cs - m)
        c.drawRoundRect(rect, cs * 0.09f, cs * 0.09f, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = cs * 0.06f
        p.color = if (on) Color.rgb(150, 98, 20) else Color.rgb(120, 78, 36)
        c.drawRoundRect(rect, cs * 0.09f, cs * 0.09f, p)
        c.drawLine(sx + m, sy + m, sx + cs - m, sy + cs - m, p)
        c.drawLine(sx + cs - m, sy + m, sx + m, sy + cs - m, p)
        p.style = Paint.Style.FILL

        if (on) {
            p.color = Color.argb(190, 255, 246, 190)
            c.drawCircle(sx + cs * 0.5f, sy + cs * 0.5f, cs * 0.11f, p)
        }
    }

    private fun drawPlayer(c: Canvas) {
        val r = cs * 0.30f
        val sx = sxOf(pdx) + cs * 0.5f
        val sy = syOf(pdy) + cs * 0.86f
        Chara.draw(c, p, path, rect, sx, sy, r, false, done, face, time * 5f)
    }

    private fun drawMsg(c: Canvas) {
        if (msgT <= 0f || done) return
        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER
        p.textSize = h * 0.028f
        val tw = p.measureText(msg)
        p.color = Color.argb(165, 0, 0, 0)
        rect.set(w * 0.5f - tw * 0.62f, fh - h * 0.070f, w * 0.5f + tw * 0.62f, fh - h * 0.024f)
        c.drawRoundRect(rect, 16f, 16f, p)
        p.color = Color.rgb(255, 240, 160)
        c.drawText(msg, w * 0.5f, fh - h * 0.036f, p)
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawDone(c: Canvas) {
        val k = clamp(doneT * 2.2f, 0f, 1f)
        p.style = Paint.Style.FILL
        p.color = Color.argb((170 * k).toInt(), 10, 18, 12)
        c.drawRect(0f, 0f, w, fh, p)
        p.textAlign = Paint.Align.CENTER
        p.color = Color.rgb(250, 214, 90)
        p.textSize = fh * 0.090f
        c.drawText("クリア！", w * 0.5f, fh * 0.42f, p)
        p.color = Color.rgb(235, 235, 225)
        p.textSize = fh * 0.038f
        c.drawText("てすう " + moves + "   おした " + pushes, w * 0.5f, fh * 0.52f, p)
        p.textSize = fh * 0.034f
        val nx = if (level + 1 < Levels.count) "がめんを タップして つぎのレベル"
        else "がめんを タップして さいしょへ"
        c.drawText(nx, w * 0.5f, fh * 0.63f, p)
        p.textAlign = Paint.Align.LEFT
    }

    // ---------------- panel ----------------

    private fun drawPanel(c: Canvas) {
        UiKit.drawPanelBase(c, p, w, h, fh, panelH)
        drawStatus(c)
        UiKit.drawDpad(c, p, path, rect, dcx, dcy, dr, ddir)

        val undoCol = if (undo.isEmpty()) Color.rgb(140, 140, 146) else Color.rgb(52, 126, 190)
        drawBtn(c, b1x, b1y, undoCol, "もどす")
        drawBtn(c, b2x, b2y, Color.rgb(180, 92, 40), "やりなおし")

        drawGrid(c)
    }

    private fun drawBtn(c: Canvas, x: Float, y: Float, col: Int, label: String) {
        UiKit.drawBtn(c, p, x, y, br, col, label)
    }

    private fun drawStatus(c: Canvas) {
        var n = 0
        for (b in cleared) if (b) n++
        UiKit.drawStatusRow(
            c, p, w, fh, panelH,
            "レベル " + (level + 1) + " / " + Levels.count + "   てすう " + moves + "   おした " + pushes,
            Color.rgb(40, 40, 44),
            "クリア " + n + "/" + Levels.count, Color.rgb(28, 92, 48)
        )
    }

    private fun drawGrid(c: Canvas) {
        val gc = 5
        val gr = (Levels.count + gc - 1) / gc
        val cw = gridR.width() / gc
        val chh = gridR.height() / gr
        val pad = min(cw, chh) * 0.10f

        p.textAlign = Paint.Align.CENTER
        for (i in 0 until Levels.count) {
            val gx = i % gc
            val gy = i / gc
            val l = gridR.left + gx * cw
            val t = gridR.top + gy * chh
            rect.set(l + pad, t + pad, l + cw - pad, t + chh - pad)
            p.style = Paint.Style.FILL
            p.color = when {
                i == level -> Color.rgb(52, 126, 190)
                cleared[i] -> Color.rgb(96, 172, 108)
                else -> Color.rgb(176, 170, 158)
            }
            c.drawRoundRect(rect, pad * 1.2f, pad * 1.2f, p)
            if (cleared[i] && i != level) {
                p.color = Color.rgb(240, 250, 240)
            } else {
                p.color = Color.WHITE
            }
            val ts = min(rect.height() * 0.58f, rect.width() * 0.58f)
            p.textSize = ts
            c.drawText((i + 1).toString(), rect.centerX(), rect.centerY() + ts * 0.35f, p)
        }
        p.textAlign = Paint.Align.LEFT
    }
}
