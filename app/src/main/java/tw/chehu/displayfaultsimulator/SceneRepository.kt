package tw.chehu.displayfaultsimulator

import android.content.Context
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.random.Random

class SceneRepository(context: Context) {
    private val appContext = context.applicationContext
    private val settings = LineSettings(context)
    private val preferences = settings.rawPreferences()

    fun scenes(): List<DamageScene> {
        val stored = preferences.getString(KEY_SCENES, null)
        if (stored.isNullOrBlank()) {
            val migrated = migrateLegacyScene()
            saveAll(listOf(migrated))
            activeSceneId = migrated.id
            return listOf(migrated)
        }
        return runCatching {
            val array = JSONArray(stored)
            buildList {
                for (index in 0 until array.length()) add(sceneFromJson(array.getJSONObject(index)))
            }
        }.getOrElse {
            val fallback = DamageScene.classic(name = appContext.getString(R.string.preset_classic_oled))
            saveAll(listOf(fallback))
            listOf(fallback)
        }.ifEmpty { listOf(DamageScene.classic(name = appContext.getString(R.string.preset_classic_oled))) }
    }

    var activeSceneId: String
        get() = preferences.getString(KEY_ACTIVE_SCENE, null) ?: scenes().first().id
        set(value) = preferences.edit().putString(KEY_ACTIVE_SCENE, value).apply()

    fun activeScene(): DamageScene {
        val all = scenes()
        return all.firstOrNull { it.id == activeSceneId } ?: all.first().also { activeSceneId = it.id }
    }

    fun find(id: String): DamageScene? = scenes().firstOrNull { it.id == id }

    fun upsert(scene: DamageScene) {
        val all = scenes().toMutableList()
        val index = all.indexOfFirst { it.id == scene.id }
        if (index >= 0) all[index] = scene else all.add(scene)
        saveAll(all)
    }

    fun create(name: String? = null): DamageScene {
        val scene = DamageScene.classic(UUID.randomUUID().toString(), name ?: appContext.getString(R.string.new_scene))
        upsert(scene)
        activeSceneId = scene.id
        return scene
    }

    fun createFromPreset(preset: DamageScene): DamageScene {
        val randomizedEffects = if (preset.effects.crackedScreen && preset.effects.randomizeCracks) {
            val source = preset.effects.crackImpacts.ifEmpty { defaultCrackImpacts(preset.effects.crackPattern) }
            preset.effects.copy(crackImpacts = source.mapIndexed { index, impact ->
                impact.copy(
                    id = System.nanoTime() + index,
                    xPercent = (impact.xPercent + Random.nextFloat() * 12f - 6f).coerceIn(2f, 98f),
                    yPercent = (impact.yPercent + Random.nextFloat() * 12f - 6f).coerceIn(2f, 98f),
                    rotationDegrees = (impact.rotationDegrees + Random.nextInt(-28, 29) + 360) % 360,
                    seedOffset = Random.nextInt()
                )
            })
        } else preset.effects
        val copy = preset.copy(
            id = UUID.randomUUID().toString(),
            lines = preset.lines.mapIndexed { index, line -> line.copy(id = System.nanoTime() + index) },
            effects = randomizedEffects
        )
        upsert(copy)
        activeSceneId = copy.id
        return copy
    }

    fun duplicate(source: DamageScene): DamageScene {
        val copy = source.copy(
            id = UUID.randomUUID().toString(),
            name = appContext.getString(R.string.copy_scene_name, source.name),
            lines = source.lines.mapIndexed { index, line -> line.copy(id = System.nanoTime() + index) }
        )
        upsert(copy)
        activeSceneId = copy.id
        return copy
    }

    private fun defaultCrackImpacts(pattern: CrackPattern): List<CrackImpact> = when (pattern) {
        CrackPattern.CORNER_SHATTER -> listOf(
            CrackImpact(System.nanoTime(), 4f, 10f, branchCount = 17, lengthPercent = 78),
            CrackImpact(System.nanoTime() + 1, 96f, 84f, rotationDegrees = 180, branchCount = 12, lengthPercent = 58)
        )
        CrackPattern.HAIRLINE -> listOf(CrackImpact(System.nanoTime(), 50f, 2f, branchCount = 7, lengthPercent = 94))
        CrackPattern.RADIAL_IMPACT -> listOf(CrackImpact(System.nanoTime(), 54f, 43f, branchCount = 18, lengthPercent = 92))
        CrackPattern.SPIDERWEB -> listOf(CrackImpact(System.nanoTime(), 72f, 29f, branchCount = 16, lengthPercent = 72))
    }

