package com.example.battery_float

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * The single app screen: live battery readout, bubble on/off, and a few options.
 * It only listens to battery changes while it is visible, so it costs nothing in the background.
 */
class MainActivity : Activity() {

    private lateinit var settings: AppSettings
    private lateinit var ui: Ui

    private lateinit var gauge: BatteryGaugeView
    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var temperatureValue: TextView
    private lateinit var voltageValue: TextView
    private lateinit var healthValue: TextView
    private lateinit var statePill: TextView
    private lateinit var permissionBox: View
    private lateinit var toggleButton: Button
    private lateinit var notificationCard: View

    private lateinit var sizeBar: SeekBar
    private lateinit var sizeValue: TextView
    private lateinit var opacityBar: SeekBar
    private lateinit var opacityValue: TextView
    private lateinit var snapSwitch: Switch
    private lateinit var restoreSwitch: Switch

    private var lastStatus: BatteryStatus? = null
    private var receiverRegistered = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            render(BatteryStatus.from(intent))
        }
    }

    // Keeps the sliders and switches correct when a setting changes elsewhere, for example when
    // the bubble is resized with two fingers. Kept in a field: SharedPreferences holds it weakly.
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == AppSettings.KEY_SIZE || key == AppSettings.KEY_OPACITY ||
            key == AppSettings.KEY_SNAP || key == AppSettings.KEY_RESTORE
        ) {
            syncSettingsViews()
        }
    }

    // ---------------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AppSettings(this)
        ui = Ui(this)
        setContentView(buildScreen())
    }

    override fun onStart() {
        super.onStart()
        val sticky = registerSystemReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        receiverRegistered = true
        render(BatteryStatus.from(sticky))
        syncSettingsViews()
        settings.register(settingsListener)
        FloatingBatteryService.onStateChanged = { runOnUiThread { refreshControls() } }
        refreshControls()
    }

    override fun onResume() {
        super.onResume()
        // The user may just have come back from a system settings screen.
        refreshControls()
    }

    override fun onStop() {
        if (receiverRegistered) {
            unregisterQuietly(batteryReceiver)
            receiverRegistered = false
        }
        settings.unregister(settingsListener)
        FloatingBatteryService.onStateChanged = null
        super.onStop()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshControls()
    }

    // ---------------------------------------------------------------------------------------
    // Building the screen
    // ---------------------------------------------------------------------------------------

    private fun buildScreen(): View {
        val column = MaxWidthLinearLayout(this, ui.dp(560)).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(20), ui.dp(28), ui.dp(20), ui.dp(32))
        }
        notificationCard = buildNotificationCard()

        column.addView(buildHeader())
        column.addView(buildHeroCard(), ui.wrapParams(24))
        column.addView(buildBubbleCard(), ui.wrapParams(16))
        column.addView(buildCustomizeCard(), ui.wrapParams(16))
        column.addView(notificationCard, ui.wrapParams(16))
        column.addView(buildTipsCard(), ui.wrapParams(16))
        column.addView(buildFooter(), ui.wrapParams(24))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            addView(
                column,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_HORIZONTAL
                )
            )
        }

        return FrameLayout(this).apply {
            setBackgroundColor(Palette.BACKGROUND)
            addView(
                scroll,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            // Android 15+ draws apps edge to edge; keep the content clear of the system bars.
            // On older versions the system already does this and reports no insets here.
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = systemInsets(insets)
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun systemInsets(insets: WindowInsets): Rect =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            Rect(bars.left, bars.top, bars.right, bars.bottom)
        } else {
            Rect(
                insets.systemWindowInsetLeft,
                insets.systemWindowInsetTop,
                insets.systemWindowInsetRight,
                insets.systemWindowInsetBottom
            )
        }

    private fun buildHeader(): View {
        val header = LinearLayout(this)
        header.orientation = LinearLayout.VERTICAL
        header.addView(ui.label(getString(R.string.app_name), 30f, Palette.TEXT_PRIMARY, medium = true))
        header.addView(ui.label(getString(R.string.tagline), 15f, Palette.TEXT_SECONDARY), ui.wrapParams(4))
        return header
    }

    private fun buildHeroCard(): View {
        val card = ui.card()
        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.CENTER_VERTICAL

        gauge = BatteryGaugeView(this)
        card.addView(gauge, LinearLayout.LayoutParams(ui.dp(112), ui.dp(112)))

        statusTitle = ui.label("", 20f, Palette.TEXT_PRIMARY, medium = true)
        statusDetail = ui.label("", 14f, Palette.TEXT_SECONDARY)
        temperatureValue = ui.label("", 14f, Palette.TEXT_PRIMARY, medium = true)
        voltageValue = ui.label("", 14f, Palette.TEXT_PRIMARY, medium = true)
        healthValue = ui.label("", 14f, Palette.TEXT_PRIMARY, medium = true)

        val divider = View(this)
        divider.setBackgroundColor(Palette.CARD_STROKE)

        val info = LinearLayout(this)
        info.orientation = LinearLayout.VERTICAL
        info.addView(statusTitle)
        info.addView(statusDetail, ui.wrapParams(2))
        info.addView(divider, ui.params(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(1), 12))
        info.addView(infoRow(R.string.label_temperature, temperatureValue), ui.wrapParams(8))
        info.addView(infoRow(R.string.label_voltage, voltageValue), ui.wrapParams(4))
        info.addView(infoRow(R.string.label_health, healthValue), ui.wrapParams(4))

        val infoParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        infoParams.marginStart = ui.dp(16)
        card.addView(info, infoParams)
        return card
    }

    private fun infoRow(labelRes: Int, value: TextView): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(
            ui.label(getString(labelRes), 13f, Palette.TEXT_SECONDARY),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        row.addView(value)
        return row
    }

    private fun buildBubbleCard(): View {
        val card = ui.card()

        statePill = ui.label("", 12f, Palette.TEXT_SECONDARY, medium = true)
        statePill.letterSpacing = 0.08f
        statePill.setPadding(ui.dp(12), ui.dp(5), ui.dp(12), ui.dp(5))

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.addView(
            ui.label(getString(R.string.bubble_title), 18f, Palette.TEXT_PRIMARY, medium = true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(statePill)

        permissionBox = buildPermissionBox()
        toggleButton = ui.button("", Palette.GREEN, Palette.GREEN_ON) { onToggleClicked() }

        card.addView(header)
        card.addView(ui.label(getString(R.string.bubble_hint), 14f, Palette.TEXT_SECONDARY), ui.wrapParams(6))
        card.addView(permissionBox, ui.wrapParams(16))
        card.addView(toggleButton, ui.wrapParams(16))
        return card
    }

    private fun buildPermissionBox(): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.background = ui.rounded(Palette.AMBER_TONAL, 16, Palette.AMBER_STROKE, 1)
        box.setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(16))
        box.addView(ui.label(getString(R.string.permission_title), 15f, Palette.AMBER, medium = true))
        box.addView(ui.label(getString(R.string.permission_body), 14f, Palette.TEXT_PRIMARY), ui.wrapParams(4))
        box.addView(
            ui.button(getString(R.string.permission_button), Palette.AMBER, Palette.AMBER_ON) {
                openOverlaySettings()
            },
            ui.wrapParams(12)
        )
        return box
    }

    private fun buildCustomizeCard(): View {
        val card = ui.card()
        card.addView(ui.label(getString(R.string.customize_title), 18f, Palette.TEXT_PRIMARY, medium = true))

        sizeValue = ui.label("", 14f, Palette.GREEN, medium = true)
        sizeBar = SeekBar(this)
        sizeBar.max = AppSettings.MAX_SIZE_DP - AppSettings.MIN_SIZE_DP
        padSeekBar(sizeBar)
        sizeBar.setOnSeekBarChangeListener(onUserProgress { progress ->
            settings.sizeDp = AppSettings.MIN_SIZE_DP + progress
            sizeValue.text = getString(R.string.value_dp, settings.sizeDp)
        })

        opacityValue = ui.label("", 14f, Palette.GREEN, medium = true)
        opacityBar = SeekBar(this)
        opacityBar.max = 100 - AppSettings.MIN_OPACITY
        padSeekBar(opacityBar)
        opacityBar.setOnSeekBarChangeListener(onUserProgress { progress ->
            settings.opacityPercent = AppSettings.MIN_OPACITY + progress
            opacityValue.text = getString(R.string.value_percent, settings.opacityPercent)
        })

        snapSwitch = Switch(this)
        restoreSwitch = Switch(this)

        card.addView(sliderHeader(R.string.size_label, sizeValue), ui.wrapParams(16))
        card.addView(sizeBar, ui.wrapParams(0))
        card.addView(sliderHeader(R.string.opacity_label, opacityValue), ui.wrapParams(8))
        card.addView(opacityBar, ui.wrapParams(0))
        card.addView(
            switchRow(R.string.snap_title, R.string.snap_hint, snapSwitch) { settings.snapToEdge = it },
            ui.wrapParams(12)
        )
        card.addView(
            switchRow(R.string.restore_title, R.string.restore_hint, restoreSwitch) {
                settings.restoreAfterReboot = it
            },
            ui.wrapParams(4)
        )

        syncSettingsViews()
        return card
    }

    /** Makes the sliders, their labels and the switches show what is saved in the settings. */
    private fun syncSettingsViews() {
        sizeBar.progress = settings.sizeDp - AppSettings.MIN_SIZE_DP
        sizeValue.text = getString(R.string.value_dp, settings.sizeDp)
        opacityBar.progress = settings.opacityPercent - AppSettings.MIN_OPACITY
        opacityValue.text = getString(R.string.value_percent, settings.opacityPercent)
        if (snapSwitch.isChecked != settings.snapToEdge) snapSwitch.isChecked = settings.snapToEdge
        if (restoreSwitch.isChecked != settings.restoreAfterReboot) {
            restoreSwitch.isChecked = settings.restoreAfterReboot
        }
    }

    private fun sliderHeader(labelRes: Int, value: TextView): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(
            ui.label(getString(labelRes), 16f, Palette.TEXT_PRIMARY, medium = true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        row.addView(value)
        return row
    }

    /** Gives a slider a comfortable (48dp+) touch height. */
    private fun padSeekBar(bar: SeekBar) {
        bar.setPadding(bar.paddingLeft, ui.dp(10), bar.paddingRight, ui.dp(10))
    }

    private fun onUserProgress(action: (Int) -> Unit): SeekBar.OnSeekBarChangeListener =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) action(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        }

    private fun switchRow(
        titleRes: Int,
        hintRes: Int,
        toggle: Switch,
        onChange: (Boolean) -> Unit
    ): View {
        toggle.contentDescription = getString(titleRes)
        toggle.setOnCheckedChangeListener { _, isOn -> onChange(isOn) }

        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        texts.addView(ui.label(getString(titleRes), 16f, Palette.TEXT_PRIMARY, medium = true))
        texts.addView(ui.label(getString(hintRes), 13f, Palette.TEXT_SECONDARY), ui.wrapParams(2))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.minimumHeight = ui.dp(56)
        row.setPadding(0, ui.dp(8), 0, ui.dp(8))
        row.setOnClickListener { toggle.toggle() }
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val toggleParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        toggleParams.marginStart = ui.dp(12)
        row.addView(toggle, toggleParams)
        return row
    }

    private fun buildNotificationCard(): View {
        val card = ui.card()
        card.addView(ui.label(getString(R.string.notif_card_title), 18f, Palette.TEXT_PRIMARY, medium = true))
        card.addView(ui.label(getString(R.string.notif_card_body), 14f, Palette.TEXT_SECONDARY), ui.wrapParams(6))
        card.addView(
            ui.button(getString(R.string.notif_card_button), Palette.FIELD, Palette.TEXT_PRIMARY) {
                requestNotificationPermission()
            },
            ui.wrapParams(16)
        )
        return card
    }

    private fun buildTipsCard(): View {
        val card = ui.card()
        card.addView(ui.label(getString(R.string.tips_title), 18f, Palette.TEXT_PRIMARY, medium = true))
        val tips = listOf(
            R.string.tip_drag_name to R.string.tip_drag,
            R.string.tip_tap_name to R.string.tip_tap,
            R.string.tip_pinch_name to R.string.tip_pinch,
            R.string.tip_hold_name to R.string.tip_hold,
        )
        for ((index, tip) in tips.withIndex()) {
            val name = ui.label(getString(tip.first), 14f, Palette.GREEN, medium = true)
            name.minWidth = ui.dp(64)
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.addView(name)
            row.addView(
                ui.label(getString(tip.second), 14f, Palette.TEXT_SECONDARY),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            card.addView(row, ui.wrapParams(if (index == 0) 12 else 8))
        }
        return card
    }

    private fun buildFooter(): View {
        val footer = ui.label(footerText(), 12f, Palette.GRAY)
        footer.gravity = Gravity.CENTER
        return footer
    }

    @Suppress("DEPRECATION")
    private fun footerText(): String {
        val info = try {
            packageManager.getPackageInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        val name = info?.versionName ?: "?"
        val code: Long = when {
            info == null -> 0L
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> info.longVersionCode
            else -> info.versionCode.toLong()
        }
        return getString(R.string.footer, name, code)
    }

    // ---------------------------------------------------------------------------------------
    // State
    // ---------------------------------------------------------------------------------------

    private fun render(status: BatteryStatus) {
        if (status == lastStatus) return
        lastStatus = status

        gauge.setStatus(status.percent, status.charging, status.full)
        statusTitle.setText(
            when {
                !status.known -> R.string.hero_unknown
                status.charging -> R.string.hero_charging
                status.full -> R.string.hero_full
                status.pluggedIn -> R.string.hero_plugged
                else -> R.string.hero_battery
            }
        )
        statusDetail.text = when {
            !status.known -> getString(R.string.detail_unknown)
            status.pluggedIn -> getString(R.string.detail_source, sourceLabel(status.plugged))
            else -> getString(R.string.detail_discharging)
        }
        val none = getString(R.string.value_none)
        temperatureValue.text = status.temperatureCelsius?.let { getString(R.string.value_temperature, it) } ?: none
        voltageValue.text = status.voltageVolts?.let { getString(R.string.value_voltage, it) } ?: none
        healthValue.setText(healthRes(status.health))
    }

    private fun sourceLabel(plugged: Int): String = getString(
        when {
            plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> R.string.source_wireless
            plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> R.string.source_ac
            plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> R.string.source_usb
            plugged and BatteryStatus.PLUGGED_DOCK != 0 -> R.string.source_dock
            else -> R.string.source_other
        }
    )

    private fun healthRes(health: Int): Int = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> R.string.health_good
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> R.string.health_overheat
        BatteryManager.BATTERY_HEALTH_DEAD -> R.string.health_dead
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> R.string.health_over_voltage
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> R.string.health_failure
        BatteryManager.BATTERY_HEALTH_COLD -> R.string.health_cold
        else -> R.string.health_unknown
    }

    /** Syncs buttons, pill and permission cards with the current state. */
    private fun refreshControls() {
        val running = FloatingBatteryService.isRunning
        val canDraw = FloatingBatteryService.canDrawOverlays(this)
        if (!canDraw && !running) settings.bubbleWanted = false

        statePill.setText(if (running) R.string.state_on else R.string.state_off)
        statePill.setTextColor(if (running) Palette.GREEN else Palette.TEXT_SECONDARY)
        statePill.background = ui.rounded(if (running) Palette.GREEN_TONAL else Palette.FIELD, 20)

        permissionBox.visibility = if (canDraw) View.GONE else View.VISIBLE
        toggleButton.visibility = if (canDraw) View.VISIBLE else View.GONE
        toggleButton.setText(if (running) R.string.stop_bubble else R.string.start_bubble)
        toggleButton.setTextColor(if (running) Palette.RED_ON else Palette.GREEN_ON)
        toggleButton.background = ui.pressable(if (running) Palette.RED else Palette.GREEN, 16)

        notificationCard.visibility = if (needsNotificationPermission()) View.VISIBLE else View.GONE
    }

    // ---------------------------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------------------------

    private fun onToggleClicked() {
        if (FloatingBatteryService.isRunning) {
            settings.bubbleWanted = false
            FloatingBatteryService.stop(this)
        } else {
            settings.bubbleWanted = true
            if (!FloatingBatteryService.start(this)) {
                settings.bubbleWanted = false
                toast(R.string.error_start_failed)
            }
        }
        refreshControls()
    }

    private fun openOverlaySettings() {
        val packageUri = Uri.parse("package:$packageName")
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri))
        } catch (e: RuntimeException) {
            openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
        }
    }

    private fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (settings.notificationAsked && !shouldShowRequestPermissionRationale(permission)) {
            // Android will not show the dialog again, so send the user to the system screen.
            openSettings(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        } else {
            settings.notificationAsked = true
            requestPermissions(arrayOf(permission), REQUEST_NOTIFICATIONS)
        }
    }

    private fun openSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: RuntimeException) {
            toast(R.string.error_open_settings)
        }
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val REQUEST_NOTIFICATIONS = 1
    }
}
