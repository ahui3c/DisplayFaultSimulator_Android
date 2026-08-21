package tw.chehu.displayfaultsimulator

import android.content.Context
import android.graphics.Color

object ScenePresets {
    fun all(context: Context): List<DamageScene> =
        listOf(
            DamageScene.classic("preset-classic-oled", context.getString(R.string.preset_classic_oled)),
            DamageScene(
                id = "preset-pink-lines",
                name = context.getString(R.string.preset_pink_lines),
                lines = listOf(
                    line(15f, 3, Color.rgb(255, 62, 190), glow = 4),
                    line(43f, 2, Color.rgb(255, 120, 220), opacity = 88, glow = 3),
                    line(78f, 5, Color.rgb(235, 40, 175), glow = 6)
                )
            ),
            DamageScene(
                id = "preset-impact-crack",
                name = context.getString(R.string.preset_impact_crack),
                lines = listOf(line(72f, 2, Color.WHITE, opacity = 48, glow = 1)),
                effects = DamageEffects(
                    crackedScreen = true,
                    crackStrength = 88,
                    crackPattern = CrackPattern.RADIAL_IMPACT,
                    crackOpacityPercent = 38,
                    crackImpacts = listOf(
                        impact(54f, 43f, 18, 92),
                        impact(18f, 76f, 11, 55, rotation = 42)
                    ),
                    edgeChips = true,
                    glassShards = true,
                    glassReflection = true,
                    crackParallax = true,
                    deadPixels = true,
                    deadPixelStrength = 22,
                    liquidDamage = true,
                    liquidStrength = 28
                ),
                dynamics = SceneDynamics(animatedEntry = true, impactFlash = true, shakeTrigger = true),
                movementEnabled = false
            ),
            DamageScene(
                id = "preset-liquid",
                name = context.getString(R.string.preset_liquid),
                lines = listOf(line(83f, 3, Color.rgb(150, 70, 210), opacity = 70, glow = 3)),
                effects = DamageEffects(
                    liquidDamage = true, liquidStrength = 88,
                    deadPixels = true, deadPixelStrength = 15,
                    oledBlackSpot = true, oledBlackSpotStrength = 54,
                    edgeBleed = true, edgeBleedStrength = 78
                ),
                dynamics = SceneDynamics(expandingDamage = true, animatedEntry = true),
                movementEnabled = false
            ),
            DamageScene(
                id = "preset-dead-pixels",
                name = context.getString(R.string.preset_dead_pixels),
                lines = listOf(line(51f, 1, Color.WHITE, opacity = 35)),
                effects = DamageEffects(deadPixels = true, deadPixelStrength = 100),
                movementEnabled = false
            ),
            DamageScene(
                id = "preset-cable-contact",
                name = context.getString(R.string.preset_cable_contact),
                lines = listOf(
                    line(8f, 2, Color.GREEN, flicker = true, flickerStrength = 72),
                    line(12f, 1, Color.WHITE, opacity = 72, flicker = true, flickerStrength = 80),
                    line(63f, 4, Color.rgb(255, 45, 185), opacity = 82, glow = 3, flicker = true, flickerStrength = 65),
                    line(66f, 2, Color.CYAN, opacity = 65, flicker = true, flickerStrength = 75)
                ),
                effects = DamageEffects(
                    scanlines = true, scanlineStrength = 28,
                    screenTearing = true, tearingStrength = 78,
                    cableJump = true, cableJumpStrength = 82,
                    intermittentFlash = true, flashStrength = 36
                ),
                dynamics = SceneDynamics(unstableLines = true, randomFaults = true, cycleEffects = true, cycleSeconds = 5, chargingTrigger = true),
                movementEnabled = false
            ),
            DamageScene(
                id = "preset-old-lcd",
                name = context.getString(R.string.preset_old_lcd),
                lines = listOf(line(35f, 2, Color.rgb(135, 190, 255), opacity = 35)),
                effects = DamageEffects(
                    ghosting = true, ghostStrength = 48,
                    scanlines = true, scanlineStrength = 92,
                    unevenBrightness = true, unevenBrightnessStrength = 62,
                    colorShift = true, colorShiftStrength = 18,
                    pwmBands = true, pwmStrength = 45
                ),
                movementEnabled = false
            ),
            DamageScene(
                id = "preset-light-damage",
                name = context.getString(R.string.preset_light_damage),
                lines = listOf(line(24f, 2, Color.rgb(37, 240, 90), opacity = 82, glow = 1)),
                effects = DamageEffects(deadPixels = true, deadPixelStrength = 8),
                movementDp = 2,
                movementSeconds = 120
            ),
            DamageScene(
                id = "preset-oled-decay",
                name = context.getString(R.string.preset_oled_decay),
                lines = listOf(line(81f, 2, Color.rgb(42, 255, 105), opacity = 62, glow = 2)),
                effects = DamageEffects(
                    oledBlackSpot = true, oledBlackSpotStrength = 82,
                    edgeBleed = true, edgeBleedStrength = 58,
                    unevenBrightness = true, unevenBrightnessStrength = 48,
                    pressureSpots = true, pressureSpotStrength = 25
                ),
                dynamics = SceneDynamics(animatedEntry = true, expandingDamage = true),
                movementEnabled = false
            ),
            DamageScene(
                id = "preset-rainbow-pressure",
                name = context.getString(R.string.preset_rainbow_pressure),
                lines = listOf(line(48f, 1, Color.CYAN, opacity = 24)),
                effects = DamageEffects(
                    colorShift = true, colorShiftStrength = 72,
                    pressureSpots = true, pressureSpotStrength = 76,
                    unevenBrightness = true, unevenBrightnessStrength = 28
                ),
                dynamics = SceneDynamics(animatedEntry = true),
                movementEnabled = false
            ),
            DamageScene(
                id = "preset-dynamic-failure",
                name = context.getString(R.string.preset_dynamic_failure),
                lines = listOf(
                    line(12f, 3, Color.GREEN, glow = 3, flicker = true, flickerStrength = 72),
                    line(68f, 2, Color.MAGENTA, opacity = 82, flicker = true, flickerStrength = 64)
                ),
                effects = DamageEffects(
                    screenTearing = true, tearingStrength = 62,
                    partialBlackout = true, blackoutStrength = 44,
                    intermittentFlash = true, flashStrength = 48,
                    pwmBands = true, pwmStrength = 35,
                    cableJump = true, cableJumpStrength = 66
                ),
                dynamics = SceneDynamics(
                    animatedEntry = true, unstableLines = true, timelineEnabled = true,
                    randomFaults = true, cycleEffects = true, cycleSeconds = 4,
                    flipTrigger = true, unlockTrigger = true
                ),
                movementEnabled = false
            ),
            DamageScene(
                id = "preset-severe-damage",
                name = context.getString(R.string.preset_severe_damage),
                lines = listOf(
                    line(7f, 4, Color.GREEN, glow = 5, flicker = true, flickerStrength = 48),
                    line(29f, 2, Color.rgb(255, 45, 185), glow = 3),
                    line(58f, 6, Color.WHITE, opacity = 76, glow = 6, flicker = true, flickerStrength = 62),
                    line(88f, 3, Color.CYAN, opacity = 70, glow = 4)
                ),
                effects = DamageEffects(
                    crackedScreen = true,
                    crackStrength = 94,
                    crackPattern = CrackPattern.CORNER_SHATTER,
                    crackOpacityPercent = 45,
                    crackMask = CrackMask.SCREEN_EDGES,
                    crackImpacts = listOf(
                        impact(4f, 10f, 20, 88),
                        impact(96f, 84f, 14, 66, rotation = 180),
                        impact(74f, 28f, 12, 58, rotation = 23)
                    ),
                    edgeChips = true,
                    glassShards = true,
                    glassReflection = true,
                    crackParallax = true,
                    deadPixels = true,
                    deadPixelStrength = 88,
                    liquidDamage = true,
                    liquidStrength = 76,
                    ghosting = true,
                    ghostStrength = 55,
                    scanlines = true,
                    scanlineStrength = 68,
                    oledBlackSpot = true, oledBlackSpotStrength = 78,
                    edgeBleed = true, edgeBleedStrength = 82,
                    unevenBrightness = true, unevenBrightnessStrength = 58,
                    colorShift = true, colorShiftStrength = 46,
                    pressureSpots = true, pressureSpotStrength = 42,
                    screenTearing = true, tearingStrength = 68,
                    partialBlackout = true, blackoutStrength = 48,
                    intermittentFlash = true, flashStrength = 44,
                    pwmBands = true, pwmStrength = 38,
                    cableJump = true, cableJumpStrength = 72
                ),
                dynamics = SceneDynamics(
                    animatedEntry = true, impactFlash = true, expandingDamage = true,
                    unstableLines = true, timelineEnabled = true, randomFaults = true,
                    shakeTrigger = true, flipTrigger = true, chargingTrigger = true, unlockTrigger = true
                ),
                movementEnabled = false
            )
        )

    private fun line(
        position: Float,
        width: Int,
        color: Int,
        opacity: Int = 100,
        glow: Int = 0,
        flicker: Boolean = false,
        flickerStrength: Int = 20
    ) = DamageLine(
        id = System.nanoTime() + (position * 100).toLong(),
        positionPercent = position,
        widthDp = width,
        color = color,
        opacityPercent = opacity,
        glowDp = glow,
        flicker = flicker,
        flickerStrength = flickerStrength
    )

    private fun impact(
        x: Float,
        y: Float,
        branches: Int,
        length: Int,
        rotation: Int = 0
    ) = CrackImpact(
        id = System.nanoTime() + (x * 100 + y).toLong(),
        xPercent = x,
        yPercent = y,
        rotationDegrees = rotation,
        branchCount = branches,
        lengthPercent = length,
        seedOffset = (x * 1_000 + y * 10).toInt()
    )
}
