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
import android.text.InputType
import android.widget.Button
import android.widget.EditText
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
    companion object {
        private const val REQ_CORE = 1
        private const val REQ_START = 2
    }

    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var statusDetail: TextView
    private lateinit var btnStartNow: Button
    private lateinit var btnOpenPhone: Button
    private lateinit var btnEndSession: Button
    private lateinit var scheduleList: LinearLayout
    private lateinit var permList: LinearLayout
    private lateinit var kioskInfo: TextView
    private lateinit var breakPinStatus: TextView
    private lateinit var autoReturnWarning: TextView
    private lateinit var btnAutoReturn: Button
    private lateinit var btnBreakPin: Button

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = render()
    }

    private var pendingStartMinutes = 0

    private val corePermissions: List<String> = buildList {
        add(Manifest.permission.CALL_PHONE)
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.ANSWER_PHONE_CALLS)
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun missingCorePermissions(): Array<String> =
        corePermissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()

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
        breakPinStatus = bind<TextView>(R.id.breakPinStatus)
        autoReturnWarning = bind<TextView>(R.id.autoReturnWarning)
        btnAutoReturn = bind<Button>(R.id.btnAutoReturn)
        btnAutoReturn.setOnClickListener { safeStart(FocusModeController.overlaySettingsIntent(this)) }
        btnBreakPin = bind<Button>(R.id.btnBreakPin)
        btnBreakPin.setOnClickListener { breakPinFlow() }
        bind<Button>(R.id.btnSecuritySettings).setOnClickListener {
            safeStart(Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS))
        }

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
            requirePin {
                AlertDialog.Builder(this)
                    .setTitle("End focus session?")
                    .setMessage("The phone stays normal until the next scheduled window.")
                    .setPositiveButton("End") { _, _ -> FocusScheduler.endSession(this) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
        bind<Button>(R.id.btnAddSchedule).setOnClickListener { addScheduleFlow() }

        bindSwitch(R.id.swKiosk, prefs.kioskEnabled) { prefs.kioskEnabled = it }
        bindSwitch(R.id.swDnd, prefs.dndEnabled) { prefs.dndEnabled = it }
        bindSwitch(R.id.swTones, prefs.keyTones) { prefs.keyTones = it }

        FocusScheduler.reschedule(this)
        // Ask for the phone / SMS / contacts permissions up front: permission prompts are
        // unreliable once the feature phone is pinned in kiosk mode.
        val missing = missingCorePermissions()
        if (missing.isNotEmpty() && !prefs.askedCorePermissions) {
            prefs.askedCorePermissions = true
            requestPermissions(missing, REQ_CORE)
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
        if (requestCode == REQ_START && pendingStartMinutes > 0) {
            val m = pendingStartMinutes
            pendingStartMinutes = 0
            if (missingCorePermissions().any { it != Manifest.permission.POST_NOTIFICATIONS }) {
                Toast.makeText(this, "Calls or texts may not work until the permissions are granted.", Toast.LENGTH_LONG).show()
            }
            launchFocus(m)
        }
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
        val auto = FocusModeController.canReturnAutomatically(this)
        autoReturnWarning.visibility = if (auto) View.GONE else View.VISIBLE
        btnAutoReturn.visibility = if (auto) View.GONE else View.VISIBLE
        btnStartNow.visibility = if (active) View.GONE else View.VISIBLE
        btnOpenPhone.visibility = if (active) View.VISIBLE else View.GONE
        btnEndSession.visibility = if (active) View.VISIBLE else View.GONE
        btnOpenPhone.text = if (prefs.isOnBreak(now)) "Resume focus now" else "Return to focus phone"
        breakPinStatus.text = if (prefs.hasBreakPin) "Set · asked before a break or ending a session" else "Off · Break works without a PIN"
        btnBreakPin.text = if (prefs.hasBreakPin) "Change" else "Set"
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
        val runtime = corePermissions.filter { it != Manifest.permission.POST_NOTIFICATIONS }
        val rows = ArrayList<PermRow>()
        if (Build.VERSION.SDK_INT >= 33) {
            rows.add(PermRow(
                "Notifications", "Shows the 'return to focus' alert when the phone is left.",
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            ) { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_CORE) })
        }
        rows.add(PermRow(
            "Phone, SMS & contacts", "Answer and make calls, send and read texts, look up names.",
            runtime.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        ) { requestPermissions(runtime.toTypedArray(), REQ_CORE) })
        rows.add(PermRow(
            "Do Not Disturb access", "Silences every notification pop-up while phone calls still ring.",
            FocusModeController.hasDndAccess(this)
        ) { safeStart(FocusModeController.dndSettingsIntent()) })
        rows.add(PermRow(
            "Display over other apps", "REQUIRED for focus to come back by itself after a break or at a scheduled start.",
            FocusModeController.canReturnAutomatically(this)
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
        val missing = missingCorePermissions()
        if (missing.isNotEmpty()) {
            pendingStartMinutes = minutes
            requestPermissions(missing, REQ_START)
            return
        }
        insistOnAutoReturn { launchFocus(minutes) }
    }

    /**
     * Without "Display over other apps" the feature phone cannot come back on its own after a
     * break, so explain and offer the settings page before continuing.
     */
    private fun insistOnAutoReturn(then: () -> Unit) {
        if (FocusModeController.canReturnAutomatically(this)) {
            then()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Allow “Display over other apps”")
            .setMessage(
                "Android blocks apps in the background from opening a screen, so after a break " +
                    "(or at a scheduled start) FocusPhone could only send you a notification.\n\n" +
                    "Allow “Display over other apps” for FocusPhone and it will take over the screen by itself."
            )
            .setPositiveButton("Allow now") { _, _ -> safeStart(FocusModeController.overlaySettingsIntent(this)) }
            .setNegativeButton("Continue anyway") { _, _ -> then() }
            .show()
    }

    private fun launchFocus(minutes: Int) {
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
                        insistOnAutoReturn { }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ------------------------------------------------------------ break PIN

    private fun pinField(): EditText = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        hint = "4–8 digits"
        val p = dp(20)
        setPadding(p, p, p, p)
    }

    /** Runs [action] immediately, or after the current Break PIN has been entered. */
    private fun requirePin(action: () -> Unit) {
        if (!prefs.hasBreakPin) {
            action()
            return
        }
        val field = pinField()
        AlertDialog.Builder(this)
            .setTitle("Enter Break PIN")
            .setView(field)
            .setPositiveButton("OK") { _, _ ->
                if (prefs.pinLockedForSeconds() > 0) {
                    Toast.makeText(this, "Too many tries, wait ${prefs.pinLockedForSeconds()} s", Toast.LENGTH_SHORT).show()
                } else if (prefs.checkBreakPin(field.text.toString())) {
                    action()
                } else {
                    Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun breakPinFlow() {
        requirePin {
            val field = pinField()
            val b = AlertDialog.Builder(this)
                .setTitle(if (prefs.hasBreakPin) "New Break PIN" else "Set Break PIN")
                .setMessage("Asked on the feature phone before a break and before ending a session.")
                .setView(field)
                .setPositiveButton("Save") { _, _ ->
                    val pin = field.text.toString()
                    if (pin.length !in 4..8 || !pin.all { it.isDigit() }) {
                        Toast.makeText(this, "Use 4 to 8 digits", Toast.LENGTH_SHORT).show()
                    } else {
                        prefs.setBreakPin(pin)
                        render()
                    }
                }
                .setNegativeButton("Cancel", null)
            if (prefs.hasBreakPin) b.setNeutralButton("Remove PIN") { _, _ -> prefs.setBreakPin(null); render() }
            b.show()
        }
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
