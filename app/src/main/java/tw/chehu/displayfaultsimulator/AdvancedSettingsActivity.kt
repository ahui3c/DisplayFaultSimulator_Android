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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlin.random.Random

class AdvancedSettingsActivity : Activity() {
    private lateinit var repository: SceneRepository
    private lateinit var workingScene: DamageScene
    private lateinit var preview: DamageSceneView
    private lateinit var impactLabel: TextView
    private lateinit var impactPosition: TextView
    private lateinit var rotationSeek: SeekBar
    private lateinit var branchesSeek: SeekBar
    private lateinit var lengthSeek: SeekBar
    private lateinit var rotationValue: TextView
    private lateinit var branchesValue: TextView
    private lateinit var lengthValue: TextView
    private lateinit var maskSpinner: Spinner
    private lateinit var edgeChipsSwitch: Switch
    private lateinit var shardsSwitch: Switch
    private lateinit var reflectionSwitch: Switch
    private lateinit var parallaxSwitch: Switch
    private lateinit var randomizeSwitch: Switch
    private lateinit var cycleSeek: SeekBar
    private lateinit var cycleValue: TextView
    private lateinit var triggerSceneSpinner: Spinner
    private val panelControls = mutableListOf<PanelControl>()
    private val dynamicControls = mutableListOf<DynamicControl>()
    private var syncing = false
    private val triggerScenes by lazy { listOf<DamageScene?>(null) + repository.scenes() }

    private val masks by lazy { listOf(
        MaskOption(getString(R.string.crack_mask_full), CrackMask.FULL_SCREEN),
        MaskOption(getString(R.string.crack_mask_top_left), CrackMask.TOP_LEFT),
        MaskOption(getString(R.string.crack_mask_edges), CrackMask.SCREEN_EDGES),
        MaskOption(getString(R.string.crack_mask_impacts), CrackMask.AROUND_IMPACTS)
    ) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = SceneRepository(this)
        val id = intent.getStringExtra(EXTRA_SCENE_ID) ?: repository.activeSceneId
        workingScene = repository.find(id) ?: repository.activeScene()
        ensureConcreteImpacts()
        val content = buildContent()
        setContentView(content)
        applySettingsSystemBarInsets(content)
        bindEvents()
        syncAll()
    }

    private fun ensureConcreteImpacts() {
        if (workingScene.effects.crackImpacts.isEmpty()) {
            workingScene = workingScene.copy(effects = workingScene.effects.copy(
                crackImpacts = resolvedCrackImpacts(workingScene).mapIndexed { index, impact -> impact.copy(id = System.nanoTime() + index) }
            ))
        }
    }