    fun delete(id: String): Boolean {
        val all = scenes().toMutableList()
        if (all.size <= 1) return false
        all.removeAll { it.id == id }
        saveAll(all)
        if (activeSceneId == id) activeSceneId = all.first().id
        return true
    }

    private fun saveAll(scenes: List<DamageScene>) {
        val array = JSONArray()
        scenes.forEach { array.put(sceneToJson(it)) }
        preferences.edit().putString(KEY_SCENES, array.toString()).apply()
    }

    private fun migrateLegacyScene(): DamageScene {
        val color = preferences.getInt("color", Color.rgb(37, 240, 90))
        val width = preferences.getInt("width_dp", 4).coerceIn(1, 30)
        val position = preferences.getInt("position_percent", 12).coerceIn(0, 100)
        return DamageScene.classic(name = appContext.getString(R.string.preset_classic_oled)).copy(
            lines = listOf(
                DamageLine(
                    id = System.nanoTime(),
                    positionPercent = position.toFloat(),
                    widthDp = width,
                    color = color,
                    opacityPercent = 100,
                    glowDp = 2,
                    flicker = false,
                    flickerStrength = 20
                )
            ),
            movementEnabled = preferences.getBoolean("movement_enabled", true),
            movementDp = preferences.getInt("movement_dp", 3).coerceIn(1, 12),
            movementSeconds = preferences.getInt("movement_seconds", 60).coerceIn(15, 600)
        )
    }

    private fun sceneToJson(scene: DamageScene) = JSONObject().apply {
        put("id", scene.id)
        put("name", scene.name)
        put("movementEnabled", scene.movementEnabled)
        put("movementDp", scene.movementDp)
        put("movementSeconds", scene.movementSeconds)
        put("lines", JSONArray().apply {
            scene.lines.forEach { line ->
                put(JSONObject().apply {
                    put("id", line.id)
                    put("position", line.positionPercent.toDouble())
                    put("width", line.widthDp)
                    put("color", line.color)
                    put("opacity", line.opacityPercent)
                    put("glow", line.glowDp)
                    put("flicker", line.flicker)
                    put("flickerStrength", line.flickerStrength)
                })
            }
        })
        put("effects", JSONObject().apply {
            put("crackedScreen", scene.effects.crackedScreen)
            put("crackStrength", scene.effects.crackStrength)
            put("crackPattern", scene.effects.crackPattern.name)
            put("crackOpacityPercent", scene.effects.crackOpacityPercent)
            put("crackMask", scene.effects.crackMask.name)
            put("crackImpacts", JSONArray().apply {
                scene.effects.crackImpacts.forEach { impact ->
                    put(JSONObject().apply {
                        put("id", impact.id)
                        put("x", impact.xPercent.toDouble())
                        put("y", impact.yPercent.toDouble())
                        put("rotation", impact.rotationDegrees)
                        put("branches", impact.branchCount)
                        put("length", impact.lengthPercent)
                        put("seed", impact.seedOffset)
                    })
                }
            })
            put("edgeChips", scene.effects.edgeChips)
            put("glassShards", scene.effects.glassShards)
            put("glassReflection", scene.effects.glassReflection)
            put("crackParallax", scene.effects.crackParallax)
            put("randomizeCracks", scene.effects.randomizeCracks)
            put("deadPixels", scene.effects.deadPixels)
            put("deadPixelStrength", scene.effects.deadPixelStrength)
            put("liquidDamage", scene.effects.liquidDamage)
            put("liquidStrength", scene.effects.liquidStrength)
            put("ghosting", scene.effects.ghosting)
            put("ghostStrength", scene.effects.ghostStrength)
            put("scanlines", scene.effects.scanlines)
            put("scanlineStrength", scene.effects.scanlineStrength)
            put("oledBlackSpot", scene.effects.oledBlackSpot)
            put("oledBlackSpotStrength", scene.effects.oledBlackSpotStrength)
            put("edgeBleed", scene.effects.edgeBleed)
            put("edgeBleedStrength", scene.effects.edgeBleedStrength)
            put("unevenBrightness", scene.effects.unevenBrightness)
            put("unevenBrightnessStrength", scene.effects.unevenBrightnessStrength)
            put("colorShift", scene.effects.colorShift)
            put("colorShiftStrength", scene.effects.colorShiftStrength)
            put("pressureSpots", scene.effects.pressureSpots)
            put("pressureSpotStrength", scene.effects.pressureSpotStrength)
            put("screenTearing", scene.effects.screenTearing)
            put("tearingStrength", scene.effects.tearingStrength)
            put("partialBlackout", scene.effects.partialBlackout)
            put("blackoutStrength", scene.effects.blackoutStrength)
            put("intermittentFlash", scene.effects.intermittentFlash)
            put("flashStrength", scene.effects.flashStrength)
            put("pwmBands", scene.effects.pwmBands)
            put("pwmStrength", scene.effects.pwmStrength)
            put("cableJump", scene.effects.cableJump)
            put("cableJumpStrength", scene.effects.cableJumpStrength)
        })
        put("dynamics", JSONObject().apply {
            put("animatedEntry", scene.dynamics.animatedEntry)
            put("impactFlash", scene.dynamics.impactFlash)
            put("expandingDamage", scene.dynamics.expandingDamage)
            put("unstableLines", scene.dynamics.unstableLines)
            put("timelineEnabled", scene.dynamics.timelineEnabled)
            put("randomFaults", scene.dynamics.randomFaults)
            put("cycleEffects", scene.dynamics.cycleEffects)
            put("cycleSeconds", scene.dynamics.cycleSeconds)
            put("triggerSceneId", scene.dynamics.triggerSceneId ?: JSONObject.NULL)
            put("shakeTrigger", scene.dynamics.shakeTrigger)
            put("flipTrigger", scene.dynamics.flipTrigger)
            put("chargingTrigger", scene.dynamics.chargingTrigger)
            put("unlockTrigger", scene.dynamics.unlockTrigger)
        })
    }

