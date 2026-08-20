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
                    deadPixels = true,
                    deadPixelStrength = 22,
                    liquidDamage = true,
                    liquidStrength = 28
                ),
                movementEnabled = false
            ),
            DamageScene(
                id = "preset-liquid",
                name = context.getString(R.string.preset_liquid),
                lines = listOf(line(83f, 3, Color.rgb(150, 70, 210), opacity = 70, glow = 3)),
                effects = DamageEffects(liquidDamage = true, liquidStrength = 88, deadPixels = true, deadPixelStrength = 15),
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
                effects = DamageEffects(scanlines = true, scanlineStrength = 28),
                movementEnabled = false
            ),
            DamageScene(
                id = "preset-old-lcd",
                name = context.getString(R.string.preset_old_lcd),
                lines = listOf(line(35f, 2, Color.rgb(135, 190, 255), opacity = 35)),
                effects = DamageEffects(ghosting = true, ghostStrength = 48, scanlines = true, scanlineStrength = 92),
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
                    deadPixels = true,
                    deadPixelStrength = 88,
                    liquidDamage = true,
                    liquidStrength = 76,
                    ghosting = true,
                    ghostStrength = 55,
                    scanlines = true,
                    scanlineStrength = 68
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
}
