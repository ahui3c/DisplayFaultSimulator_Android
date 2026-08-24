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
import android.os.SystemClock
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
    var selectedCrackId: Long? = null
        private set
    var onSceneChanged: ((DamageScene) -> Unit)? = null
    var onSelectionChanged: ((Long) -> Unit)? = null
    var onCrackSelectionChanged: ((Long) -> Unit)? = null
    var parallaxX: Float = 0f
        set(value) { field = value; invalidate() }
    var parallaxY: Float = 0f
        set(value) { field = value; invalidate() }

    private var flickerFactor = 1f
    private var sceneStartedAt = SystemClock.elapsedRealtime()
    private var eventPulseUntil = 0L
    private var dragTarget = DragTarget.NONE
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
        sceneStartedAt = SystemClock.elapsedRealtime()
        if (!keepSelection || scene.lines.none { it.id == selectedLineId }) {
            selectedLineId = scene.lines.firstOrNull()?.id
        }
        val impacts = resolvedCrackImpacts(scene)
        if (!keepSelection || impacts.none { it.id == selectedCrackId }) selectedCrackId = impacts.firstOrNull()?.id
        restartFlicker()
        restartAnimation()
        invalidate()
    }

    fun selectLine(id: Long) {
        if (scene.lines.any { it.id == id }) {
            selectedLineId = id
            onSelectionChanged?.invoke(id)
            invalidate()
        }
    }
    private val animationRunnable = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow || !needsAnimation()) return
            invalidate()
            postDelayed(this, 50L)
        }
    }

    fun selectCrack(id: Long) {
        if (resolvedCrackImpacts(scene).any { it.id == id }) {
            selectedCrackId = id
            onCrackSelectionChanged?.invoke(id)
            invalidate()
        }
    }

    fun triggerEventPulse(durationMs: Long = 1_600L) {
        eventPulseUntil = SystemClock.elapsedRealtime() + durationMs
        restartAnimation()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        restartFlicker()
        restartAnimation()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(flickerRunnable)
        removeCallbacks(animationRunnable)
        super.onDetachedFromWindow()
    }

    private fun restartFlicker() {
        removeCallbacks(flickerRunnable)
        flickerFactor = 1f
        if (isAttachedToWindow && scene.lines.any { it.flicker }) post(flickerRunnable)
    }

    private fun restartAnimation() {
        removeCallbacks(animationRunnable)
        if (isAttachedToWindow && needsAnimation()) post(animationRunnable)
    }

    private fun needsAnimation(): Boolean {
        val d = scene.dynamics
        val e = scene.effects
        return d.animatedEntry || d.impactFlash || d.expandingDamage || d.unstableLines ||
            d.timelineEnabled || d.randomFaults || d.cycleEffects || e.intermittentFlash ||
            e.screenTearing || e.cableJump || e.pwmBands || eventPulseUntil > SystemClock.elapsedRealtime()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        SceneRenderer.draw(
            canvas = canvas,
            scene = scene,
            density = resources.displayMetrics.density,
            movementOffsetPx = movementOffsetPx,
            flickerFactor = flickerFactor,
            selectedLineId = if (editorMode) selectedLineId else null,
            selectedCrackId = if (editorMode) selectedCrackId else null,
            elapsedMs = SystemClock.elapsedRealtime() - sceneStartedAt,
            eventPulse = eventPulseUntil > SystemClock.elapsedRealtime(),
            parallaxX = parallaxX,
            parallaxY = parallaxY
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!editorMode) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val crack = resolvedCrackImpacts(scene).minByOrNull {
                    val dx = event.x - width * it.xPercent / 100f
                    val dy = event.y - height * it.yPercent / 100f
                    dx * dx + dy * dy
                }
                if (crack != null) {
                    val dx = event.x - width * crack.xPercent / 100f
                    val dy = event.y - height * crack.yPercent / 100f
                    if (dx * dx + dy * dy <= dp(38f) * dp(38f)) {
                        selectCrack(crack.id)
                        dragTarget = DragTarget.CRACK
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
                val nearest = scene.lines.minByOrNull { abs(event.x - width * it.positionPercent / 100f) } ?: return false
                val distance = abs(event.x - width * nearest.positionPercent / 100f)
                if (distance > max(dp(34f), dp(nearest.widthDp.toFloat()) * 2f)) return false
                selectLine(nearest.id)
                dragTarget = DragTarget.LINE
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                when (dragTarget) {
                    DragTarget.LINE -> {
                        val id = selectedLineId ?: return false
                        val percent = (event.x / width.coerceAtLeast(1) * 100f).coerceIn(0f, 100f)
                        scene = scene.copy(lines = scene.lines.map { if (it.id == id) it.copy(positionPercent = percent) else it })
                    }
                    DragTarget.CRACK -> {
                        val id = selectedCrackId ?: return false
                        val x = (event.x / width.coerceAtLeast(1) * 100f).coerceIn(0f, 100f)
                        val y = (event.y / height.coerceAtLeast(1) * 100f).coerceIn(0f, 100f)
                        val impacts = resolvedCrackImpacts(scene).map { if (it.id == id) it.copy(xPercent = x, yPercent = y) else it }
                        scene = scene.copy(effects = scene.effects.copy(crackImpacts = impacts))
                    }
                    DragTarget.NONE -> return false
                }
                onSceneChanged?.invoke(scene)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                dragTarget = DragTarget.NONE
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
    private enum class DragTarget { NONE, LINE, CRACK }
}

fun resolvedCrackImpacts(scene: DamageScene): List<CrackImpact> = scene.effects.crackImpacts.ifEmpty {
    when (scene.effects.crackPattern) {
        CrackPattern.CORNER_SHATTER -> listOf(
            CrackImpact(-101L, 4f, 10f, branchCount = 17, lengthPercent = 78),
            CrackImpact(-102L, 96f, 84f, rotationDegrees = 180, branchCount = 12, lengthPercent = 58)
        )
        CrackPattern.HAIRLINE -> listOf(CrackImpact(-201L, 50f, 2f, branchCount = 7, lengthPercent = 94))
        CrackPattern.RADIAL_IMPACT -> listOf(CrackImpact(-301L, 54f, 43f, branchCount = 18, lengthPercent = 92))
        CrackPattern.SPIDERWEB -> listOf(CrackImpact(-401L, 72f, 29f, branchCount = 16, lengthPercent = 72))
    }
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
        selectedLineId: Long?,
        selectedCrackId: Long?,
        elapsedMs: Long,
        eventPulse: Boolean,
        parallaxX: Float,
        parallaxY: Float
    ) {
        val entryProgress = if (scene.dynamics.animatedEntry) (elapsedMs / 1_500f).coerceIn(0f, 1f) else 1f
        AdvancedEffectsRenderer.draw(canvas, scene, density, elapsedMs, eventPulse, entryProgress)
        drawLiquidDamage(canvas, scene.effects, density, if (scene.dynamics.expandingDamage) expansionProgress(elapsedMs) else 1f)
        drawCrackedScreen(canvas, scene, density, selectedCrackId, parallaxX, parallaxY, entryProgress)
        drawGhosting(canvas, scene.effects, density)
        drawScanlines(canvas, scene.effects, density)
        drawDeadPixels(canvas, scene, density)
        drawLines(canvas, scene, density, movementOffsetPx, flickerFactor, selectedLineId, elapsedMs)
        paint.shader = null
    }

    private fun expansionProgress(elapsedMs: Long): Float = (0.28f + (elapsedMs / 12_000f).coerceIn(0f, 0.72f))

    private fun drawCrackedScreen(
        canvas: Canvas,
        scene: DamageScene,
        density: Float,
        selectedCrackId: Long?,
        parallaxX: Float,
        parallaxY: Float,
        entryProgress: Float
    ) {
        val effects = scene.effects
        if (!effects.crackedScreen) return
        val strength = (effects.crackStrength.coerceIn(0, 100) * entryProgress).toInt()
        val opacity = (effects.crackOpacityPercent.coerceIn(10, 100) * entryProgress).toInt().coerceAtLeast(3)
        val impacts = resolvedCrackImpacts(scene)
        val offsetX = if (effects.crackParallax) parallaxX * 7f * density else 0f
        val offsetY = if (effects.crackParallax) parallaxY * 7f * density else 0f
        val save = canvas.save()
        applyCrackMask(canvas, effects.crackMask, impacts, density)
        if (effects.crackPattern == CrackPattern.HAIRLINE) {
            impacts.forEachIndexed { index, impact ->
                drawHairlineCracks(
                    canvas = canvas,
                    centerX = canvas.width * impact.xPercent / 100f + offsetX,
                    centerY = canvas.height * impact.yPercent / 100f + offsetY,
                    strength = strength,
                    opacityPercent = opacity,
                    density = density,
                    seed = scene.id.hashCode() xor impact.seedOffset xor index * 0x45D9F3B,
                    lengthScale = impact.lengthPercent / 72f,
                    branchCount = impact.branchCount,
                    rotationDegrees = impact.rotationDegrees
                )
            }
        } else impacts.forEachIndexed { index, impact ->
            drawCrackImpact(
                canvas = canvas,
                centerX = canvas.width * impact.xPercent / 100f + offsetX,
                centerY = canvas.height * impact.yPercent / 100f + offsetY,
                strength = strength,
                opacityPercent = opacity,
                density = density,
                seed = scene.id.hashCode() xor impact.seedOffset xor index * 0x45D9F3B,
                lengthScale = impact.lengthPercent / 72f,
                drawRings = effects.crackPattern == CrackPattern.SPIDERWEB || effects.crackPattern == CrackPattern.CORNER_SHATTER,
                branchCount = impact.branchCount,
                rotationDegrees = impact.rotationDegrees
            )
        }
        if (effects.edgeChips) drawEdgeChips(canvas, scene, impacts, density, opacity)
        if (effects.glassShards) drawPanelRuptureVeins(canvas, scene, impacts, density, opacity)
        if (effects.glassReflection) drawGlassReflection(canvas, impacts, density, opacity)
        canvas.restoreToCount(save)
        selectedCrackId?.let { id -> impacts.firstOrNull { it.id == id }?.let { drawCrackHandle(canvas, it, density) } }
    }

    private fun applyCrackMask(canvas: Canvas, mask: CrackMask, impacts: List<CrackImpact>, density: Float) {
        when (mask) {
            CrackMask.FULL_SCREEN -> Unit
            CrackMask.TOP_LEFT -> canvas.clipRect(0f, 0f, canvas.width * 0.62f, canvas.height * 0.58f)
            CrackMask.SCREEN_EDGES -> {
                val path = Path().apply {
                    fillType = Path.FillType.EVEN_ODD
                    addRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), Path.Direction.CW)
                    addRect(72f * density, 110f * density, canvas.width - 72f * density, canvas.height - 110f * density, Path.Direction.CW)
                }
                canvas.clipPath(path)
            }
            CrackMask.AROUND_IMPACTS -> {
                val path = Path()
                impacts.forEach { impact ->
                    path.addCircle(canvas.width * impact.xPercent / 100f, canvas.height * impact.yPercent / 100f, canvas.width * 0.34f, Path.Direction.CW)
                }
                canvas.clipPath(path)
            }
        }
    }
    private fun drawCrackImpact(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        strength: Int,
        opacityPercent: Int,
        density: Float,
        seed: Int,
        lengthScale: Float,
        drawRings: Boolean,
        branchCount: Int,
        rotationDegrees: Int
    ) {
        val random = Random(seed)
        val rayCount = branchCount.coerceIn(3, 28)
        val maxLength = max(canvas.width, canvas.height) * (0.16f + strength / 225f) * lengthScale
        val paths = mutableListOf<Path>()

        repeat(rayCount) { ray ->
            val baseAngle = Math.toRadians(rotationDegrees.toDouble()).toFloat() +
                (Math.PI * 2.0 * ray / rayCount).toFloat() + (random.nextFloat() - 0.5f) * 0.32f
            val length = maxLength * (0.46f + random.nextFloat() * 0.58f)
            val segments = 4 + random.nextInt(4)
            val path = Path().apply { moveTo(centerX, centerY) }
            var branchX = centerX
            var branchY = centerY
            for (segment in 1..segments) {
                val distance = length * segment / segments
                val sideJitter = (random.nextFloat() - 0.5f) * length * 0.065f
                val x = centerX + cos(baseAngle) * distance - sin(baseAngle) * sideJitter
                val y = centerY + sin(baseAngle) * distance + cos(baseAngle) * sideJitter
                path.lineTo(x, y)
                if (segment == segments / 2) {
                    branchX = x
                    branchY = y
                }
            }
            paths += path
            if (strength >= 38 && ray % 2 == 0) {
                val branchAngle = baseAngle + (if (random.nextBoolean()) 1f else -1f) * (0.42f + random.nextFloat() * 0.5f)
                val branchLength = length * (0.13f + random.nextFloat() * 0.18f)
                paths += Path().apply {
                    moveTo(branchX, branchY)
                    lineTo(
                        branchX + cos(branchAngle) * branchLength,
                        branchY + sin(branchAngle) * branchLength
                    )
                }
            }
        }

        val opacityScale = opacityPercent / 100f
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = Color.argb(((54 + strength * 0.48f) * opacityScale).toInt().coerceIn(8, 128), 8, 10, 12)
        paint.strokeWidth = density * (0.62f + strength / 190f)
        paths.forEach { canvas.drawPath(it, paint) }

        paint.color = Color.argb(((82 + strength * 0.62f) * opacityScale).toInt().coerceIn(10, 152), 238, 246, 248)
        paint.strokeWidth = density * (0.26f + strength / 520f)
        paths.forEach { canvas.drawPath(it, paint) }

        if (drawRings) {
            repeat(2 + strength / 32) { ring ->
                val radius = density * (11f + ring * 17f + random.nextFloat() * 8f)
                rect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
                paint.color = Color.argb(((58 + strength * 0.34f) * opacityScale).toInt().coerceIn(7, 110), 225, 238, 244)
                paint.strokeWidth = density * 0.34f
                val start = random.nextFloat() * 260f
                canvas.drawArc(rect, start, 45f + random.nextFloat() * 90f, false, paint)
                canvas.drawArc(rect, start + 185f, 30f + random.nextFloat() * 65f, false, paint)
            }
        }

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(((70 + strength * 0.55f) * opacityScale).toInt().coerceIn(8, 135), 5, 5, 8)
        canvas.drawCircle(centerX, centerY, density * (1.2f + strength / 55f), paint)
    }

    private fun drawEdgeChips(
        canvas: Canvas,
        scene: DamageScene,
        impacts: List<CrackImpact>,
        density: Float,
        opacity: Int
    ) {
        val random = Random(scene.id.hashCode() xor 0x2C1B3C6D)
        val averageX = impacts.map { it.xPercent }.average().takeUnless { it.isNaN() } ?: 50.0
        val focusLeft = averageX < 35.0
        val focusRight = averageX > 65.0
        val ruptureCount = if (focusLeft || focusRight) 7 else 5
        paint.style = Paint.Style.FILL
        repeat(ruptureCount) { index ->
            val onLeft = when {
                focusLeft -> true
                focusRight -> false
                else -> index % 2 == 0
            }
            val edgeX = if (onLeft) 0f else canvas.width.toFloat()
            val centerY = if (impacts.isNotEmpty()) {
                val impact = impacts[index % impacts.size]
                canvas.height * (
                    impact.yPercent / 100f + (random.nextFloat() - 0.5f) *
                        if (focusLeft || focusRight) 0.12f else 0.22f
                ).coerceIn(0.02f, 0.98f)
            } else {
                canvas.height * (0.08f + random.nextFloat() * 0.84f)
            }
            val halfHeight = density * (12f + random.nextFloat() * 34f)
            val depth = density * (12f + random.nextFloat() * 38f)

            val bleed = buildEdgeRupturePath(
                edgeX, centerY, halfHeight * 1.12f, depth * 1.28f,
                onLeft, canvas.height.toFloat(), random
            )
            paint.color = Color.argb((72 + opacity / 3).coerceAtMost(118), 2, 2, 4)
            canvas.drawPath(bleed, paint)

            val core = buildEdgeRupturePath(
                edgeX, centerY, halfHeight, depth,
                onLeft, canvas.height.toFloat(), random
            )
            paint.color = Color.argb((205 + opacity / 2).coerceAtMost(248), 0, 0, 1)
            canvas.drawPath(core, paint)
        }
    }

    private fun buildEdgeRupturePath(
        edgeX: Float,
        centerY: Float,
        halfHeight: Float,
        depth: Float,
        onLeft: Boolean,
        canvasHeight: Float,
        random: Random
    ): Path {
        val top = (centerY - halfHeight).coerceIn(0f, canvasHeight)
        val bottom = (centerY + halfHeight).coerceIn(0f, canvasHeight)
        val direction = if (onLeft) 1f else -1f
        val path = Path()
        path.moveTo(edgeX, top)
        var previousX = edgeX
        var previousY = top
        val segments = 7
        for (step in 1 until segments) {
            val progress = step.toFloat() / segments
            val y = top + (bottom - top) * progress
            val envelope = sin(Math.PI.toFloat() * progress).coerceAtLeast(0.12f)
            val unevenDepth = depth * envelope * (0.55f + random.nextFloat() * 0.75f)
            val x = edgeX + direction * unevenDepth
            val controlY = (previousY + y) * 0.5f
            path.cubicTo(
                previousX + direction * depth * (random.nextFloat() - 0.35f) * 0.18f,
                controlY - (random.nextFloat() - 0.5f) * halfHeight * 0.16f,
                x - direction * depth * random.nextFloat() * 0.14f,
                controlY + (random.nextFloat() - 0.5f) * halfHeight * 0.16f,
                x,
                y
            )
            previousX = x
            previousY = y
        }
        path.cubicTo(
            previousX + direction * depth * (random.nextFloat() - 0.5f) * 0.12f,
            (previousY + bottom) * 0.5f,
            edgeX + direction * depth * 0.08f,
            bottom,
            edgeX,
            bottom
        )
        path.close()
        return path
    }

    private fun drawPanelRuptureVeins(
        canvas: Canvas,
        scene: DamageScene,
        impacts: List<CrackImpact>,
        density: Float,
        opacity: Int
    ) {
        val random = Random(scene.id.hashCode() xor 0x6A09E667)
        val averageX = impacts.map { it.xPercent }.average().takeUnless { it.isNaN() } ?: 50.0
        val edgeFocused = averageX < 35.0 || averageX > 65.0
        val veinCount = if (edgeFocused) 18 else 12
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        repeat(veinCount) { index ->
            val impact = impacts.getOrNull(index % impacts.size.coerceAtLeast(1))
            val onLeft = when {
                edgeFocused -> averageX < 35.0
                impact != null -> impact.xPercent < 50f
                else -> index % 2 == 0
            }
            val direction = if (onLeft) 1f else -1f
            val edgeX = if (onLeft) 0f else canvas.width.toFloat()
            val sourceY = if (impact != null) {
                canvas.height * (impact.yPercent / 100f + (random.nextFloat() - 0.5f) * 0.12f)
            } else {
                canvas.height * (0.08f + random.nextFloat() * 0.84f)
            }.coerceIn(0f, canvas.height.toFloat())
            val reach = canvas.width * (0.035f + random.nextFloat() * if (edgeFocused) 0.16f else 0.1f)
            val verticalDrift = (random.nextFloat() - 0.5f) * canvas.height * 0.11f
            val endX = edgeX + direction * reach
            val endY = (sourceY + verticalDrift).coerceIn(0f, canvas.height.toFloat())
            val path = Path().apply {
                moveTo(edgeX, sourceY)
                cubicTo(
                    edgeX + direction * reach * (0.18f + random.nextFloat() * 0.12f),
                    sourceY + verticalDrift * 0.08f,
                    edgeX + direction * reach * (0.42f + random.nextFloat() * 0.18f),
                    sourceY + verticalDrift * (0.35f + random.nextFloat() * 0.2f),
                    endX,
                    endY
                )
            }
            paint.strokeWidth = density * (0.65f + random.nextFloat() * 2.35f)
            paint.color = Color.argb((168 + opacity / 2 + random.nextInt(28)).coerceAtMost(236), 0, 0, 2)
            canvas.drawPath(path, paint)
        }
        paint.strokeCap = Paint.Cap.BUTT
        paint.strokeJoin = Paint.Join.MITER
    }
    private fun drawGlassReflection(canvas: Canvas, impacts: List<CrackImpact>, density: Float, opacity: Int) {
        paint.shader = LinearGradient(
            0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(),
            intArrayOf(Color.TRANSPARENT, Color.argb((opacity * 0.18f).toInt(), 235, 248, 255), Color.TRANSPARENT),
            floatArrayOf(0.18f, 0.5f, 0.82f), Shader.TileMode.CLAMP
        )
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density * 4f
        impacts.forEach { impact ->
            val cx = canvas.width * impact.xPercent / 100f
            val cy = canvas.height * impact.yPercent / 100f
            canvas.drawArc(cx - 42f * density, cy - 42f * density, cx + 42f * density, cy + 42f * density, 205f, 70f, false, paint)
        }
        paint.shader = null
    }

    private fun drawCrackHandle(canvas: Canvas, impact: CrackImpact, density: Float) {
        val cx = canvas.width * impact.xPercent / 100f
        val cy = canvas.height * impact.yPercent / 100f
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(220, 8, 122, 54)
        canvas.drawCircle(cx, cy, density * 8f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density * 2f
        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, density * 12f, paint)
    }

    private fun drawHairlineCracks(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        strength: Int,
        opacityPercent: Int,
        density: Float,
        seed: Int,
        lengthScale: Float,
        branchCount: Int,
        rotationDegrees: Int
    ) {
        val random = Random(seed)
        val paths = mutableListOf<Path>()
        val rayCount = branchCount.coerceIn(3, 28)
        val maxLength = max(canvas.width, canvas.height) * (0.2f + strength / 150f) * lengthScale
        repeat(rayCount) { index ->
            val angle = Math.toRadians(rotationDegrees.toDouble()).toFloat() +
                (Math.PI * 2.0 * index / rayCount).toFloat() + (random.nextFloat() - 0.5f) * 0.38f
            val length = maxLength * (0.5f + random.nextFloat() * 0.5f)
            var x = centerX
            var y = centerY
            val path = Path().apply { moveTo(x, y) }
            val segmentCount = 7 + strength / 16
            repeat(segmentCount) { segment ->
                val step = length / segmentCount
                val lateralJitter = (random.nextFloat() - 0.5f) * step * 0.62f
                x += cos(angle) * step - sin(angle) * lateralJitter
                y += sin(angle) * step + cos(angle) * lateralJitter
                path.lineTo(x, y)
                if (segment > 1 && segment % 3 == 0) {
                    val branchLength = length * (0.06f + random.nextFloat() * 0.08f)
                    val branchAngle = angle + (if (random.nextBoolean()) 1f else -1f) *
                        (0.48f + random.nextFloat() * 0.42f)
                    paths += Path().apply {
                        moveTo(x, y)
                        lineTo(x + cos(branchAngle) * branchLength, y + sin(branchAngle) * branchLength)
                    }
                }
            }
            paths += path
        }
        val opacityScale = opacityPercent / 100f
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = Color.argb(((52 + strength * 0.4f) * opacityScale).toInt().coerceIn(7, 105), 5, 7, 8)
        paint.strokeWidth = density * 0.68f
        paths.forEach { canvas.drawPath(it, paint) }
        paint.color = Color.argb(((75 + strength * 0.5f) * opacityScale).toInt().coerceIn(9, 135), 240, 247, 249)
        paint.strokeWidth = density * 0.28f
        paths.forEach { canvas.drawPath(it, paint) }
    }

    private fun drawLines(
        canvas: Canvas,
        scene: DamageScene,
        density: Float,
        movementOffsetPx: Int,
        flickerFactor: Float,
        selectedLineId: Long?,
        elapsedMs: Long
    ) {
        scene.lines.forEach { line ->
            val unstable = scene.dynamics.unstableLines
            val random = Random(scene.id.hashCode() xor line.id.toInt() xor (elapsedMs / 320L).toInt())
            if (unstable && random.nextFloat() < 0.16f) return@forEach
            if (scene.dynamics.cycleEffects && ((elapsedMs / (scene.dynamics.cycleSeconds * 1_000L)) % 3L) == 1L) return@forEach
            val width = max(1f, line.widthDp * density)
            val jump = if (unstable) (random.nextFloat() - 0.5f) * density * 9f else 0f
            val center = (canvas.width - width) * line.positionPercent / 100f + movementOffsetPx + jump
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

            paint.color = if (unstable && random.nextFloat() < 0.22f) {
                when (random.nextInt(3)) { 0 -> Color.CYAN; 1 -> Color.MAGENTA; else -> Color.WHITE }
            } else line.color
            paint.alpha = alpha
            paint.style = Paint.Style.FILL
            canvas.drawRect(left, 0f, right, canvas.height.toFloat(), paint)
            if (unstable && random.nextFloat() < 0.30f) {
                val split = density * (2f + random.nextFloat() * 5f)
                paint.alpha = (alpha * 0.58f).toInt()
                canvas.drawRect((left + split).coerceAtMost(canvas.width.toFloat()), 0f, (right + split).coerceAtMost(canvas.width.toFloat()), canvas.height.toFloat(), paint)
            }

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

    private fun drawLiquidDamage(canvas: Canvas, effects: DamageEffects, density: Float, expansion: Float) {
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
            val radius = blob[2] * canvas.width * (0.7f + strength * 0.55f) * expansion
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
