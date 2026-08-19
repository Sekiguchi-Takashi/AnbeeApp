package com.appathy.anbee

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

class MainActivity : Activity() {

    private var root: FrameLayout? = null
    private var title: TitleView? = null
    private var maze: MazeView? = null
    private var soko: SokoView? = null
    private var mode = 0 // 0 タイトル / 1 めいろ / 2 そうこばん

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val f = FrameLayout(this)
        root = f
        setContentView(f)
        showTitle()
        hideBars()
    }

    private fun swap(v: View) {
        val f = root ?: return
        maze?.pauseGame()
        soko?.pauseGame()
        f.removeAllViews()
        f.addView(
            v,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun showTitle() {
        mode = 0
        var t = title
        if (t == null) {
            t = TitleView(this)
            t.onPick = { pick ->
                if (pick == 0) showMaze() else showSoko()
            }
            title = t
        }
        swap(t)
    }

    private fun showMaze() {
        mode = 1
        var v = maze
        if (v == null) {
            v = MazeView(this)
            v.onExit = { showTitle() }
            maze = v
        }
        swap(v)
        v.resumeGame()
    }

    private fun showSoko() {
        mode = 2
        var v = soko
        if (v == null) {
            v = SokoView(this)
            v.onExit = { showTitle() }
            soko = v
        }
        swap(v)
        v.resumeGame()
    }

    private fun hideBars() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    override fun onBackPressed() {
        if (mode == 0) {
            super.onBackPressed()
        } else {
            showTitle()
        }
    }

    override fun onResume() {
        super.onResume()
        when (mode) {
            1 -> maze?.resumeGame()
            2 -> soko?.resumeGame()
        }
    }

    override fun onPause() {
        super.onPause()
        maze?.pauseGame()
        soko?.pauseGame()
    }
}
