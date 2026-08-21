package tw.chehu.displayfaultsimulator

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class SceneEditorActivity : Activity() {
    private lateinit var repository: SceneRepository
    private lateinit var workingScene: DamageScene
    private lateinit var preview: DamageSceneView
    private lateinit var nameInput: EditText
    private lateinit var selectedLabel: TextView
    private lateinit var positionValue: TextView
    private lateinit var colorSpinner: Spinner
    private lateinit var widthSeek: SeekBar
    private lateinit var opacitySeek: SeekBar
    private lateinit var glowSeek: SeekBar
    private lateinit var flickerSwitch: Switch
    private lateinit var flickerSeek: SeekBar
    private lateinit var widthValue: TextView
    private lateinit var opacityValue: TextView
    private lateinit var glowValue: TextView
    private lateinit var flickerValue: TextView
    private lateinit var movementSwitch: Switch
    private lateinit var movementSeek: SeekBar
    private lateinit var movementIntervalSeek: SeekBar
    private lateinit var movementValue: TextView
    private lateinit var movementIntervalValue: TextView
    private lateinit var crackSwitch: Switch
    private lateinit var crackPatternSpinner: Spinner
    private lateinit var crackSeek: SeekBar
    private lateinit var crackStrengthValue: TextView
    private lateinit var crackOpacitySeek: SeekBar
    private lateinit var crackOpacityValue: TextView
    private lateinit var deadPixelSwitch: Switch
    private lateinit var deadPixelSeek: SeekBar
    private lateinit var liquidSwitch: Switch
    private lateinit var liquidSeek: SeekBar
    private lateinit var ghostSwitch: Switch
    private lateinit var ghostSeek: SeekBar
    private lateinit var scanlineSwitch: Switch
    private lateinit var scanlineSeek: SeekBar
    private var syncing = false

    private val colors by lazy { listOf(
        ColorOption(getString(R.string.color_bright_green), Color.rgb(37, 240, 90)),
        ColorOption(getString(R.string.color_bright_purple), Color.rgb(196, 88, 255)),
        ColorOption(getString(R.string.color_bright_red), Color.rgb(255, 64, 64)),
        ColorOption(getString(R.string.color_bright_blue), Color.rgb(55, 154, 255)),
        ColorOption(getString(R.string.color_bright_yellow), Color.rgb(255, 225, 64)),
        ColorOption(getString(R.string.color_cyan), Color.rgb(37, 230, 230)),
        ColorOption(getString(R.string.color_white), Color.WHITE),
        ColorOption(getString(R.string.color_black), Color.BLACK)
    ) }

    private val crackPatterns by lazy { listOf(
        CrackPatternOption(getString(R.string.crack_pattern_spiderweb), CrackPattern.SPIDERWEB),
        CrackPatternOption(getString(R.string.crack_pattern_radial), CrackPattern.RADIAL_IMPACT),
        CrackPatternOption(getString(R.string.crack_pattern_corner), CrackPattern.CORNER_SHATTER),
        CrackPatternOption(getString(R.string.crack_pattern_hairline), CrackPattern.HAIRLINE)
    ) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = SceneRepository(this)
        val id = intent.getStringExtra(EXTRA_SCENE_ID) ?: repository.activeSceneId
        workingScene = repository.find(id) ?: repository.activeScene()
        setContentView(buildContent())
        bindEvents()
        syncAllControls()
    }

    private fun buildContent(): ScrollView {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(245, 247, 246)); isFillViewport = true }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }

        content.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(textView(getString(R.string.editor_title), 24f, Color.rgb(13, 27, 20), Typeface.BOLD), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(smallButton(getString(R.string.action_done)) { saveAndFinish() })
        }, marginBottom())

        content.addView(card().apply {
            addView(fieldLabel(getString(R.string.scene_name)))
            nameInput = EditText(this@SceneEditorActivity).apply {
                setSingleLine(true)
                setTextColor(Color.rgb(25, 35, 30))
                textSize = 17f
            }
            addView(nameInput, matchWrap())
        }, marginBottom())

        content.addView(card().apply {
            addView(textView(getString(R.string.drag_preview_title), 18f, Color.rgb(13, 27, 20), Typeface.BOLD))
            addView(textView(getString(R.string.drag_preview_description), 13f, Color.rgb(90, 102, 96)).apply { setPadding(0, dp(4), 0, dp(10)) })
            preview = DamageSceneView(this@SceneEditorActivity).apply {
                editorMode = true
                setBackgroundColor(Color.rgb(35, 40, 38))
                contentDescription = getString(R.string.drag_preview_accessibility)
            }
            addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(380)))
            selectedLabel = textView("", 14f, Color.rgb(8, 122, 54), Typeface.BOLD).apply { setPadding(0, dp(10), 0, 0) }
            positionValue = textView("", 13f, Color.rgb(90, 102, 96))
            addView(selectedLabel)
            addView(positionValue)
            addView(LinearLayout(this@SceneEditorActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(smallButton(getString(R.string.add_line)) { addLine() }, LinearLayout.LayoutParams(0, dp(52), 1f))
                addView(smallButton(getString(R.string.delete_selected)) { deleteSelectedLine() }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginStart = dp(8) })
            }.apply { setPadding(0, dp(8), 0, 0) })
        }, marginBottom())

        content.addView(card().apply {
            addView(sectionTitle(getString(R.string.section_selected_line)))
            addView(fieldLabel(getString(R.string.field_color)))
            colorSpinner = Spinner(this@SceneEditorActivity).apply {
                adapter = ArrayAdapter(this@SceneEditorActivity, android.R.layout.simple_spinner_dropdown_item, colors.map { it.name })
            }
            addView(colorSpinner, matchWrap())
            widthValue = valueText(); addView(fieldHeader(getString(R.string.field_width), widthValue))
            widthSeek = SeekBar(this@SceneEditorActivity).apply { max = 29 }; addView(widthSeek, matchWrap())
            opacityValue = valueText(); addView(fieldHeader(getString(R.string.field_opacity), opacityValue))
            opacitySeek = SeekBar(this@SceneEditorActivity).apply { max = 90 }; addView(opacitySeek, matchWrap())
            glowValue = valueText(); addView(fieldHeader(getString(R.string.field_glow), glowValue))
            glowSeek = SeekBar(this@SceneEditorActivity).apply { max = 24 }; addView(glowSeek, matchWrap())
            flickerSwitch = Switch(this@SceneEditorActivity).apply { text = getString(R.string.field_flicker); textSize = 16f }
            addView(flickerSwitch, matchWrap())
            flickerValue = valueText(); addView(fieldHeader(getString(R.string.field_flicker_strength), flickerValue))
            flickerSeek = SeekBar(this@SceneEditorActivity).apply { max = 80 }; addView(flickerSeek, matchWrap())
        }, marginBottom())

        content.addView(card().apply {
            addView(sectionTitle(getString(R.string.section_position_protection)))
            movementSwitch = Switch(this@SceneEditorActivity).apply { text = getString(R.string.field_timed_movement); textSize = 16f }
            addView(movementSwitch, matchWrap())
            movementValue = valueText(); addView(fieldHeader(getString(R.string.field_max_offset), movementValue))
            movementSeek = SeekBar(this@SceneEditorActivity).apply { max = 11 }; addView(movementSeek, matchWrap())
            movementIntervalValue = valueText(); addView(fieldHeader(getString(R.string.field_movement_interval), movementIntervalValue))
            movementIntervalSeek = SeekBar(this@SceneEditorActivity).apply { max = 19 }; addView(movementIntervalSeek, matchWrap())
        }, marginBottom())

        content.addView(card().apply {
            addView(sectionTitle(getString(R.string.section_composite_damage)))
            crackSwitch = effectSwitch(getString(R.string.effect_cracked_screen)); addView(crackSwitch)
            addView(fieldLabel(getString(R.string.field_crack_pattern)))
            crackPatternSpinner = Spinner(this@SceneEditorActivity).apply {
                adapter = ArrayAdapter(this@SceneEditorActivity, android.R.layout.simple_spinner_dropdown_item, crackPatterns.map { it.name })
            }
            addView(crackPatternSpinner, matchWrap())
            crackStrengthValue = valueText(); addView(fieldHeader(getString(R.string.field_crack_strength), crackStrengthValue))
            crackSeek = effectSeek(); addView(crackSeek)
            crackOpacityValue = valueText(); addView(fieldHeader(getString(R.string.field_crack_opacity), crackOpacityValue))
            crackOpacitySeek = SeekBar(this@SceneEditorActivity).apply { max = 90 }; addView(crackOpacitySeek, matchWrap())
            deadPixelSwitch = effectSwitch(getString(R.string.effect_dead_pixels)); addView(deadPixelSwitch)
            deadPixelSeek = effectSeek(); addView(deadPixelSeek)
            liquidSwitch = effectSwitch(getString(R.string.effect_liquid_damage)); addView(liquidSwitch)
            liquidSeek = effectSeek(); addView(liquidSeek)
            ghostSwitch = effectSwitch(getString(R.string.effect_ghosting)); addView(ghostSwitch)
            ghostSeek = effectSeek(); addView(ghostSeek)
            scanlineSwitch = effectSwitch(getString(R.string.effect_scanlines)); addView(scanlineSwitch)
            scanlineSeek = effectSeek(); addView(scanlineSeek)
            addView(textView(getString(R.string.effect_strength_description), 13f, Color.rgb(90, 102, 96)).apply { setPadding(0, dp(8), 0, 0) })
            addView(actionButton(getString(R.string.advanced_effects_button)) { openAdvancedEditor() })
        }, marginBottom())

        content.addView(actionButton(getString(R.string.save_and_apply_scene)) { saveAndFinish() }.apply {
            background = rounded(Color.rgb(8, 122, 54), 14f)
            setTextColor(Color.WHITE)
        })
        scroll.addView(content)
        return scroll
    }

    private fun bindEvents() {
        preview.onSceneChanged = {
            workingScene = it
            updateSelectedLabels()
        }
        preview.onSelectionChanged = { syncSelectedLineControls() }
        colorSpinner.onItemSelectedListener = SimpleItemSelectedListener {
            if (!syncing) updateSelectedLine { it.copy(color = colors[colorSpinner.selectedItemPosition].color) }
        }
        widthSeek.listen { updateSelectedLine { line -> line.copy(widthDp = widthSeek.progress + 1) }; updateLineValues() }
        opacitySeek.listen { updateSelectedLine { line -> line.copy(opacityPercent = opacitySeek.progress + 10) }; updateLineValues() }
        glowSeek.listen { updateSelectedLine { line -> line.copy(glowDp = glowSeek.progress) }; updateLineValues() }
        flickerSeek.listen { updateSelectedLine { line -> line.copy(flickerStrength = flickerSeek.progress) }; updateLineValues() }
        flickerSwitch.setOnCheckedChangeListener { _, checked ->
            if (!syncing) { updateSelectedLine { it.copy(flicker = checked) }; updateEnabledStates() }
        }
        movementSwitch.setOnCheckedChangeListener { _, checked ->
            if (!syncing) { updateScene(workingScene.copy(movementEnabled = checked)); updateEnabledStates() }
        }
        movementSeek.listen { updateScene(workingScene.copy(movementDp = movementSeek.progress + 1)); updateMovementValues() }
        movementIntervalSeek.listen { updateScene(workingScene.copy(movementSeconds = 30 + movementIntervalSeek.progress * 30)); updateMovementValues() }

        crackSwitch.setOnCheckedChangeListener { _, value -> if (!syncing) updateEffects { it.copy(crackedScreen = value) } }
        crackPatternSpinner.onItemSelectedListener = SimpleItemSelectedListener {
            if (!syncing) updateEffects { it.copy(crackPattern = crackPatterns[crackPatternSpinner.selectedItemPosition].pattern) }
        }
        crackSeek.listen { updateEffects { it.copy(crackStrength = crackSeek.progress) }; updateEffectValues() }
        crackOpacitySeek.listen { updateEffects { it.copy(crackOpacityPercent = crackOpacitySeek.progress + 10) }; updateEffectValues() }
        deadPixelSwitch.setOnCheckedChangeListener { _, value -> if (!syncing) updateEffects { it.copy(deadPixels = value) } }
        deadPixelSeek.listen { updateEffects { it.copy(deadPixelStrength = deadPixelSeek.progress) } }
        liquidSwitch.setOnCheckedChangeListener { _, value -> if (!syncing) updateEffects { it.copy(liquidDamage = value) } }
        liquidSeek.listen { updateEffects { it.copy(liquidStrength = liquidSeek.progress) } }
        ghostSwitch.setOnCheckedChangeListener { _, value -> if (!syncing) updateEffects { it.copy(ghosting = value) } }
        ghostSeek.listen { updateEffects { it.copy(ghostStrength = ghostSeek.progress) } }
        scanlineSwitch.setOnCheckedChangeListener { _, value -> if (!syncing) updateEffects { it.copy(scanlines = value) } }
        scanlineSeek.listen { updateEffects { it.copy(scanlineStrength = scanlineSeek.progress) } }
    }

    private fun syncAllControls() {
        syncing = true
        nameInput.setText(workingScene.name)
        movementSwitch.isChecked = workingScene.movementEnabled
        movementSeek.progress = workingScene.movementDp - 1
        movementIntervalSeek.progress = ((workingScene.movementSeconds - 30) / 30).coerceIn(0, 19)
        crackSwitch.isChecked = workingScene.effects.crackedScreen
        crackPatternSpinner.setSelection(crackPatterns.indexOfFirst { it.pattern == workingScene.effects.crackPattern }.coerceAtLeast(0))
        crackSeek.progress = workingScene.effects.crackStrength
        crackOpacitySeek.progress = workingScene.effects.crackOpacityPercent - 10
        deadPixelSwitch.isChecked = workingScene.effects.deadPixels
        deadPixelSeek.progress = workingScene.effects.deadPixelStrength
        liquidSwitch.isChecked = workingScene.effects.liquidDamage
        liquidSeek.progress = workingScene.effects.liquidStrength
        ghostSwitch.isChecked = workingScene.effects.ghosting
        ghostSeek.progress = workingScene.effects.ghostStrength
        scanlineSwitch.isChecked = workingScene.effects.scanlines
        scanlineSeek.progress = workingScene.effects.scanlineStrength
        preview.updateScene(workingScene, keepSelection = false)
        syncing = false
        syncSelectedLineControls()
        updateMovementValues()
        updateEffectValues()
        updateEnabledStates()
    }

    private fun syncSelectedLineControls() {
        val line = selectedLine() ?: return
        syncing = true
        colorSpinner.setSelection(colors.indexOfFirst { it.color == line.color }.coerceAtLeast(0))
        widthSeek.progress = line.widthDp - 1
        opacitySeek.progress = line.opacityPercent - 10
        glowSeek.progress = line.glowDp
        flickerSwitch.isChecked = line.flicker
        flickerSeek.progress = line.flickerStrength
        syncing = false
        updateSelectedLabels()
        updateLineValues()
        updateEnabledStates()
    }

    private fun addLine() {
        if (workingScene.lines.size >= 12) {
            Toast.makeText(this, R.string.toast_max_lines, Toast.LENGTH_SHORT).show(); return
        }
        val source = selectedLine() ?: workingScene.lines.first()
        val newLine = source.copy(
            id = System.nanoTime(),
            positionPercent = (source.positionPercent + 8f).coerceAtMost(96f),
            color = colors[(workingScene.lines.size) % colors.size].color
        )
        updateScene(workingScene.copy(lines = workingScene.lines + newLine))
        preview.selectLine(newLine.id)
    }

    private fun deleteSelectedLine() {
        val id = preview.selectedLineId ?: return
        if (workingScene.lines.size <= 1) {
            Toast.makeText(this, R.string.toast_min_lines, Toast.LENGTH_SHORT).show(); return
        }
        updateScene(workingScene.copy(lines = workingScene.lines.filterNot { it.id == id }))
        preview.selectLine(workingScene.lines.first().id)
    }

    private fun updateSelectedLine(transform: (DamageLine) -> DamageLine) {
        if (syncing) return
        val id = preview.selectedLineId ?: return
        updateScene(workingScene.copy(lines = workingScene.lines.map { if (it.id == id) transform(it) else it }))
    }

    private fun updateEffects(transform: (DamageEffects) -> DamageEffects) {
        if (syncing) return
        updateScene(workingScene.copy(effects = transform(workingScene.effects)))
        updateEnabledStates()
    }

    private fun updateScene(scene: DamageScene) {
        workingScene = scene
        preview.updateScene(scene)
    }

    private fun selectedLine(): DamageLine? = workingScene.lines.firstOrNull { it.id == preview.selectedLineId }

    private fun updateSelectedLabels() {
        val id = preview.selectedLineId ?: return
        val index = workingScene.lines.indexOfFirst { it.id == id }.coerceAtLeast(0)
        selectedLabel.text = getString(R.string.selected_line_value, index + 1, workingScene.lines.size)
        positionValue.text = getString(R.string.position_decimal_value, workingScene.lines[index].positionPercent)
    }

    private fun updateLineValues() {
        widthValue.text = getString(R.string.width_value, widthSeek.progress + 1)
        opacityValue.text = getString(R.string.percent_value, opacitySeek.progress + 10)
        glowValue.text = getString(R.string.width_value, glowSeek.progress)
        flickerValue.text = getString(R.string.percent_value, flickerSeek.progress)
    }

    private fun updateMovementValues() {
        movementValue.text = getString(R.string.movement_value, movementSeek.progress + 1)
        val seconds = 30 + movementIntervalSeek.progress * 30
        movementIntervalValue.text = when {
            seconds < 60 -> resources.getQuantityString(R.plurals.seconds_value, seconds, seconds)
            seconds % 60 == 0 -> resources.getQuantityString(R.plurals.minutes_value, seconds / 60, seconds / 60)
            else -> getString(
                R.string.time_parts,
                resources.getQuantityString(R.plurals.minutes_value, seconds / 60, seconds / 60),
                resources.getQuantityString(R.plurals.seconds_value, seconds % 60, seconds % 60)
            )
        }
    }

    private fun updateEffectValues() {
        crackStrengthValue.text = getString(R.string.percent_value, crackSeek.progress)
        crackOpacityValue.text = getString(R.string.percent_value, crackOpacitySeek.progress + 10)
    }

    private fun updateEnabledStates() {
        flickerSeek.isEnabled = flickerSwitch.isChecked
        movementSeek.isEnabled = movementSwitch.isChecked
        movementIntervalSeek.isEnabled = movementSwitch.isChecked
        crackSeek.isEnabled = crackSwitch.isChecked
        crackPatternSpinner.isEnabled = crackSwitch.isChecked
        crackOpacitySeek.isEnabled = crackSwitch.isChecked
        deadPixelSeek.isEnabled = deadPixelSwitch.isChecked
        liquidSeek.isEnabled = liquidSwitch.isChecked
        ghostSeek.isEnabled = ghostSwitch.isChecked
        scanlineSeek.isEnabled = scanlineSwitch.isChecked
    }

    private fun saveAndFinish() {
        val name = nameInput.text.toString().trim().ifBlank { getString(R.string.unnamed_scene) }
        workingScene = workingScene.copy(name = name)
        repository.upsert(workingScene)
        repository.activeSceneId = workingScene.id
        if (LineSettings(this).serviceEnabled && Settings.canDrawOverlays(this)) {
            startService(Intent(this, LineOverlayService::class.java).setAction(LineOverlayService.ACTION_REFRESH))
        }
        setResult(RESULT_OK)
        finish()
    }

    private fun openAdvancedEditor() {
        workingScene = workingScene.copy(name = nameInput.text.toString().trim().ifBlank { getString(R.string.unnamed_scene) })
        repository.upsert(workingScene)
        startActivityForResult(
            Intent(this, AdvancedSettingsActivity::class.java).putExtra(AdvancedSettingsActivity.EXTRA_SCENE_ID, workingScene.id),
            REQUEST_ADVANCED
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ADVANCED && resultCode == RESULT_OK) {
            workingScene = repository.find(workingScene.id) ?: workingScene
            syncAllControls()
        }
    }

    private fun SeekBar.listen(action: () -> Unit) {
        setOnSeekBarChangeListener(SimpleSeekListener { if (!syncing) action() })
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18))
        background = rounded(Color.WHITE, 18f, Color.rgb(224, 230, 226)); elevation = dp(1).toFloat()
    }
    private fun effectSwitch(label: String) = Switch(this).apply { text = label; textSize = 16f; setPadding(0, dp(8), 0, 0) }
    private fun effectSeek() = SeekBar(this).apply { max = 100 }
    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 16f; minHeight = dp(56); background = rounded(Color.rgb(232, 238, 234), 14f); setOnClickListener { action() }
    }
    private fun smallButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 14f; minHeight = dp(48); background = rounded(Color.rgb(232, 238, 234), 12f); setOnClickListener { action() }
    }
    private fun sectionTitle(label: String) = textView(label, 19f, Color.rgb(13, 27, 20), Typeface.BOLD).apply { setPadding(0, 0, 0, dp(8)) }
    private fun fieldLabel(label: String) = textView(label, 14f, Color.rgb(82, 95, 88), Typeface.BOLD).apply { setPadding(0, dp(8), 0, 0) }
    private fun fieldHeader(label: String, value: TextView) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(12), 0, 0)
        addView(textView(label, 15f, Color.rgb(65, 78, 71), Typeface.BOLD), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); addView(value)
    }
    private fun valueText() = textView("", 14f, Color.rgb(8, 122, 54), Typeface.BOLD)
    private fun textView(value: String, size: Float, color: Int, style: Int = Typeface.NORMAL) = TextView(this).apply { text = value; textSize = size; setTextColor(color); setTypeface(typeface, style) }
    private fun rounded(fill: Int, radiusDp: Float, stroke: Int? = null) = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(fill); cornerRadius = dp(radiusDp).toFloat(); stroke?.let { setStroke(dp(1), it) } }
    private fun marginBottom() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) }
    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()
    private data class ColorOption(val name: String, val color: Int)
    private data class CrackPatternOption(val name: String, val pattern: CrackPattern)

    companion object {
        const val EXTRA_SCENE_ID = "scene_id"
        private const val REQUEST_ADVANCED = 402
    }
}
