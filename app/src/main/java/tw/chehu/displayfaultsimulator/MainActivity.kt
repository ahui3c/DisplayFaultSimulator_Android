package tw.chehu.displayfaultsimulator

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var settingsStore: LineSettings
    private lateinit var repository: SceneRepository
    private lateinit var sceneSpinner: Spinner
    private lateinit var presetSpinner: Spinner
    private lateinit var permissionState: TextView
    private lateinit var batteryState: TextView
    private lateinit var serviceState: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var delaySpinner: Spinner
    private lateinit var durationSpinner: Spinner
    private lateinit var bootCheck: CheckBox
    private var sceneItems: List<DamageScene> = emptyList()
    private var presetItems: List<DamageScene> = emptyList()
    private var syncingSceneSpinner = false

    private val delayOptions by lazy { listOf(
        TimeOption(getString(R.string.time_immediately), 0),
        TimeOption(getString(R.string.time_after_5_seconds), 5),
        TimeOption(getString(R.string.time_after_10_seconds), 10),
        TimeOption(getString(R.string.time_after_30_seconds), 30),
        TimeOption(getString(R.string.time_after_1_minute), 60),
        TimeOption(getString(R.string.time_after_5_minutes), 300)
    ) }
    private val durationOptions by lazy { listOf(
        TimeOption(getString(R.string.duration_manual), 0),
        TimeOption(getString(R.string.duration_1_minute), 60),
        TimeOption(getString(R.string.duration_5_minutes), 300),
        TimeOption(getString(R.string.duration_15_minutes), 900),
        TimeOption(getString(R.string.duration_30_minutes), 1_800),
        TimeOption(getString(R.string.duration_1_hour), 3_600)
    ) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsStore = LineSettings(this)
        repository = SceneRepository(this)
        setContentView(buildContent())
        bindEvents()
    }

    override fun onResume() {
        super.onResume()
        reloadScenes()
        updateStatus()
    }

    private fun buildContent(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(245, 247, 246))
        }
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(22), dp(24), dp(20))
            background = rounded(Color.rgb(13, 27, 20), 0f)
            addView(textView(getString(R.string.app_name), 27f, Color.WHITE, Typeface.BOLD))
            addView(textView(getString(R.string.app_tagline), 14f, Color.rgb(176, 212, 188)).apply {
                setPadding(0, dp(4), 0, 0)
            })
        })

        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(32))
        }

        content.addView(card().apply {
            addView(sectionTitle(getString(R.string.section_safety)))
            addView(body(getString(R.string.safety_description)))
        }, marginBottom())

        content.addView(card().apply {
            addView(sectionTitle(getString(R.string.section_required_settings)))
            permissionState = statusText()
            addView(permissionState)
            addView(actionButton(getString(R.string.grant_overlay_permission)) { openOverlaySettings() })
            batteryState = statusText().apply { setPadding(0, dp(16), 0, 0) }
            addView(batteryState)
            addView(actionButton(getString(R.string.open_battery_optimization)) { openBatterySettings() })
            bootCheck = CheckBox(this@MainActivity).apply {
                text = getString(R.string.restore_after_boot)
                textSize = 15f
                setTextColor(Color.rgb(31, 41, 36))
                setPadding(0, dp(12), 0, 0)
                isChecked = settingsStore.startOnBoot
            }
            addView(bootCheck)
        }, marginBottom())

        content.addView(card().apply {
            addView(sectionTitle(getString(R.string.section_damage_scenes)))
            sceneSpinner = Spinner(this@MainActivity)
            addView(sceneSpinner, matchWrap())
            addView(actionButton(getString(R.string.open_drag_editor)) { editActiveScene() }.apply {
                background = rounded(Color.rgb(8, 122, 54), 14f)
                setTextColor(Color.WHITE)
            })
            addView(horizontalButtons(
                actionButton(getString(R.string.action_new)) { createScene() },
                actionButton(getString(R.string.action_duplicate)) { duplicateScene() },
                actionButton(getString(R.string.action_delete)) { deleteScene() }
            ))
            addView(body(getString(R.string.scene_editor_description)))
        }, marginBottom())

        content.addView(card().apply {
            addView(sectionTitle(getString(R.string.section_preset_library)))
            addView(body(getString(R.string.preset_library_description)))
            presetItems = ScenePresets.all(this@MainActivity)
            presetSpinner = Spinner(this@MainActivity).apply {
                adapter = ArrayAdapter(
                    this@MainActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    presetItems.map { it.name }
                )
            }
            addView(presetSpinner, matchWrap())
            addView(actionButton(getString(R.string.apply_preset)) { applyPreset() }.apply {
                background = rounded(Color.rgb(232, 238, 234), 14f)
            })
        }, marginBottom())

        content.addView(card().apply {
            addView(sectionTitle(getString(R.string.section_schedule)))
            addView(fieldLabel(getString(R.string.start_time)))
            delaySpinner = Spinner(this@MainActivity).apply {
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, delayOptions.map { it.label })
            }
            addView(delaySpinner, matchWrap())
            addView(fieldLabel(getString(R.string.stop_time)))
            durationSpinner = Spinner(this@MainActivity).apply {
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, durationOptions.map { it.label })
            }
            addView(durationSpinner, matchWrap())
        }, marginBottom())

        content.addView(card().apply {
            addView(sectionTitle(getString(R.string.section_quick_tile)))
            addView(body(getString(R.string.quick_tile_description)))
        }, marginBottom())

        content.addView(card().apply {
            addView(sectionTitle(getString(R.string.section_online_update)))
            addView(body(getString(R.string.online_update_main_description)))
            addView(actionButton(getString(R.string.check_online_update)) {
                startActivity(Intent(this@MainActivity, OnlineUpdateActivity::class.java))
            }.apply {
                background = rounded(Color.rgb(232, 238, 234), 14f)
            })
        }, marginBottom())

        content.addView(card().apply {
            addView(sectionTitle(getString(R.string.section_display_control)))
            serviceState = statusText()
            addView(serviceState)
            startButton = actionButton(getString(R.string.start_or_schedule)) { startOverlay() }.apply {
                background = rounded(Color.rgb(8, 122, 54), 14f)
                setTextColor(Color.WHITE)
            }
            addView(startButton)
            stopButton = actionButton(getString(R.string.stop_all_effects)) { stopOverlay() }
            addView(stopButton)
            addView(textView(getString(R.string.force_stop_note), 12.5f, Color.rgb(100, 112, 106)).apply {
                setPadding(0, dp(12), 0, 0)
            })
        })

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun bindEvents() {
        bootCheck.setOnCheckedChangeListener { _, checked -> settingsStore.startOnBoot = checked }
        sceneSpinner.onItemSelectedListener = SimpleItemSelectedListener {
            if (!syncingSceneSpinner && sceneItems.isNotEmpty()) {
                repository.activeSceneId = sceneItems[sceneSpinner.selectedItemPosition].id
                refreshRunningScene()
            }
        }
    }

    private fun reloadScenes() {
        if (!::sceneSpinner.isInitialized) return
        sceneItems = repository.scenes()
        syncingSceneSpinner = true
        sceneSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sceneItems.map { it.name })
        sceneSpinner.setSelection(sceneItems.indexOfFirst { it.id == repository.activeSceneId }.coerceAtLeast(0))
        syncingSceneSpinner = false
    }

    private fun createScene() {
        val scene = repository.create()
        openEditor(scene.id)
    }

    private fun duplicateScene() {
        val scene = repository.duplicate(repository.activeScene())
        openEditor(scene.id)
    }

    private fun deleteScene() {
        if (!repository.delete(repository.activeSceneId)) {
            Toast.makeText(this, R.string.toast_keep_one_scene, Toast.LENGTH_SHORT).show()
            return
        }
        reloadScenes()
        refreshRunningScene()
    }

    private fun editActiveScene() = openEditor(repository.activeSceneId)

    private fun applyPreset() {
        val preset = presetItems[presetSpinner.selectedItemPosition]
        repository.createFromPreset(preset)
        reloadScenes()
        refreshRunningScene()
        Toast.makeText(this, getString(R.string.toast_preset_applied, preset.name), Toast.LENGTH_SHORT).show()
    }

    private fun openEditor(sceneId: String) {
        startActivity(Intent(this, SceneEditorActivity::class.java).putExtra(SceneEditorActivity.EXTRA_SCENE_ID, sceneId))
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.toast_overlay_required, Toast.LENGTH_LONG).show()
            openOverlaySettings()
            return
        }
        requestNotificationPermissionIfNeeded()
        val delay = delayOptions[delaySpinner.selectedItemPosition].seconds
        val duration = durationOptions[durationSpinner.selectedItemPosition].seconds
        val intent = Intent(this, LineOverlayService::class.java)
            .setAction(LineOverlayService.ACTION_START)
            .putExtra(LineOverlayService.EXTRA_DELAY_SECONDS, delay)
            .putExtra(LineOverlayService.EXTRA_DURATION_SECONDS, duration)
        startForegroundService(intent)
        settingsStore.serviceEnabled = true
        updateStatus()
    }

    private fun stopOverlay() {
        settingsStore.serviceEnabled = false
        settingsStore.clearSchedule()
        startService(Intent(this, LineOverlayService::class.java).setAction(LineOverlayService.ACTION_STOP))
        updateStatus()
    }

    private fun refreshRunningScene() {
        if (settingsStore.serviceEnabled && Settings.canDrawOverlays(this)) {
            startService(Intent(this, LineOverlayService::class.java).setAction(LineOverlayService.ACTION_REFRESH))
        }
    }

    private fun openOverlaySettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }.onFailure { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) }
    }

    private fun openBatterySettings() {
        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 201)
    }

    private fun updateStatus() {
        if (!::permissionState.isInitialized) return
        val overlayGranted = Settings.canDrawOverlays(this)
        val batteryGranted = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        permissionState.text = getString(if (overlayGranted) R.string.status_overlay_granted else R.string.status_overlay_missing)
        permissionState.setTextColor(if (overlayGranted) Color.rgb(8, 122, 54) else Color.rgb(185, 74, 54))
        batteryState.text = getString(if (batteryGranted) R.string.status_battery_unrestricted else R.string.status_battery_suggestion)
        batteryState.setTextColor(if (batteryGranted) Color.rgb(8, 122, 54) else Color.rgb(151, 105, 22))
        val enabled = settingsStore.serviceEnabled && overlayGranted
        val now = System.currentTimeMillis()
        serviceState.text = when {
            !enabled -> getString(R.string.status_effect_stopped)
            settingsStore.pendingStartAt > now -> getString(R.string.status_effect_scheduled)
            settingsStore.autoStopAt > now -> getString(R.string.status_effect_auto_stop)
            else -> getString(R.string.status_effect_active)
        }
        serviceState.setTextColor(if (enabled) Color.rgb(8, 122, 54) else Color.rgb(100, 112, 106))
        startButton.isEnabled = !enabled
        stopButton.isEnabled = enabled
    }

    private fun horizontalButtons(vararg buttons: Button) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        buttons.forEachIndexed { index, button ->
            button.layoutParams = LinearLayout.LayoutParams(0, dp(54), 1f).apply {
                topMargin = dp(8)
                if (index > 0) marginStart = dp(8)
            }
            addView(button)
        }
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(20), dp(20), dp(20))
        background = rounded(Color.WHITE, 18f, Color.rgb(224, 230, 226))
        elevation = dp(1).toFloat()
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 15f; isAllCaps = false
        setTextColor(Color.rgb(31, 41, 36))
        background = rounded(Color.rgb(232, 238, 234), 14f)
        minHeight = dp(52)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(10) }
    }

    private fun sectionTitle(label: String) = textView(label, 19f, Color.rgb(13, 27, 20), Typeface.BOLD).apply { setPadding(0, 0, 0, dp(10)) }
    private fun fieldLabel(label: String) = textView(label, 14f, Color.rgb(82, 95, 88), Typeface.BOLD).apply { setPadding(0, dp(12), 0, dp(2)) }
    private fun statusText() = textView("", 14f, Color.rgb(100, 112, 106), Typeface.BOLD)
    private fun body(value: String) = textView(value, 14f, Color.rgb(82, 95, 88)).apply { setLineSpacing(0f, 1.25f); setPadding(0, dp(5), 0, 0) }
    private fun textView(value: String, size: Float, color: Int, style: Int = Typeface.NORMAL) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); setTypeface(typeface, style)
    }
    private fun rounded(fill: Int, radiusDp: Float, stroke: Int? = null) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; setColor(fill); cornerRadius = dp(radiusDp).toFloat(); stroke?.let { setStroke(dp(1), it) }
    }
    private fun marginBottom() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) }
    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()
    private data class TimeOption(val label: String, val seconds: Int)
}
