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

enum class CrackMask {
    FULL_SCREEN,
    TOP_LEFT,
    SCREEN_EDGES,
    AROUND_IMPACTS
}

data class CrackImpact(
    val id: Long,
    val xPercent: Float,
    val yPercent: Float,
    val rotationDegrees: Int = 0,
    val branchCount: Int = 14,
    val lengthPercent: Int = 72,
    val seedOffset: Int = 0
)

data class DamageEffects(
    val crackedScreen: Boolean = false,
    val crackStrength: Int = 55,
    val crackPattern: CrackPattern = CrackPattern.SPIDERWEB,
    val crackOpacityPercent: Int = 42,
    val crackMask: CrackMask = CrackMask.FULL_SCREEN,
    val crackImpacts: List<CrackImpact> = emptyList(),
    val edgeChips: Boolean = false,
    val glassShards: Boolean = false,
    val glassReflection: Boolean = true,
    val crackParallax: Boolean = false,
    val randomizeCracks: Boolean = true,
    val deadPixels: Boolean = false,
    val deadPixelStrength: Int = 35,
    val liquidDamage: Boolean = false,
    val liquidStrength: Int = 45,
    val ghosting: Boolean = false,
    val ghostStrength: Int = 30,
    val scanlines: Boolean = false,
    val scanlineStrength: Int = 30,
    val oledBlackSpot: Boolean = false,
    val oledBlackSpotStrength: Int = 45,
    val edgeBleed: Boolean = false,
    val edgeBleedStrength: Int = 45,
    val unevenBrightness: Boolean = false,
    val unevenBrightnessStrength: Int = 35,
    val colorShift: Boolean = false,
    val colorShiftStrength: Int = 35,
    val pressureSpots: Boolean = false,
    val pressureSpotStrength: Int = 40,
    val screenTearing: Boolean = false,
    val tearingStrength: Int = 40,
    val partialBlackout: Boolean = false,
    val blackoutStrength: Int = 72,
    val intermittentFlash: Boolean = false,
    val flashStrength: Int = 55,
    val pwmBands: Boolean = false,
    val pwmStrength: Int = 38,
    val cableJump: Boolean = false,
    val cableJumpStrength: Int = 55
)

data class SceneDynamics(
    val animatedEntry: Boolean = false,
    val impactFlash: Boolean = false,
    val expandingDamage: Boolean = false,
    val unstableLines: Boolean = false,
    val timelineEnabled: Boolean = false,
    val randomFaults: Boolean = false,
    val cycleEffects: Boolean = false,
    val cycleSeconds: Int = 8,
    val triggerSceneId: String? = null,
    val shakeTrigger: Boolean = false,
    val flipTrigger: Boolean = false,
    val chargingTrigger: Boolean = false,
    val unlockTrigger: Boolean = false
)

data class DamageScene(
    val id: String,
    val name: String,
    val lines: List<DamageLine>,
    val effects: DamageEffects = DamageEffects(),
    val dynamics: SceneDynamics = SceneDynamics(),
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