    private fun sceneFromJson(json: JSONObject): DamageScene {
        val linesJson = json.optJSONArray("lines") ?: JSONArray()
        val lines = buildList {
            for (index in 0 until linesJson.length()) {
                val line = linesJson.getJSONObject(index)
                add(
                    DamageLine(
                        id = line.optLong("id", System.nanoTime() + index),
                        positionPercent = line.optDouble("position", 12.0).toFloat().coerceIn(0f, 100f),
                        widthDp = line.optInt("width", 4).coerceIn(1, 30),
                        color = line.optInt("color", Color.rgb(37, 240, 90)),
                        opacityPercent = line.optInt("opacity", 100).coerceIn(10, 100),
                        glowDp = line.optInt("glow", 2).coerceIn(0, 24),
                        flicker = line.optBoolean("flicker", false),
                        flickerStrength = line.optInt("flickerStrength", 20).coerceIn(0, 80)
                    )
                )
            }
        }.ifEmpty { DamageScene.classic().lines }
        val effectsJson = json.optJSONObject("effects") ?: JSONObject()
        val impactsJson = effectsJson.optJSONArray("crackImpacts") ?: JSONArray()
        val crackImpacts = buildList {
            for (index in 0 until impactsJson.length()) {
                val impact = impactsJson.optJSONObject(index) ?: continue
                add(CrackImpact(
                    id = impact.optLong("id", System.nanoTime() + index),
                    xPercent = impact.optDouble("x", 72.0).toFloat().coerceIn(0f, 100f),
                    yPercent = impact.optDouble("y", 29.0).toFloat().coerceIn(0f, 100f),
                    rotationDegrees = impact.optInt("rotation", 0).coerceIn(0, 359),
                    branchCount = impact.optInt("branches", 14).coerceIn(3, 28),
                    lengthPercent = impact.optInt("length", 72).coerceIn(20, 100),
                    seedOffset = impact.optInt("seed", index)
                ))
            }
        }
        val dynamicsJson = json.optJSONObject("dynamics") ?: JSONObject()
        return DamageScene(
            id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = json.optString("name", appContext.getString(R.string.unnamed_scene)),
            lines = lines,
            effects = DamageEffects(
                crackedScreen = effectsJson.optBoolean("crackedScreen", false),
                crackStrength = effectsJson.optInt("crackStrength", 55).coerceIn(0, 100),
                crackPattern = runCatching {
                    CrackPattern.valueOf(effectsJson.optString("crackPattern", CrackPattern.SPIDERWEB.name))
                }.getOrDefault(CrackPattern.SPIDERWEB),
                crackOpacityPercent = effectsJson.optInt("crackOpacityPercent", 42).coerceIn(10, 100),
                crackMask = runCatching {
                    CrackMask.valueOf(effectsJson.optString("crackMask", CrackMask.FULL_SCREEN.name))
                }.getOrDefault(CrackMask.FULL_SCREEN),
                crackImpacts = crackImpacts,
                edgeChips = effectsJson.optBoolean("edgeChips", false),
                glassShards = effectsJson.optBoolean("glassShards", false),
                glassReflection = effectsJson.optBoolean("glassReflection", true),
                crackParallax = effectsJson.optBoolean("crackParallax", false),
                randomizeCracks = effectsJson.optBoolean("randomizeCracks", true),
                deadPixels = effectsJson.optBoolean("deadPixels", false),
                deadPixelStrength = effectsJson.optInt("deadPixelStrength", 35).coerceIn(0, 100),
                liquidDamage = effectsJson.optBoolean("liquidDamage", false),
                liquidStrength = effectsJson.optInt("liquidStrength", 45).coerceIn(0, 100),
                ghosting = effectsJson.optBoolean("ghosting", false),
                ghostStrength = effectsJson.optInt("ghostStrength", 30).coerceIn(0, 100),
                scanlines = effectsJson.optBoolean("scanlines", false),
                scanlineStrength = effectsJson.optInt("scanlineStrength", 30).coerceIn(0, 100),
                oledBlackSpot = effectsJson.optBoolean("oledBlackSpot", false),
                oledBlackSpotStrength = effectsJson.optInt("oledBlackSpotStrength", 45).coerceIn(0, 100),
                edgeBleed = effectsJson.optBoolean("edgeBleed", false),
                edgeBleedStrength = effectsJson.optInt("edgeBleedStrength", 45).coerceIn(0, 100),
                unevenBrightness = effectsJson.optBoolean("unevenBrightness", false),
                unevenBrightnessStrength = effectsJson.optInt("unevenBrightnessStrength", 35).coerceIn(0, 100),
                colorShift = effectsJson.optBoolean("colorShift", false),
                colorShiftStrength = effectsJson.optInt("colorShiftStrength", 35).coerceIn(0, 100),
                pressureSpots = effectsJson.optBoolean("pressureSpots", false),
                pressureSpotStrength = effectsJson.optInt("pressureSpotStrength", 40).coerceIn(0, 100),
                screenTearing = effectsJson.optBoolean("screenTearing", false),
                tearingStrength = effectsJson.optInt("tearingStrength", 40).coerceIn(0, 100),
                partialBlackout = effectsJson.optBoolean("partialBlackout", false),
                blackoutStrength = effectsJson.optInt("blackoutStrength", 72).coerceIn(0, 100),
                intermittentFlash = effectsJson.optBoolean("intermittentFlash", false),
                flashStrength = effectsJson.optInt("flashStrength", 55).coerceIn(0, 100),
                pwmBands = effectsJson.optBoolean("pwmBands", false),
                pwmStrength = effectsJson.optInt("pwmStrength", 38).coerceIn(0, 100),
                cableJump = effectsJson.optBoolean("cableJump", false),
                cableJumpStrength = effectsJson.optInt("cableJumpStrength", 55).coerceIn(0, 100)
            ),
            dynamics = SceneDynamics(
                animatedEntry = dynamicsJson.optBoolean("animatedEntry", false),
                impactFlash = dynamicsJson.optBoolean("impactFlash", false),
                expandingDamage = dynamicsJson.optBoolean("expandingDamage", false),
                unstableLines = dynamicsJson.optBoolean("unstableLines", false),
                timelineEnabled = dynamicsJson.optBoolean("timelineEnabled", false),
                randomFaults = dynamicsJson.optBoolean("randomFaults", false),
                cycleEffects = dynamicsJson.optBoolean("cycleEffects", false),
                cycleSeconds = dynamicsJson.optInt("cycleSeconds", 8).coerceIn(2, 60),
                triggerSceneId = dynamicsJson.optString("triggerSceneId").takeIf { it.isNotBlank() && it != "null" },
                shakeTrigger = dynamicsJson.optBoolean("shakeTrigger", false),
                flipTrigger = dynamicsJson.optBoolean("flipTrigger", false),
                chargingTrigger = dynamicsJson.optBoolean("chargingTrigger", false),
                unlockTrigger = dynamicsJson.optBoolean("unlockTrigger", false)
            ),
            movementEnabled = json.optBoolean("movementEnabled", true),
            movementDp = json.optInt("movementDp", 3).coerceIn(1, 12),
            movementSeconds = json.optInt("movementSeconds", 60).coerceIn(15, 600)
        )
    }

    companion object {
        private const val KEY_SCENES = "damage_scenes_v2"
        private const val KEY_ACTIVE_SCENE = "active_scene_id"
    }
}
