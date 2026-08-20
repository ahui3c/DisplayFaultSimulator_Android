package tw.chehu.displayfaultsimulator

import android.graphics.Color

data class DamageLine(
    val id: Long,
    val positionPercent: Float,
    val widthDp: Int,
    val color: Int,
    val opacityPercent: Int,
    val glowDp: Int,
    val flicker: Boolean,
    val flickerStrength: Int
)

enum class CrackPattern {
    SPIDERWEB,
    RADIAL_IMPACT,
    CORNER_SHATTER,
    HAIRLINE
}

data class DamageEffects(
    val crackedScreen: Boolean = false,
    val crackStrength: Int = 55,
    val crackPattern: CrackPattern = CrackPattern.SPIDERWEB,
    val crackOpacityPercent: Int = 42,
    val deadPixels: Boolean = false,
    val deadPixelStrength: Int = 35,
    val liquidDamage: Boolean = false,
    val liquidStrength: Int = 45,
    val ghosting: Boolean = false,
    val ghostStrength: Int = 30,
    val scanlines: Boolean = false,
    val scanlineStrength: Int = 30
)

data class DamageScene(
    val id: String,
    val name: String,
    val lines: List<DamageLine>,
    val effects: DamageEffects = DamageEffects(),
    val movementEnabled: Boolean = true,
    val movementDp: Int = 3,
    val movementSeconds: Int = 60
) {
    companion object {
        fun classic(id: String = "classic-green", name: String = "Classic OLED green line") = DamageScene(
            id = id,
            name = name,
            lines = listOf(
                DamageLine(
                    id = System.nanoTime(),
                    positionPercent = 12f,
                    widthDp = 4,
                    color = Color.rgb(37, 240, 90),
                    opacityPercent = 100,
                    glowDp = 2,
                    flicker = false,
                    flickerStrength = 20
                )
            )
        )
    }
}
