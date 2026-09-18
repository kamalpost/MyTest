package app.focusphone.phone.screens

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.View
import app.focusphone.phone.Key
import app.focusphone.phone.Lcd
import app.focusphone.phone.Screen
import app.focusphone.phone.ScreenHost
import kotlin.random.Random

/** The game every feature phone must have. */
class SnakeScreen(host: ScreenHost) : Screen(host) {
    override val title: String get() = if (::game.isInitialized) "Snake  ${game.score}" else "Snake"
    override val leftSoft: String? get() = if (!::game.isInitialized) "Pause" else if (game.over) "New" else if (game.paused) "Resume" else "Pause"

    private lateinit var game: SnakeView

    override fun createView(): View {
        game = SnakeView(ctx) { host.refreshChrome() }
        game.highScore = ctx.prefs.snakeHighScore
        game.onGameOver = { score ->
            if (score > ctx.prefs.snakeHighScore) ctx.prefs.snakeHighScore = score
            game.highScore = ctx.prefs.snakeHighScore
        }
        return game
    }

    override fun onShow() { game.resumeLoop() }
    override fun onHide() { game.pauseLoop() }

    override fun onKey(key: Key): Boolean {
        when (key) {
            Key.UP, Key.D2 -> game.turn(0, -1)
            Key.DOWN, Key.D8 -> game.turn(0, 1)
            Key.LEFT, Key.D4 -> game.turn(-1, 0)
            Key.RIGHT, Key.D6 -> game.turn(1, 0)
            Key.OK, Key.SOFT_LEFT, Key.D5 -> if (game.over) game.restart() else game.togglePause()
            else -> return false
        }
        host.refreshChrome()
        return true
    }
}

class SnakeView(ctx: Context, private val onScore: () -> Unit) : View(ctx) {
    private val cols = 22
    private val rows = 16
    private val ink = Paint().apply { color = Lcd.ink(ctx); style = Paint.Style.FILL }
    private val textPaint = Paint().apply {
        color = Lcd.ink(ctx); textAlign = Paint.Align.CENTER; typeface = Typeface.MONOSPACE
        textSize = Lcd.dp(ctx, 14).toFloat(); isAntiAlias = true
    }
    private val handler = Handler(Looper.getMainLooper())
    private val snake = ArrayDeque<Pair<Int, Int>>()
    private var dir = Pair(1, 0)
    private var nextDir = Pair(1, 0)
    private var food = Pair(5, 5)
    var score = 0
        private set
    var highScore = 0
    var over = false
        private set
    var paused = false
        private set
    private var loopRunning = false
    var onGameOver: ((Int) -> Unit)? = null

    private val tick = object : Runnable {
        override fun run() {
            if (!loopRunning) return
            if (!paused && !over) step()
            handler.postDelayed(this, maxOf(90L, 220L - score * 6L))
        }
    }

    init { restart() }

    fun restart() {
        snake.clear()
        snake.addLast(Pair(3, rows / 2)); snake.addLast(Pair(2, rows / 2)); snake.addLast(Pair(1, rows / 2))
        dir = Pair(1, 0); nextDir = dir
        score = 0; over = false; paused = false
        placeFood()
        onScore()
        invalidate()
    }

    fun togglePause() { paused = !paused; invalidate() }

    fun resumeLoop() {
        if (loopRunning) return
        loopRunning = true
        handler.post(tick)
    }

    fun pauseLoop() {
        loopRunning = false
        handler.removeCallbacks(tick)
    }

    fun turn(dx: Int, dy: Int) {
        if (over) return
        if (dx == -dir.first && dy == -dir.second) return // no 180° turns
        nextDir = Pair(dx, dy)
        paused = false
    }

    private fun placeFood() {
        do {
            food = Pair(Random.nextInt(cols), Random.nextInt(rows))
        } while (snake.contains(food))
    }

    private fun step() {
        dir = nextDir
        val head = snake.first()
        val nh = Pair((head.first + dir.first + cols) % cols, (head.second + dir.second + rows) % rows)
        if (snake.contains(nh)) {
            over = true
            onGameOver?.invoke(score)
            onScore()
            invalidate()
            return
        }
        snake.addFirst(nh)
        if (nh == food) {
            score++
            placeFood()
            onScore()
        } else {
            snake.removeLast()
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cell = minOf(width / cols.toFloat(), height / rows.toFloat())
        val ox = (width - cell * cols) / 2f
        val oy = (height - cell * rows) / 2f
        // border
        ink.style = Paint.Style.STROKE; ink.strokeWidth = 2f
        canvas.drawRect(ox, oy, ox + cell * cols, oy + cell * rows, ink)
        ink.style = Paint.Style.FILL
        val gap = maxOf(1f, cell * 0.12f)
        for ((x, y) in snake) {
            canvas.drawRect(ox + x * cell + gap, oy + y * cell + gap, ox + (x + 1) * cell - gap, oy + (y + 1) * cell - gap, ink)
        }
        val fx = ox + food.first * cell + cell / 2f
        val fy = oy + food.second * cell + cell / 2f
        canvas.drawCircle(fx, fy, cell * 0.3f, ink)
        if (over || paused) {
            val cx = width / 2f
            val cy = height / 2f
            val bw = cell * 12; val bh = cell * 4.5f
            canvas.drawRect(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2, ink)
            textPaint.color = Lcd.paper(context)
            val l1 = if (over) "GAME OVER" else "PAUSED"
            val l2 = if (over) "Score $score · Best $highScore" else "OK to resume"
            canvas.drawText(l1, cx, cy - textPaint.textSize * 0.3f, textPaint)
            canvas.drawText(l2, cx, cy + textPaint.textSize * 1.1f, textPaint)
            textPaint.color = Lcd.ink(context)
        }
    }
}
