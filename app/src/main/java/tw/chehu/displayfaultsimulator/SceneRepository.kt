package tw.chehu.displayfaultsimulator

import android.content.Context
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

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
        val copy = preset.copy(
            id = UUID.randomUUID().toString(),
            lines = preset.lines.mapIndexed { index, line -> line.copy(id = System.nanoTime() + index) }
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
            put("deadPixels", scene.effects.deadPixels)
            put("deadPixelStrength", scene.effects.deadPixelStrength)
            put("liquidDamage", scene.effects.liquidDamage)
            put("liquidStrength", scene.effects.liquidStrength)
            put("ghosting", scene.effects.ghosting)
            put("ghostStrength", scene.effects.ghostStrength)
            put("scanlines", scene.effects.scanlines)
            put("scanlineStrength", scene.effects.scanlineStrength)
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
                deadPixels = effectsJson.optBoolean("deadPixels", false),
                deadPixelStrength = effectsJson.optInt("deadPixelStrength", 35).coerceIn(0, 100),
                liquidDamage = effectsJson.optBoolean("liquidDamage", false),
                liquidStrength = effectsJson.optInt("liquidStrength", 45).coerceIn(0, 100),
                ghosting = effectsJson.optBoolean("ghosting", false),
                ghostStrength = effectsJson.optInt("ghostStrength", 30).coerceIn(0, 100),
                scanlines = effectsJson.optBoolean("scanlines", false),
                scanlineStrength = effectsJson.optInt("scanlineStrength", 30).coerceIn(0, 100)
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
