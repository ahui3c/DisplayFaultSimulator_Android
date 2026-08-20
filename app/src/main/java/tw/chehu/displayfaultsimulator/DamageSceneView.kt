package tw.chehu.displayfaultsimulator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

class DamageSceneView(context: Context) : View(context) {
    var scene: DamageScene = DamageScene.classic()
        private set
    var movementOffsetPx: Int = 0
        set(value) {
            field = value
            invalidate()
        }
    var editorMode: Boolean = false
    var selectedLineId: Long? = null
        private set
    var onSceneChanged: ((DamageScene) -> Unit)? = null
    var onSelectionChanged: ((Long) -> Unit)? = null

    private var flickerFactor = 1f
    private val flickerRunnable = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow || scene.lines.none { it.flicker }) return
            flickerFactor = Random.nextFloat().coerceIn(0.08f, 1f)
            invalidate()
            postDelayed(this, Random.nextLong(65L, 190L))
        }
    }

    fun updateScene(value: DamageScene, keepSelection: Boolean = true) {
        scene = value
        if (!keepSelection || scene.lines.none { it.id == selectedLineId }) {
            selectedLineId = scene.lines.firstOrNull()?.id
        }
        restartFlicker()
        invalidate()
    }

    fun selectLine(id: Long) {
        if (scene.lines.any { it.id == id }) {
            selectedLineId = id
            onSelectionChanged?.invoke(id)
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        restartFlicker()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(flickerRunnable)
        super.onDetachedFromWindow()
    }

    private fun restartFlicker() {
        removeCallbacks(flickerRunnable)
        flickerFactor = 1f
        if (isAttachedToWindow && scene.lines.any { it.flicker }) post(flickerRunnable)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        SceneRenderer.draw(
            canvas = canvas,
            scene = scene,
            density = resources.displayMetrics.density,
            movementOffsetPx = movementOffsetPx,
            flickerFactor = flickerFactor,
            selectedLineId = if (editorMode) selectedLineId else null
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!editorMode || scene.lines.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val nearest = scene.lines.minByOrNull { abs(event.x - width * it.positionPercent / 100f) } ?: return false
                val distance = abs(event.x - width * nearest.positionPercent / 100f)
                if (distance > max(dp(34f), dp(nearest.widthDp.toFloat()) * 2f)) return false
                selectLine(nearest.id)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val id = selectedLineId ?: return false
                val percent = (event.x / width.coerceAtLeast(1) * 100f).coerceIn(0f, 100f)
                scene = scene.copy(lines = scene.lines.map { if (it.id == id) it.copy(positionPercent = percent) else it })
                onSceneChanged?.invoke(scene)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
}

object SceneRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    fun draw(
        canvas: Canvas,
        scene: DamageScene,
        density: Float,
        movementOffsetPx: Int,
        flickerFactor: Float,
        selectedLineId: Long?
    ) {
        drawLiquidDamage(canvas, scene.effects, density)
        drawCrackedScreen(canvas, scene, density)
        drawGhosting(canvas, scene.effects, density)
        drawScanlines(canvas, scene.effects, density)
        drawDeadPixels(canvas, scene, density)
        drawLines(canvas, scene, density, movementOffsetPx, flickerFactor, selectedLineId)
        paint.shader = null
    }

    private fun drawCrackedScreen(canvas: Canvas, scene: DamageScene, density: Float) {
        val effects = scene.effects
        if (!effects.crackedScreen) return
        val strength = effects.crackStrength.coerceIn(0, 100)
        drawCrackImpact(
            canvas,
            canvas.width * 0.72f,
            canvas.height * 0.29f,
            strength,
            density,
            scene.id.hashCode()
        )
        if (strength >= 62) {
            drawCrackImpact(
                canvas,
                canvas.width * 0.23f,
                canvas.height * 0.68f,
                (strength * 0.68f).toInt(),
                density,
                scene.id.hashCode() xor 0x5F3759DF
            )
        }
    }

    private fun drawCrackImpact(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        strength: Int,
        density: Float,
        seed: Int
    ) {
        val random = Random(seed)
        val rayCount = 9 + strength / 6
        val maxLength = max(canvas.width, canvas.height) * (0.22f + strength / 155f)
        val paths = mutableListOf<Path>()

        repeat(rayCount) { ray ->
            val baseAngle = (Math.PI * 2.0 * ray / rayCount).toFloat() + (random.nextFloat() - 0.5f) * 0.32f
            val length = maxLength * (0.46f + random.nextFloat() * 0.58f)
            val segments = 4 + random.nextInt(4)
            val path = Path().apply { moveTo(centerX, centerY) }
            for (segment in 1..segments) {
                val distance = length * segment / segments
                val sideJitter = (random.nextFloat() - 0.5f) * length * 0.075f
                val x = centerX + cos(baseAngle) * distance - sin(baseAngle) * sideJitter
                val y = centerY + sin(baseAngle) * distance + cos(baseAngle) * sideJitter
                path.lineTo(x, y)
            }
            paths += path
        }

        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = Color.argb((95 + strength).coerceAtMost(190), 0, 0, 0)
        paint.strokeWidth = density * (1.7f + strength / 65f)
        paths.forEach { canvas.drawPath(it, paint) }

        paint.color = Color.argb((105 + strength).coerceAtMost(205), 235, 245, 250)
        paint.strokeWidth = density * 0.72f
        paths.forEach { canvas.drawPath(it, paint) }

        repeat(4 + strength / 28) { ring ->
            val radius = density * (13f + ring * 16f + random.nextFloat() * 7f)
            rect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
            paint.color = Color.argb((75 + strength / 2).coerceAtMost(145), 225, 238, 244)
            paint.strokeWidth = density * 0.65f
            val start = random.nextFloat() * 260f
            canvas.drawArc(rect, start, 58f + random.nextFloat() * 105f, false, paint)
            canvas.drawArc(rect, start + 185f, 35f + random.nextFloat() * 75f, false, paint)
        }

        paint.style = Paint.Style.FILL
        paint.color = Color.argb((110 + strength).coerceAtMost(210), 5, 5, 8)
        canvas.drawCircle(centerX, centerY, density * (2.5f + strength / 30f), paint)
    }

    private fun drawLines(
        canvas: Canvas,
        scene: DamageScene,
        density: Float,
        movementOffsetPx: Int,
        flickerFactor: Float,
        selectedLineId: Long?
    ) {
        scene.lines.forEach { line ->
            val width = max(1f, line.widthDp * density)
            val center = (canvas.width - width) * line.positionPercent / 100f + movementOffsetPx
            val left = center.coerceIn(0f, (canvas.width - width).coerceAtLeast(0f))
            val right = (left + width).coerceAtMost(canvas.width.toFloat())
            val flicker = if (line.flicker) {
                val reduction = line.flickerStrength / 100f
                (1f - reduction) + flickerFactor * reduction
            } else 1f
            val alpha = (255 * line.opacityPercent / 100f * flicker).toInt().coerceIn(0, 255)

            if (line.glowDp > 0) {
                val glow = line.glowDp * density
                val glowColor = Color.argb((alpha * 0.48f).toInt(), Color.red(line.color), Color.green(line.color), Color.blue(line.color))
                paint.shader = LinearGradient(
                    left - glow,
                    0f,
                    right + glow,
                    0f,
                    intArrayOf(Color.TRANSPARENT, glowColor, glowColor, Color.TRANSPARENT),
                    floatArrayOf(0f, 0.46f, 0.54f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(left - glow, 0f, right + glow, canvas.height.toFloat(), paint)
                paint.shader = null
            }

            paint.color = line.color
            paint.alpha = alpha
            paint.style = Paint.Style.FILL
            canvas.drawRect(left, 0f, right, canvas.height.toFloat(), paint)

            if (selectedLineId == line.id) {
                paint.color = Color.WHITE
                paint.alpha = 235
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f * density
                canvas.drawRect(left - 5f * density, 1f, right + 5f * density, canvas.height - 1f, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle((left + right) / 2f, 22f * density, 8f * density, paint)
            }
        }
    }

    private fun drawDeadPixels(canvas: Canvas, scene: DamageScene, density: Float) {
        val effects = scene.effects
        if (!effects.deadPixels) return
        val random = Random(scene.id.hashCode())
        val count = 8 + effects.deadPixelStrength * 2
        repeat(count) {
            val x = random.nextFloat() * canvas.width
            val y = random.nextFloat() * canvas.height
            val size = density * (if (random.nextFloat() < 0.82f) 1f else random.nextInt(2, 5).toFloat())
            paint.shader = null
            paint.style = Paint.Style.FILL
            paint.alpha = 170 + random.nextInt(86)
            paint.color = when (random.nextInt(7)) {
                0 -> Color.RED
                1 -> Color.GREEN
                2 -> Color.BLUE
                3 -> Color.WHITE
                else -> Color.BLACK
            }
            canvas.drawRect(x, y, x + size, y + size, paint)
        }
    }

    private fun drawLiquidDamage(canvas: Canvas, effects: DamageEffects, density: Float) {
        if (!effects.liquidDamage) return
        val strength = effects.liquidStrength / 100f
        val blobs = listOf(
            floatArrayOf(0.12f, 0.82f, 0.32f),
            floatArrayOf(0.88f, 0.16f, 0.24f),
            floatArrayOf(0.74f, 0.70f, 0.18f)
        )
        blobs.forEachIndexed { index, blob ->
            val cx = blob[0] * canvas.width
            val cy = blob[1] * canvas.height
            val radius = blob[2] * canvas.width * (0.7f + strength * 0.55f)
            val core = if (index == 1) Color.argb((150 * strength).toInt(), 30, 0, 55) else Color.argb((190 * strength).toInt(), 0, 0, 0)
            paint.shader = RadialGradient(
                cx,
                cy,
                radius,
                intArrayOf(core, Color.argb((100 * strength).toInt(), 45, 10, 65), Color.TRANSPARENT),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP
            )
            paint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, radius, paint)
        }
        paint.shader = null
        paint.color = Color.argb((70 * strength).toInt(), 110, 20, 130)
        canvas.drawRect(0f, canvas.height - 5f * density, canvas.width.toFloat(), canvas.height.toFloat(), paint)
    }

    private fun drawGhosting(canvas: Canvas, effects: DamageEffects, density: Float) {
        if (!effects.ghosting) return
        val alpha = (effects.ghostStrength * 0.72f).toInt().coerceIn(8, 72)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        paint.color = Color.argb(alpha, 225, 235, 230)
        rect.set(canvas.width * 0.12f, canvas.height * 0.20f, canvas.width * 0.88f, canvas.height * 0.34f)
        canvas.drawRoundRect(rect, 18f * density, 18f * density, paint)
        rect.set(canvas.width * 0.18f, canvas.height * 0.47f, canvas.width * 0.82f, canvas.height * 0.54f)
        canvas.drawRoundRect(rect, 10f * density, 10f * density, paint)
        rect.set(canvas.width * 0.18f, canvas.height * 0.59f, canvas.width * 0.68f, canvas.height * 0.65f)
        canvas.drawRoundRect(rect, 10f * density, 10f * density, paint)
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 38f * density
        paint.color = Color.argb((alpha * 0.8f).toInt(), 220, 230, 225)
        canvas.drawText("12:48", canvas.width / 2f, canvas.height * 0.30f, paint)
    }

    private fun drawScanlines(canvas: Canvas, effects: DamageEffects, density: Float) {
        if (!effects.scanlines) return
        val strength = effects.scanlineStrength.coerceIn(0, 100)
        val spacing = (10f - strength / 16f).coerceAtLeast(3f) * density
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Color.argb((12 + strength * 0.6f).toInt(), 0, 0, 0)
        var y = 0f
        while (y < canvas.height) {
            canvas.drawRect(0f, y, canvas.width.toFloat(), y + max(1f, density * 0.7f), paint)
            y += spacing
        }
    }
}
