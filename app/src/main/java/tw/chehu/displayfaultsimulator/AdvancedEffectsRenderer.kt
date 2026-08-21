package tw.chehu.displayfaultsimulator

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** Draws panel-level faults that do not require access to the underlying app pixels. */
object AdvancedEffectsRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun draw(
        canvas: Canvas,
        scene: DamageScene,
        density: Float,
        elapsedMs: Long,
        eventPulse: Boolean,
        entryProgress: Float
    ) {
        val effects = scene.effects
        val dynamics = scene.dynamics
        val expansion = if (dynamics.expandingDamage) (0.22f + elapsedMs / 14_000f).coerceIn(0.22f, 1f) else 1f
        val timelinePhase = if (dynamics.timelineEnabled) ((elapsedMs / 3_500L) % 4L).toInt() else 3
        val cycleGroup = if (dynamics.cycleEffects) ((elapsedMs / (dynamics.cycleSeconds * 1_000L)) % 3L).toInt() else -1
        fun visible(group: Int) = timelinePhase >= group && (cycleGroup < 0 || cycleGroup == group % 3)

        if (visible(0)) {
            if (effects.unevenBrightness) drawUnevenBrightness(canvas, effects.unevenBrightnessStrength, entryProgress)
            if (effects.colorShift) drawColorShift(canvas, effects.colorShiftStrength, entryProgress)
            if (effects.edgeBleed) drawEdgeBleed(canvas, effects.edgeBleedStrength, entryProgress)
            if (effects.oledBlackSpot) drawOledBlackSpot(canvas, scene.id.hashCode(), effects.oledBlackSpotStrength, expansion, entryProgress)
            if (effects.pressureSpots) drawPressureSpots(canvas, effects.pressureSpotStrength, density, entryProgress)
        }
        if (visible(1)) {
            if (effects.partialBlackout) drawPartialBlackout(canvas, scene.id.hashCode(), effects.blackoutStrength, entryProgress)
            if (effects.screenTearing) drawScreenTearing(canvas, scene.id.hashCode(), effects.tearingStrength, density, elapsedMs, entryProgress)
            if (effects.cableJump) drawCableJump(canvas, effects.cableJumpStrength, density, elapsedMs, entryProgress)
        }
        if (visible(2)) {
            if (effects.pwmBands) drawPwmBands(canvas, effects.pwmStrength, density, elapsedMs, entryProgress)
            if (effects.intermittentFlash) drawIntermittentFlash(canvas, scene.id.hashCode(), effects.flashStrength, elapsedMs, entryProgress)
        }
        if (dynamics.randomFaults) drawRandomFault(canvas, scene.id.hashCode(), density, elapsedMs, entryProgress)
        if (eventPulse || (dynamics.impactFlash && elapsedMs < 520L)) drawImpactFlash(canvas, elapsedMs, eventPulse)
        paint.shader = null
        paint.alpha = 255
    }

    private fun drawOledBlackSpot(canvas: Canvas, seed: Int, strength: Int, expansion: Float, entry: Float) {
        val random = Random(seed xor 0x73A5C91)
        repeat(3) { index ->
            val cx = canvas.width * (if (index == 0) 0.82f else 0.08f + random.nextFloat() * 0.84f)
            val cy = canvas.height * (if (index == 0) 0.76f else 0.12f + random.nextFloat() * 0.76f)
            val radius = canvas.width * (0.10f + strength / 330f) * expansion * (0.55f + random.nextFloat() * 0.55f)
            paint.shader = RadialGradient(
                cx, cy, radius,
                intArrayOf(
                    Color.argb((235 * entry).toInt(), 0, 0, 0),
                    Color.argb((185 * entry).toInt(), 1, 1, 3),
                    Color.argb((70 * entry).toInt(), 48, 0, 58),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.55f, 0.82f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, radius, paint)
        }
    }

    private fun drawEdgeBleed(canvas: Canvas, strength: Int, entry: Float) {
        val alpha = (strength * 1.55f * entry).toInt().coerceIn(0, 155)
        val edge = canvas.width * (0.08f + strength / 240f)
        paint.shader = LinearGradient(0f, 0f, edge, 0f, intArrayOf(Color.argb(alpha, 15, 255, 72), Color.argb(alpha / 2, 170, 18, 210), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, edge, canvas.height.toFloat(), paint)
        paint.shader = LinearGradient(canvas.width.toFloat(), 0f, canvas.width - edge, 0f, intArrayOf(Color.argb(alpha, 180, 28, 220), Color.argb(alpha / 2, 18, 245, 86), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawRect(canvas.width - edge, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paint)
    }

    private fun drawUnevenBrightness(canvas: Canvas, strength: Int, entry: Float) {
        val radius = max(canvas.width, canvas.height) * 0.76f
        paint.shader = RadialGradient(
            canvas.width * 0.46f, canvas.height * 0.42f, radius,
            intArrayOf(Color.argb((strength * 0.22f * entry).toInt(), 255, 255, 240), Color.TRANSPARENT, Color.argb((strength * 1.15f * entry).toInt().coerceAtMost(150), 0, 0, 5)),
            floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paint)
    }

    private fun drawColorShift(canvas: Canvas, strength: Int, entry: Float) {
        val alpha = (strength * 0.72f * entry).toInt().coerceAtMost(72)
        paint.shader = LinearGradient(
            0f, canvas.height.toFloat(), canvas.width.toFloat(), 0f,
            intArrayOf(Color.argb(alpha, 255, 20, 50), Color.argb(alpha, 255, 220, 30), Color.argb(alpha, 20, 255, 120), Color.argb(alpha, 20, 120, 255), Color.argb(alpha, 210, 30, 255)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paint)
    }

    private fun drawPressureSpots(canvas: Canvas, strength: Int, density: Float, entry: Float) {
        val spots = listOf(0.28f to 0.36f, 0.68f to 0.58f, 0.43f to 0.82f)
        spots.forEachIndexed { index, (x, y) ->
            val radius = density * (32f + strength * (0.45f + index * 0.08f))
            paint.shader = RadialGradient(
                canvas.width * x, canvas.height * y, radius,
                intArrayOf(Color.argb((strength * 0.72f * entry).toInt(), 245, 252, 255), Color.argb((strength * 0.38f * entry).toInt(), 80, 150, 255), Color.TRANSPARENT),
                floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawCircle(canvas.width * x, canvas.height * y, radius, paint)
        }
    }

    private fun drawScreenTearing(canvas: Canvas, seed: Int, strength: Int, density: Float, elapsedMs: Long, entry: Float) {
        val random = Random(seed xor (elapsedMs / 180L).toInt())
        paint.shader = null
        repeat(3 + strength / 22) {
            val y = random.nextFloat() * canvas.height
            val height = density * (1f + random.nextFloat() * (3f + strength / 12f))
            val offset = density * (4f + random.nextFloat() * strength / 2f)
            paint.color = Color.argb((strength * 0.75f * entry).toInt().coerceAtMost(95), if (it % 2 == 0) 0 else 255, 210, if (it % 2 == 0) 255 else 30)
            canvas.drawRect(offset, y, canvas.width.toFloat(), y + height, paint)
            paint.color = Color.argb((strength * 0.48f * entry).toInt().coerceAtMost(65), 0, 0, 0)
            canvas.drawRect(0f, y + height, canvas.width - offset, y + height + density, paint)
        }
    }

    private fun drawPartialBlackout(canvas: Canvas, seed: Int, strength: Int, entry: Float) {
        paint.shader = null
        paint.color = Color.argb((strength * 2.15f * entry).toInt().coerceAtMost(235), 0, 0, 2)
        if (seed and 1 == 0) canvas.drawRect(0f, 0f, canvas.width * 0.52f, canvas.height.toFloat(), paint)
        else canvas.drawRect(0f, canvas.height * 0.48f, canvas.width.toFloat(), canvas.height.toFloat(), paint)
    }

    private fun drawIntermittentFlash(canvas: Canvas, seed: Int, strength: Int, elapsedMs: Long, entry: Float) {
        val bucket = elapsedMs / 110L
        val random = Random(seed xor bucket.toInt())
        if (random.nextFloat() > 0.16f + strength / 420f) return
        val green = random.nextBoolean()
        paint.shader = null
        paint.color = Color.argb((strength * 1.25f * entry).toInt().coerceAtMost(150), if (green) 95 else 255, 255, if (green) 115 else 255)
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paint)
    }

    private fun drawPwmBands(canvas: Canvas, strength: Int, density: Float, elapsedMs: Long, entry: Float) {
        paint.shader = null
        paint.color = Color.argb((8 + strength * 0.72f * entry).toInt().coerceAtMost(82), 0, 0, 0)
        val spacing = density * (3f + (100 - strength) / 18f)
        val offset = (elapsedMs / 12f % spacing).toFloat()
        var y = -spacing + offset
        while (y < canvas.height) {
            canvas.drawRect(0f, y, canvas.width.toFloat(), y + density * (0.8f + strength / 90f), paint)
            y += spacing
        }
    }

    private fun drawCableJump(canvas: Canvas, strength: Int, density: Float, elapsedMs: Long, entry: Float) {
        val phase = sin(elapsedMs / 82.0).toFloat()
        val y = canvas.height * (0.5f + phase * 0.38f)
        val height = density * (4f + strength / 4f)
        paint.shader = LinearGradient(0f, y, canvas.width.toFloat(), y, intArrayOf(Color.argb((strength * entry).toInt(), 255, 30, 170), Color.argb((strength * entry).toInt(), 20, 230, 255), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, y, canvas.width.toFloat(), y + height, paint)
        paint.shader = null
        paint.color = Color.argb((strength * 0.55f * entry).toInt(), 0, 0, 0)
        canvas.drawRect(0f, y + height, canvas.width.toFloat(), y + height + density * 2f, paint)
    }

    private fun drawRandomFault(canvas: Canvas, seed: Int, density: Float, elapsedMs: Long, entry: Float) {
        val random = Random(seed xor (elapsedMs / 650L).toInt())
        paint.shader = null
        repeat(2 + random.nextInt(4)) {
            val left = random.nextFloat() * canvas.width
            val top = random.nextFloat() * canvas.height
            paint.color = Color.argb((25 + random.nextInt(60) * entry).toInt(), random.nextInt(256), random.nextInt(256), random.nextInt(256))
            canvas.drawRect(left, top, (left + density * random.nextInt(8, 90)).coerceAtMost(canvas.width.toFloat()), top + density * random.nextInt(1, 9), paint)
        }
    }

    private fun drawImpactFlash(canvas: Canvas, elapsedMs: Long, eventPulse: Boolean) {
        val local = if (eventPulse) (elapsedMs % 1_600L).coerceAtMost(520L) else elapsedMs.coerceAtMost(520L)
        val fade = (1f - local / 520f).coerceIn(0f, 1f)
        paint.shader = RadialGradient(
            canvas.width * 0.55f, canvas.height * 0.42f, max(canvas.width, canvas.height) * 0.72f,
            intArrayOf(Color.argb((145 * fade).toInt(), 255, 255, 255), Color.argb((45 * fade).toInt(), 170, 220, 255), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paint)
    }
}
