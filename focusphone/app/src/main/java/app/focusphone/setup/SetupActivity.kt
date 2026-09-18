package app.focusphone.setup

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import app.focusphone.R
import app.focusphone.bind
import app.focusphone.data.Prefs
import app.focusphone.data.Schedule
import app.focusphone.focus.FocusModeController
import app.focusphone.focus.FocusScheduler
import app.focusphone.focus.FocusService
import app.focusphone.phone.FeaturePhoneActivity

/** The normal smartphone-style screen: schedules, permissions, options and "start now". */
class SetupActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var statusDetail: TextView
    private lateinit var btnStartNow: Button
    private lateinit var btnOpenPhone: Button
    private lateinit var btnEndSession: Button
    private lateinit var scheduleList: LinearLayout
    private lateinit var permList: LinearLayout
    private lateinit var kioskInfo: TextView

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        FocusService.ensureChannels(this)
        setContentView(R.layout.activity_setup)
        status = bind<TextView>(R.id.status)
        statusDetail = bind<TextView>(R.id.statusDetail)
        btnStartNow = bind<Button>(R.id.btnStartNow)
        btnOpenPhone = bind<Button>(R.id.btnOpenPhone)
        btnEndSession = bind<Button>(R.id.btnEndSession)
        scheduleList = bind<LinearLayout>(R.id.scheduleList)
        permList = bind<LinearLayout>(R.id.permList)
        kioskInfo = bind<TextView>(R.id.kioskInfo)

        btnStartNow.setOnClickListener { askStartNow() }
        btnOpenPhone.setOnClickListener {
            if (prefs.isOnBreak()) {
                AlertDialog.Builder(this)
                    .setTitle("End the break?")
                    .setMessage("Focus mode resumes now instead of at ${FocusScheduler.fmt(prefs.breakUntil)}.")
                    .setPositiveButton("Resume focus") { _, _ -> FocusScheduler.endBreak(this) }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                startActivity(Intent(this, FeaturePhoneActivity::class.java))
            }
        }
        btnEndSession.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("End focus session?")
                .setMessage("The phone stays normal until the next scheduled window.")
                .setPositiveButton("End") { _, _ -> FocusScheduler.endSession(this) }
                .setNegativeButton("Cancel", null)
                .show()
        }
        bind<Button>(R.id.btnAddSchedule).setOnClickListener { addScheduleFlow() }

        bindSwitch(R.id.swKiosk, prefs.kioskEnabled) { prefs.kioskEnabled = it }
        bindSwitch(R.id.swDnd, prefs.dndEnabled) { prefs.dndEnabled = it }
        bindSwitch(R.id.swTones, prefs.keyTones) { prefs.keyTones = it }

        FocusScheduler.reschedule(this)
        // First launch on Android 13+: ask for notifications so the "return to focus" alert can show.
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        val f = IntentFilter(FocusScheduler.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(receiver, f)
        render()
    }

    override fun onPause() {
        try {
            unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            // not registered
        }
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        render()
    }

    private fun bindSwitch(id: Int, initial: Boolean, onChange: (Boolean) -> Unit) {
        val sw = bind<Switch>(id)
        sw.isChecked = initial
        sw.setOnCheckedChangeListener { _, checked -> onChange(checked) }
    }

    // ------------------------------------------------------------ rendering

    private fun render() {
        val now = System.currentTimeMillis()
        val active = prefs.sessionActive && now < prefs.sessionEndAt
        status.text = FocusScheduler.statusText(this)
        statusDetail.text = when {
            prefs.isOnBreak(now) -> "You pressed Break. The feature phone comes back automatically."
            active -> "The feature phone is pinned on screen. Use its Break key to come back here."
            prefs.schedules.none { it.enabled } -> "Add a schedule below or start a session right away."
            else -> "The feature phone will take over automatically."
        }
        btnStartNow.visibility = if (active) View.GONE else View.VISIBLE
        btnOpenPhone.visibility = if (active) View.VISIBLE else View.GONE
        btnEndSession.visibility = if (active) View.VISIBLE else View.GONE
        btnOpenPhone.text = if (prefs.isOnBreak(now)) "Resume focus now" else "Return to focus phone"
        renderSchedules()
        renderPermissions()
    }

    private fun renderSchedules() {
        scheduleList.removeAllViews()
        val list = prefs.schedules
        if (list.isEmpty()) {
            scheduleList.addView(TextView(this).apply {
                text = "No schedules yet."
                setTextColor(getColor(R.color.setup_muted))
                textSize = 14f
            })
            return
        }
        for (s in list) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, dp(8))
            }
            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(this).apply {
                this.text = "${Schedule.formatTime(s.startMinutes)} – ${Schedule.formatTime(s.endMinutes)}"
                setTextColor(getColor(if (s.enabled) R.color.setup_text else R.color.setup_muted))
                textSize = 17f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            textCol.addView(TextView(this).apply {
                this.text = Schedule.formatDays(s.days)
                setTextColor(getColor(R.color.setup_muted))
                textSize = 13f
            })
            row.addView(textCol)
            row.addView(Switch(this).apply {
                isChecked = s.enabled
                setOnCheckedChangeListener { _, checked ->
                    prefs.schedules = prefs.schedules.map { if (it.id == s.id) it.copy(enabled = checked) else it }
                    FocusScheduler.reschedule(this@SetupActivity)
                    render()
                }
            })
            row.addView(Button(this, null, 0, R.style.SetupSmallButton).apply {
                text = "Delete"
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)).also { it.marginStart = dp(12) }
                setOnClickListener {
                    prefs.schedules = prefs.schedules.filter { it.id != s.id }
                    FocusScheduler.reschedule(this@SetupActivity)
                    render()
                }
            })
            scheduleList.addView(row)
        }
    }

    private data class PermRow(val title: String, val why: String, val granted: Boolean, val action: (() -> Unit)?)

    private fun renderPermissions() {
        permList.removeAllViews()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val runtime = listOf(
            Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_CONTACTS
        )
        val rows = ArrayList<PermRow>()
        if (Build.VERSION.SDK_INT >= 33) {
            rows.add(PermRow(
                "Notifications", "Shows the 'return to focus' alert when the phone is left.",
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            ) { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1) })
        }
        rows.add(PermRow(
            "Phone, SMS & contacts", "Lets the feature phone call, text and look up names.",
            runtime.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        ) { requestPermissions(runtime.toTypedArray(), 2) })
        rows.add(PermRow(
            "Do Not Disturb access", "Silences every notification pop-up while phone calls still ring.",
            FocusModeController.hasDndAccess(this)
        ) { safeStart(FocusModeController.dndSettingsIntent()) })
        rows.add(PermRow(
            "Display over other apps", "Lets focus mode bring the feature phone back on top automatically.",
            FocusModeController.hasOverlayPermission(this)
        ) { safeStart(FocusModeController.overlaySettingsIntent(this)) })
        if (Build.VERSION.SDK_INT >= 31) {
            rows.add(PermRow(
                "Exact alarms", "Starts focus time at the exact scheduled minute.",
                FocusScheduler.canScheduleExact(this)
            ) { FocusModeController.exactAlarmSettingsIntent(this)?.let { safeStart(it) } })
        }
        rows.add(PermRow(
            "Ignore battery optimisation", "Keeps the schedule alive when the phone sleeps.",
            pm.isIgnoringBatteryOptimizations(packageName)
        ) { safeStart(FocusModeController.batterySettingsIntent(this)) })

        for (r in rows) permList.addView(permRow(r))

        val owner = FocusModeController.isDeviceOwner(this)
        kioskInfo.text = if (owner) {
            "✓ Device owner: full kiosk. Home, Recents and Back cannot leave the feature phone."
        } else {
            "Screen pinning: Android asks once to pin the app; holding Back + Recents can unpin it. " +
                "For a tamper-proof kiosk make the app device owner (no accounts on the phone, via USB debugging):\n\n" +
                "adb shell dpm set-device-owner app.focusphone/.focus.FocusAdminReceiver"
        }
    }

    private fun permRow(r: PermRow): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textCol.addView(TextView(this).apply {
            this.text = (if (r.granted) "✓ " else "○ ") + r.title
            setTextColor(getColor(if (r.granted) R.color.setup_accent else R.color.setup_text))
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        textCol.addView(TextView(this).apply {
            this.text = r.why
            setTextColor(getColor(R.color.setup_muted))
            textSize = 12f
        })
        row.addView(textCol)
        if (!r.granted && r.action != null) {
            row.addView(Button(this, null, 0, R.style.SetupSmallButton).apply {
                text = "Grant"
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)).also { it.marginStart = dp(12) }
                setOnClickListener { r.action.invoke() }
            })
        }
        return row
    }

    // ------------------------------------------------------------ actions

    private fun askStartNow() {
        val choices = arrayOf("25 minutes", "45 minutes", "1 hour", "90 minutes", "2 hours", "4 hours")
        val minutes = intArrayOf(25, 45, 60, 90, 120, 240)
        AlertDialog.Builder(this)
            .setTitle("Focus for how long?")
            .setItems(choices) { _, which -> startNow(minutes[which]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startNow(minutes: Int) {
        if (prefs.kioskEnabled && !FocusModeController.isDeviceOwner(this)) {
            Toast.makeText(this, "Android will ask to pin the screen — choose OK / Got it.", Toast.LENGTH_LONG).show()
        }
        FocusScheduler.startSessionNow(this, minutes)
        startActivity(Intent(this, FeaturePhoneActivity::class.java))
    }

    private fun addScheduleFlow() {
        val checked = BooleanArray(7) { it < 5 }
        AlertDialog.Builder(this)
            .setTitle("Which days?")
            .setMultiChoiceItems(Schedule.DAY_NAMES.toTypedArray(), checked) { _, i, isChecked -> checked[i] = isChecked }
            .setPositiveButton("Next") { _, _ ->
                var days = 0
                checked.forEachIndexed { i, b -> if (b) days = days or (1 shl i) }
                if (days == 0) {
                    Toast.makeText(this, "Pick at least one day", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                pickTime("Focus starts at", 9, 0) { sh, sm ->
                    pickTime("Focus ends at", 12, 0) { eh, em ->
                        val s = Schedule(System.currentTimeMillis(), sh * 60 + sm, eh * 60 + em, days)
                        prefs.schedules = prefs.schedules + s
                        FocusScheduler.reschedule(this)
                        render()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickTime(title: String, h: Int, m: Int, onPicked: (Int, Int) -> Unit) {
        val d = TimePickerDialog(this, { _, hh, mm -> onPicked(hh, mm) }, h, m, true)
        d.setTitle(title)
        d.show()
    }

    private fun safeStart(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Not available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
}