    private fun buildContent(): ScrollView {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(245, 247, 246)); isFillViewport = true }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(32)) }
        content.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(textView(getString(R.string.advanced_effects_title), 24f, Color.rgb(13, 27, 20), Typeface.BOLD), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(smallButton(getString(R.string.action_done)) { saveAndFinish() })
        }, marginBottom())

        content.addView(card().apply {
            addView(body(getString(R.string.advanced_preview_description)))
            preview = DamageSceneView(this@AdvancedSettingsActivity).apply {
                editorMode = true
                setBackgroundColor(Color.rgb(26, 32, 29))
                contentDescription = getString(R.string.drag_preview_accessibility)
            }
            addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(430)).apply { topMargin = dp(10) })
        }, marginBottom())

        content.addView(card().apply {
            addView(sectionTitle(getString(R.string.section_crack_geometry)))
            impactLabel = valueText(); addView(impactLabel)
            impactPosition = body(""); addView(impactPosition)
            addView(horizontalButtons(
                smallButton(getString(R.string.add_crack_point)) { addImpact() },
                smallButton(getString(R.string.delete_crack_point)) { deleteImpact() }
            ))
            addView(actionButton(getString(R.string.randomize_cracks)) { randomizeImpacts() })
            rotationValue = valueText(); addView(fieldHeader(getString(R.string.field_crack_rotation), rotationValue))
            rotationSeek = SeekBar(this@AdvancedSettingsActivity).apply { max = 359 }; addView(rotationSeek)
            branchesValue = valueText(); addView(fieldHeader(getString(R.string.field_crack_branches), branchesValue))
            branchesSeek = SeekBar(this@AdvancedSettingsActivity).apply { max = 25 }; addView(branchesSeek)
            lengthValue = valueText(); addView(fieldHeader(getString(R.string.field_crack_length), lengthValue))
            lengthSeek = SeekBar(this@AdvancedSettingsActivity).apply { max = 80 }; addView(lengthSeek)
            addView(fieldLabel(getString(R.string.field_crack_mask)))
            maskSpinner = Spinner(this@AdvancedSettingsActivity).apply {
                adapter = ArrayAdapter(this@AdvancedSettingsActivity, android.R.layout.simple_spinner_dropdown_item, masks.map { it.name })
            }
            addView(maskSpinner)
            edgeChipsSwitch = toggle(R.string.effect_edge_chips); addView(edgeChipsSwitch)
            shardsSwitch = toggle(R.string.effect_glass_shards); addView(shardsSwitch)
            reflectionSwitch = toggle(R.string.effect_glass_reflection); addView(reflectionSwitch)
            parallaxSwitch = toggle(R.string.effect_crack_parallax); addView(parallaxSwitch)
            randomizeSwitch = toggle(R.string.effect_randomize_cracks); addView(randomizeSwitch)
        }, marginBottom())

        content.addView(card().apply {
            addView(sectionTitle(getString(R.string.section_panel_faults)))
            addPanelEffect(this, R.string.effect_oled_black_spot, { it.oledBlackSpot }, { it.oledBlackSpotStrength }, { e, v -> e.copy(oledBlackSpot = v) }, { e, v -> e.copy(oledBlackSpotStrength = v) })
            addPanelEffect(this, R.string.effect_edge_bleed, { it.edgeBleed }, { it.edgeBleedStrength }, { e, v -> e.copy(edgeBleed = v) }, { e, v -> e.copy(edgeBleedStrength = v) })
            addPanelEffect(this, R.string.effect_uneven_brightness, { it.unevenBrightness }, { it.unevenBrightnessStrength }, { e, v -> e.copy(unevenBrightness = v) }, { e, v -> e.copy(unevenBrightnessStrength = v) })
            addPanelEffect(this, R.string.effect_color_shift, { it.colorShift }, { it.colorShiftStrength }, { e, v -> e.copy(colorShift = v) }, { e, v -> e.copy(colorShiftStrength = v) })
            addPanelEffect(this, R.string.effect_pressure_spots, { it.pressureSpots }, { it.pressureSpotStrength }, { e, v -> e.copy(pressureSpots = v) }, { e, v -> e.copy(pressureSpotStrength = v) })
            addPanelEffect(this, R.string.effect_screen_tearing, { it.screenTearing }, { it.tearingStrength }, { e, v -> e.copy(screenTearing = v) }, { e, v -> e.copy(tearingStrength = v) })
            addPanelEffect(this, R.string.effect_partial_blackout, { it.partialBlackout }, { it.blackoutStrength }, { e, v -> e.copy(partialBlackout = v) }, { e, v -> e.copy(blackoutStrength = v) })
            addPanelEffect(this, R.string.effect_intermittent_flash, { it.intermittentFlash }, { it.flashStrength }, { e, v -> e.copy(intermittentFlash = v) }, { e, v -> e.copy(flashStrength = v) })
            addPanelEffect(this, R.string.effect_pwm_bands, { it.pwmBands }, { it.pwmStrength }, { e, v -> e.copy(pwmBands = v) }, { e, v -> e.copy(pwmStrength = v) })
            addPanelEffect(this, R.string.effect_cable_jump, { it.cableJump }, { it.cableJumpStrength }, { e, v -> e.copy(cableJump = v) }, { e, v -> e.copy(cableJumpStrength = v) })
        }, marginBottom())

        content.addView(card().apply {
            addView(sectionTitle(getString(R.string.section_dynamic_scene)))
            addDynamic(this, R.string.dynamic_animated_entry, { it.animatedEntry }, { d, v -> d.copy(animatedEntry = v) })
            addDynamic(this, R.string.dynamic_impact_flash, { it.impactFlash }, { d, v -> d.copy(impactFlash = v) })
            addDynamic(this, R.string.dynamic_expanding_damage, { it.expandingDamage }, { d, v -> d.copy(expandingDamage = v) })
            addDynamic(this, R.string.dynamic_unstable_lines, { it.unstableLines }, { d, v -> d.copy(unstableLines = v) })
            addDynamic(this, R.string.dynamic_timeline, { it.timelineEnabled }, { d, v -> d.copy(timelineEnabled = v) })
            addDynamic(this, R.string.dynamic_random_faults, { it.randomFaults }, { d, v -> d.copy(randomFaults = v) })
            addDynamic(this, R.string.dynamic_cycle_effects, { it.cycleEffects }, { d, v -> d.copy(cycleEffects = v) })
            cycleValue = valueText(); addView(fieldHeader(getString(R.string.dynamic_cycle_interval), cycleValue))
            cycleSeek = SeekBar(this@AdvancedSettingsActivity).apply { max = 58 }; addView(cycleSeek)
            addView(fieldLabel(getString(R.string.dynamic_trigger_scene)))
            triggerSceneSpinner = Spinner(this@AdvancedSettingsActivity).apply {
                adapter = ArrayAdapter(
                    this@AdvancedSettingsActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    triggerScenes.map { it?.name ?: getString(R.string.dynamic_trigger_current_scene) }
                )
            }
            addView(triggerSceneSpinner)
            addDynamic(this, R.string.dynamic_shake_trigger, { it.shakeTrigger }, { d, v -> d.copy(shakeTrigger = v) })
            addDynamic(this, R.string.dynamic_flip_trigger, { it.flipTrigger }, { d, v -> d.copy(flipTrigger = v) })
            addDynamic(this, R.string.dynamic_charge_trigger, { it.chargingTrigger }, { d, v -> d.copy(chargingTrigger = v) })
            addDynamic(this, R.string.dynamic_unlock_trigger, { it.unlockTrigger }, { d, v -> d.copy(unlockTrigger = v) })
        }, marginBottom())

        content.addView(actionButton(getString(R.string.save_and_apply_scene)) { saveAndFinish() }.apply {
            background = rounded(Color.rgb(8, 122, 54), 14f); setTextColor(Color.WHITE)
        })
        scroll.addView(content)
        return scroll
    }

    private fun bindEvents() {
        preview.onSceneChanged = { workingScene = it; updateImpactLabels() }
        preview.onCrackSelectionChanged = { syncImpactControls() }
        rotationSeek.listen { updateImpact { it.copy(rotationDegrees = rotationSeek.progress) }; updateImpactValues() }
        branchesSeek.listen { updateImpact { it.copy(branchCount = branchesSeek.progress + 3) }; updateImpactValues() }
        lengthSeek.listen { updateImpact { it.copy(lengthPercent = lengthSeek.progress + 20) }; updateImpactValues() }
        maskSpinner.onItemSelectedListener = SimpleItemSelectedListener {
            if (!syncing) updateEffects { it.copy(crackMask = masks[maskSpinner.selectedItemPosition].mask) }
        }
        edgeChipsSwitch.bindEffect { e, v -> e.copy(edgeChips = v) }
        shardsSwitch.bindEffect { e, v -> e.copy(glassShards = v) }
        reflectionSwitch.bindEffect { e, v -> e.copy(glassReflection = v) }
        parallaxSwitch.bindEffect { e, v -> e.copy(crackParallax = v) }
        randomizeSwitch.bindEffect { e, v -> e.copy(randomizeCracks = v) }
        panelControls.forEach { control ->
            control.toggle.setOnCheckedChangeListener { _, value ->
                if (!syncing) { updateEffects { control.setEnabled(it, value) }; control.seek.isEnabled = value }
            }
            control.seek.listen { updateEffects { control.setStrength(it, control.seek.progress) }; control.value.text = getString(R.string.percent_value, control.seek.progress) }
        }
        dynamicControls.forEach { control ->
            control.toggle.setOnCheckedChangeListener { _, value -> if (!syncing) updateDynamics { control.setEnabled(it, value) } }
        }
        cycleSeek.listen {
            updateDynamics { it.copy(cycleSeconds = cycleSeek.progress + 2) }
            cycleValue.text = resources.getQuantityString(R.plurals.seconds_value, cycleSeek.progress + 2, cycleSeek.progress + 2)
        }
        triggerSceneSpinner.onItemSelectedListener = SimpleItemSelectedListener {
            if (!syncing) updateDynamics { it.copy(triggerSceneId = triggerScenes[triggerSceneSpinner.selectedItemPosition]?.id) }
        }
    }

    private fun syncAll() {
        syncing = true
        maskSpinner.setSelection(masks.indexOfFirst { it.mask == workingScene.effects.crackMask }.coerceAtLeast(0))
        edgeChipsSwitch.isChecked = workingScene.effects.edgeChips
        shardsSwitch.isChecked = workingScene.effects.glassShards
        reflectionSwitch.isChecked = workingScene.effects.glassReflection
        parallaxSwitch.isChecked = workingScene.effects.crackParallax
        randomizeSwitch.isChecked = workingScene.effects.randomizeCracks
        panelControls.forEach { control ->
            control.toggle.isChecked = control.getEnabled(workingScene.effects)
            control.seek.progress = control.getStrength(workingScene.effects)
            control.seek.isEnabled = control.toggle.isChecked
            control.value.text = getString(R.string.percent_value, control.seek.progress)
        }
        dynamicControls.forEach { it.toggle.isChecked = it.getEnabled(workingScene.dynamics) }
        cycleSeek.progress = workingScene.dynamics.cycleSeconds - 2
        cycleValue.text = resources.getQuantityString(R.plurals.seconds_value, workingScene.dynamics.cycleSeconds, workingScene.dynamics.cycleSeconds)
        triggerSceneSpinner.setSelection(triggerScenes.indexOfFirst { it?.id == workingScene.dynamics.triggerSceneId }.coerceAtLeast(0))
        preview.updateScene(workingScene, keepSelection = false)
        syncing = false
        syncImpactControls()
    }

    private fun syncImpactControls() {
        val impact = selectedImpact() ?: return
        syncing = true
        rotationSeek.progress = impact.rotationDegrees
        branchesSeek.progress = impact.branchCount - 3
        lengthSeek.progress = impact.lengthPercent - 20
        syncing = false
        updateImpactLabels()
        updateImpactValues()
    }

    private fun updateImpactLabels() {
        val impact = selectedImpact() ?: return
        val impacts = workingScene.effects.crackImpacts
        val index = impacts.indexOfFirst { it.id == impact.id }.coerceAtLeast(0)
        impactLabel.text = getString(R.string.selected_crack_point, index + 1, impacts.size)
        impactPosition.text = getString(R.string.crack_position_value, impact.xPercent, impact.yPercent)
    }

    private fun updateImpactValues() {
        rotationValue.text = getString(R.string.degree_value, rotationSeek.progress)
        branchesValue.text = getString(R.string.number_value, branchesSeek.progress + 3)
        lengthValue.text = getString(R.string.percent_value, lengthSeek.progress + 20)
    }

    private fun addImpact() {
        val impacts = workingScene.effects.crackImpacts
        if (impacts.size >= 6) { Toast.makeText(this, R.string.toast_max_cracks, Toast.LENGTH_SHORT).show(); return }
        val source = selectedImpact() ?: impacts.first()
        val added = source.copy(id = System.nanoTime(), xPercent = (source.xPercent + 14f).coerceAtMost(94f), yPercent = (source.yPercent + 12f).coerceAtMost(94f), rotationDegrees = (source.rotationDegrees + 37) % 360, seedOffset = Random.nextInt())
        updateEffects { it.copy(crackImpacts = impacts + added, crackedScreen = true) }
        preview.selectCrack(added.id)
    }

    private fun deleteImpact() {
        val impacts = workingScene.effects.crackImpacts
        if (impacts.size <= 1) { Toast.makeText(this, R.string.toast_min_cracks, Toast.LENGTH_SHORT).show(); return }
        val id = preview.selectedCrackId ?: return
        val remaining = impacts.filterNot { it.id == id }
        updateEffects { it.copy(crackImpacts = remaining) }
        preview.selectCrack(remaining.first().id)
    }

    private fun randomizeImpacts() {
        val randomized = workingScene.effects.crackImpacts.mapIndexed { index, impact -> impact.copy(
            xPercent = Random.nextFloat() * 88f + 6f,
            yPercent = Random.nextFloat() * 86f + 7f,
            rotationDegrees = Random.nextInt(360),
            branchCount = Random.nextInt(8, 23),
            lengthPercent = Random.nextInt(42, 96),
            seedOffset = Random.nextInt() + index
        ) }
        updateEffects { it.copy(crackImpacts = randomized, crackedScreen = true) }
        preview.selectCrack(randomized.first().id)
        syncImpactControls()
    }

    private fun selectedImpact(): CrackImpact? = workingScene.effects.crackImpacts.firstOrNull { it.id == preview.selectedCrackId }
    private fun updateImpact(transform: (CrackImpact) -> CrackImpact) {
        if (syncing) return
        val id = preview.selectedCrackId ?: return
        updateEffects { effects -> effects.copy(crackImpacts = effects.crackImpacts.map { if (it.id == id) transform(it) else it }) }
    }
    private fun updateEffects(transform: (DamageEffects) -> DamageEffects) {
        if (syncing) return
        workingScene = workingScene.copy(effects = transform(workingScene.effects))
        preview.updateScene(workingScene)
    }
    private fun updateDynamics(transform: (SceneDynamics) -> SceneDynamics) {
        if (syncing) return
        workingScene = workingScene.copy(dynamics = transform(workingScene.dynamics))
        preview.updateScene(workingScene)
    }

    private fun saveAndFinish() {
        repository.upsert(workingScene)
        repository.activeSceneId = workingScene.id
        if (LineSettings(this).serviceEnabled && Settings.canDrawOverlays(this)) startService(Intent(this, LineOverlayService::class.java).setAction(LineOverlayService.ACTION_REFRESH))
        setResult(RESULT_OK)
        finish()
    }

    private fun addPanelEffect(parent: LinearLayout, label: Int, enabled: (DamageEffects) -> Boolean, strength: (DamageEffects) -> Int, setEnabled: (DamageEffects, Boolean) -> DamageEffects, setStrength: (DamageEffects, Int) -> DamageEffects) {
        val toggle = toggle(label)
        val value = valueText()
        val seek = SeekBar(this).apply { max = 100 }
        parent.addView(toggle)
        parent.addView(fieldHeader(getString(R.string.field_effect_strength), value))
        parent.addView(seek)
        panelControls += PanelControl(toggle, seek, value, enabled, strength, setEnabled, setStrength)
    }

    private fun addDynamic(parent: LinearLayout, label: Int, getter: (SceneDynamics) -> Boolean, setter: (SceneDynamics, Boolean) -> SceneDynamics) {
        val toggle = toggle(label); parent.addView(toggle); dynamicControls += DynamicControl(toggle, getter, setter)
    }
    private fun Switch.bindEffect(transform: (DamageEffects, Boolean) -> DamageEffects) = setOnCheckedChangeListener { _, value -> if (!syncing) updateEffects { transform(it, value) } }
    private fun SeekBar.listen(action: () -> Unit) = setOnSeekBarChangeListener(SimpleSeekListener { if (!syncing) action() })
    private fun toggle(label: Int) = Switch(this).apply { text = getString(label); textSize = 15.5f; setPadding(0, dp(7), 0, 0) }
    private fun card() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18)); background = rounded(Color.WHITE, 18f, Color.rgb(224, 230, 226)); elevation = dp(1).toFloat() }
    private fun sectionTitle(value: String) = textView(value, 19f, Color.rgb(13, 27, 20), Typeface.BOLD).apply { setPadding(0, 0, 0, dp(8)) }
    private fun fieldLabel(value: String) = textView(value, 14f, Color.rgb(82, 95, 88), Typeface.BOLD).apply { setPadding(0, dp(10), 0, 0) }
    private fun fieldHeader(label: String, value: TextView) = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, 0); addView(textView(label, 14f, Color.rgb(65, 78, 71), Typeface.BOLD), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); addView(value) }
    private fun body(value: String) = textView(value, 13.5f, Color.rgb(82, 95, 88)).apply { setLineSpacing(0f, 1.2f) }
    private fun valueText() = textView("", 14f, Color.rgb(8, 122, 54), Typeface.BOLD)
    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply { text = label; isAllCaps = false; textSize = 15f; minHeight = dp(54); background = rounded(Color.rgb(232, 238, 234), 14f); setOnClickListener { action() }; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(8) } }
    private fun smallButton(label: String, action: () -> Unit) = Button(this).apply { text = label; isAllCaps = false; textSize = 13.5f; minHeight = dp(48); background = rounded(Color.rgb(232, 238, 234), 12f); setOnClickListener { action() } }
    private fun horizontalButtons(vararg buttons: Button) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; buttons.forEachIndexed { index, button -> addView(button, LinearLayout.LayoutParams(0, dp(52), 1f).apply { topMargin = dp(8); if (index > 0) marginStart = dp(8) }) } }
    private fun textView(value: String, size: Float, color: Int, style: Int = Typeface.NORMAL) = TextView(this).apply { text = value; textSize = size; setTextColor(color); setTypeface(typeface, style) }
    private fun rounded(fill: Int, radius: Float, stroke: Int? = null) = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(fill); cornerRadius = dp(radius).toFloat(); stroke?.let { setStroke(dp(1), it) } }
    private fun marginBottom() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()

    private data class MaskOption(val name: String, val mask: CrackMask)
    private data class PanelControl(val toggle: Switch, val seek: SeekBar, val value: TextView, val getEnabled: (DamageEffects) -> Boolean, val getStrength: (DamageEffects) -> Int, val setEnabled: (DamageEffects, Boolean) -> DamageEffects, val setStrength: (DamageEffects, Int) -> DamageEffects)
    private data class DynamicControl(val toggle: Switch, val getEnabled: (SceneDynamics) -> Boolean, val setEnabled: (SceneDynamics, Boolean) -> SceneDynamics)

    companion object { const val EXTRA_SCENE_ID = "scene_id" }
}
