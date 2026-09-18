package app.focusphone.phone

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.focusphone.R
import app.focusphone.bind
import app.focusphone.data.Prefs
import app.focusphone.focus.FocusModeController
import app.focusphone.focus.FocusScheduler
import app.focusphone.focus.FocusService
import app.focusphone.focus.SmsReceiver
import app.focusphone.focus.SmsSentReceiver
import app.focusphone.phone.screens.CallScreen
import app.focusphone.phone.screens.HomeScreen
import app.focusphone.setup.SetupActivity

/**
 * The feature phone. Shown (and pinned) only while a focus session is active.
 * Everything on the LCD is a [Screen]; the on-screen keypad feeds [Key]s into the [ScreenHost].
 */
class FeaturePhoneActivity : Activity() {
    companion object {
        @Volatile
        var isInForeground: Boolean = false
        private const val REQ_CALL = 11
        private const val REQ_SMS = 12
        private const val REQ_CONTACTS = 13
    }

    lateinit var prefs: Prefs
        private set
    lateinit var host: ScreenHost
        private set
    val contacts by lazy { ContactsRepo(this) }
    val sms by lazy { SmsRepo(this) }

    private lateinit var screenContainer: FrameLayout
    private lateinit var batteryView: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var toneGen: ToneGenerator? = null
    private var pendingCall: String? = null
    private var pendingAfterPermission: (() -> Unit)? = null
    private var leaving = false

    private val ticker = object : Runnable {
        override fun run() {
            if (!prefs.isFocusActive()) {
                leaveFocus()
                return
            }
            host.tick()
            updateBattery()
            handler.postDelayed(this, 1000)
        }
    }

    private val callReceiver = app.focusphone.focus.CallStateReceiver()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                FocusScheduler.ACTION_STATE_CHANGED -> {
                    if (!prefs.isFocusActive()) leaveFocus() else host.current.refresh()
                }
                SmsReceiver.ACTION_NEW_SMS -> {
                    val from = intent.getStringExtra(SmsReceiver.EXTRA_FROM) ?: ""
                    val name = contacts.nameFor(from) ?: from
                    lcdToast("New message: $name")
                    beep(ToneGenerator.TONE_PROP_BEEP2)
                    host.current.refresh()
                }
                SmsSentReceiver.ACTION_RESULT -> {
                    val ok = intent.getBooleanExtra(SmsSentReceiver.EXTRA_OK, false)
                    val reason = intent.getStringExtra(SmsSentReceiver.EXTRA_REASON) ?: ""
                    lcdToast(if (ok) "Message sent" else "Send failed: $reason", 2500)
                    if (!ok) beep(ToneGenerator.TONE_PROP_NACK)
                    host.current.refresh()
                }
                CallState.ACTION_CHANGED -> onCallStateChanged()
            }
        }
    }

    // ------------------------------------------------------------ lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        if (!prefs.isFocusActive()) {
            goToSetup()
            return
        }
        setContentView(R.layout.activity_feature_phone)
        hideSystemBars()
        screenContainer = bind<FrameLayout>(R.id.screenContainer)
        batteryView = bind<TextView>(R.id.battery)
        host = ScreenHost(
            this, screenContainer,
            bind<TextView>(R.id.title), bind<TextView>(R.id.leftSoft), bind<TextView>(R.id.rightSoft)
        )
        buildKeypad(bind<LinearLayout>(R.id.keypad))
        host.setRoot(HomeScreen(host))
        updateBattery()
    }

    override fun onStart() {
        super.onStart()
        if (!::host.isInitialized) return
        val f = IntentFilter().apply {
            addAction(FocusScheduler.ACTION_STATE_CHANGED)
            addAction(SmsReceiver.ACTION_NEW_SMS)
            addAction(SmsSentReceiver.ACTION_RESULT)
            addAction(CallState.ACTION_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(receiver, f)
        // Also listen directly: the manifest receiver may lag while the process is cold.
        val pf = IntentFilter(android.telephony.TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(callReceiver, pf, Context.RECEIVER_EXPORTED)
        else registerReceiver(callReceiver, pf)
    }

    override fun onResume() {
        super.onResume()
        isInForeground = true
        if (!::host.isInitialized) return
        if (!prefs.isFocusActive()) {
            leaveFocus()
            return
        }
        if (CallState.isInCall(this)) {
            FocusModeController.suspendForCall(this)
        } else {
            FocusModeController.enter(this)
        }
        FocusService.sync(this)
        onCallStateChanged()
        try {
            toneGen = ToneGenerator(AudioManager.STREAM_SYSTEM, 40)
        } catch (e: Exception) {
            toneGen = null
        }
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        host.current.refresh()
        host.refreshChrome()
    }

    override fun onPause() {
        isInForeground = false
        handler.removeCallbacks(ticker)
        toneGen?.release()
        toneGen = null
        super.onPause()
    }

    override fun onStop() {
        if (::host.isInitialized) {
            for (r in listOf(receiver, callReceiver)) {
                try {
                    unregisterReceiver(r)
                } catch (e: IllegalArgumentException) {
                    // not registered
                }
            }
        }
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::host.isInitialized) hideSystemBars()
    }

    @Deprecated("Back is a phone key here")
    override fun onBackPressed() {
        if (::host.isInitialized) press(Key.SOFT_RIGHT)
        // Never call super: the activity must not be dismissed while focus is active.
    }

    /** Hardware / bluetooth keyboards drive the phone too. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!::host.isInitialized) return super.onKeyDown(keyCode, event)
        val key = when (keyCode) {
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> Key.D0
            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> Key.D1
            KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> Key.D2
            KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> Key.D3
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> Key.D4
            KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> Key.D5
            KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> Key.D6
            KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> Key.D7
            KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> Key.D8
            KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> Key.D9
            KeyEvent.KEYCODE_STAR -> Key.STAR
            KeyEvent.KEYCODE_POUND -> Key.HASH
            KeyEvent.KEYCODE_DPAD_UP -> Key.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> Key.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> Key.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> Key.RIGHT
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> Key.OK
            KeyEvent.KEYCODE_CALL -> Key.CALL
            KeyEvent.KEYCODE_ENDCALL -> Key.END
            KeyEvent.KEYCODE_SOFT_LEFT -> Key.SOFT_LEFT
            KeyEvent.KEYCODE_SOFT_RIGHT, KeyEvent.KEYCODE_DEL -> Key.SOFT_RIGHT
            else -> null
        } ?: return super.onKeyDown(keyCode, event)
        press(key)
        return true
    }

    // ------------------------------------------------------------ keypad

    private fun buildKeypad(keypad: LinearLayout) {
        val rows: List<List<Triple<String, String?, Key>>> = listOf(
            listOf(Triple("—", null, Key.SOFT_LEFT), Triple("▲", null, Key.UP), Triple("—", null, Key.SOFT_RIGHT)),
            listOf(Triple("◀", null, Key.LEFT), Triple("OK", null, Key.OK), Triple("▶", null, Key.RIGHT)),
            listOf(Triple("📞", null, Key.CALL), Triple("▼", null, Key.DOWN), Triple("⏻", null, Key.END)),
            listOf(Triple("1", ".,?!", Key.D1), Triple("2", "ABC", Key.D2), Triple("3", "DEF", Key.D3)),
            listOf(Triple("4", "GHI", Key.D4), Triple("5", "JKL", Key.D5), Triple("6", "MNO", Key.D6)),
            listOf(Triple("7", "PQRS", Key.D7), Triple("8", "TUV", Key.D8), Triple("9", "WXYZ", Key.D9)),
            listOf(Triple("*", "+", Key.STAR), Triple("0", "␣", Key.D0), Triple("#", "⇧", Key.HASH)),
        )
        val m = Lcd.dp(this, 3)
        for (row in rows) {
            val rowView = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            }
            for ((label, sub, key) in row) {
                val bg = when (key) {
                    Key.CALL -> R.drawable.key_bg_call
                    Key.END -> R.drawable.key_bg_end
                    else -> R.drawable.key_bg
                }
                val size = if (key.isDialChar || key == Key.OK) 20 else 16
                val b = KeyButton(this, label, sub, bg, size)
                b.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    setMargins(m, m, m, m)
                }
                b.setOnClickListener { v ->
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    press(key)
                }
                rowView.addView(b)
            }
            keypad.addView(rowView)
        }
    }

    fun press(key: Key) {
        if (prefs.keyTones) {
            val tone = when (key) {
                Key.D0 -> ToneGenerator.TONE_DTMF_0
                Key.D1 -> ToneGenerator.TONE_DTMF_1
                Key.D2 -> ToneGenerator.TONE_DTMF_2
                Key.D3 -> ToneGenerator.TONE_DTMF_3
                Key.D4 -> ToneGenerator.TONE_DTMF_4
                Key.D5 -> ToneGenerator.TONE_DTMF_5
                Key.D6 -> ToneGenerator.TONE_DTMF_6
                Key.D7 -> ToneGenerator.TONE_DTMF_7
                Key.D8 -> ToneGenerator.TONE_DTMF_8
                Key.D9 -> ToneGenerator.TONE_DTMF_9
                Key.STAR -> ToneGenerator.TONE_DTMF_S
                Key.HASH -> ToneGenerator.TONE_DTMF_P
                else -> ToneGenerator.TONE_PROP_BEEP
            }
            beep(tone, 40)
        }
        host.dispatch(key)
    }

    fun beep(tone: Int, ms: Int = 120) {
        try {
            toneGen?.startTone(tone, ms)
        } catch (e: Exception) {
            // no audio, fine
        }
    }

    // ------------------------------------------------------------ actions used by screens

    /** Brief inverted banner at the bottom of the LCD. */
    fun lcdToast(text: String, ms: Long = 1800) {
        if (!::screenContainer.isInitialized) return
        val tv = Lcd.text(this, text, 13f, bold = true, inverted = true, center = true, singleLine = true)
        tv.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
        )
        screenContainer.addView(tv)
        handler.postDelayed({ screenContainer.removeView(tv) }, ms)
    }

    /**
     * A call started ringing, got answered, or ended. The feature phone unpins so the
     * system call UI can appear as a fallback, shows its own call screen, and pins again
     * once the line is idle.
     */
    private fun onCallStateChanged() {
        if (!::host.isInitialized) return
        if (!CallState.isIdle) {
            FocusModeController.suspendForCall(this)
            if (host.current is CallScreen) {
                host.current.refresh()
                host.refreshChrome()
            } else {
                if (CallState.isRinging) beep(ToneGenerator.TONE_SUP_RINGTONE, 600)
                host.push(CallScreen(host))
            }
        } else {
            if (host.current is CallScreen) host.pop()
            if (prefs.isFocusActive() && isInForeground) FocusModeController.enter(this)
        }
    }

    fun placeCall(number: String) {
        val n = number.trim()
        if (n.isEmpty()) return
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            pendingCall = n
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), REQ_CALL)
            return
        }
        lcdToast("Calling $n")
        CallState.number = n
        FocusModeController.suspendForCall(this)
        try {
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(n))))
        } catch (e: Exception) {
            lcdToast("Call failed")
        }
    }

    fun ensureSmsPermission(then: () -> Unit) {
        val need = listOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (need.isEmpty()) {
            then()
        } else {
            pendingAfterPermission = then
            requestPermissions(need.toTypedArray(), REQ_SMS)
        }
    }

    fun ensureContactsPermission(then: () -> Unit) {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            then()
        } else {
            pendingAfterPermission = then
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQ_CONTACTS)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        when (requestCode) {
            REQ_CALL -> {
                val n = pendingCall
                pendingCall = null
                if (granted && n != null) placeCall(n) else lcdToast("No call permission")
            }
            REQ_SMS, REQ_CONTACTS -> {
                val cb = pendingAfterPermission
                pendingAfterPermission = null
                if (granted) cb?.invoke() else lcdToast("Allow it in FocusPhone setup (take a break)", 3000)
                if (::host.isInitialized) host.current.refresh()
            }
        }
    }

    /** The Break button: leave the feature phone for [minutes], focus resumes automatically. */
    fun takeBreak(minutes: Int) {
        FocusScheduler.startBreak(this, minutes)
        leaveFocus()
    }

    fun endSession() {
        FocusScheduler.endSession(this)
        leaveFocus()
    }

    /** Unpin, restore notifications and hand the device back to the normal launcher. */
    private fun leaveFocus() {
        if (leaving) return
        leaving = true
        handler.removeCallbacks(ticker)
        FocusModeController.exit(this)
        try {
            startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            // no launcher? nothing to do
        }
        finish()
    }

    private fun goToSetup() {
        leaving = true
        FocusModeController.exit(this)
        startActivity(Intent(this, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    // ------------------------------------------------------------ chrome

    private fun hideSystemBars() {
        val w = window ?: return
        if (Build.VERSION.SDK_INT >= 30) {
            w.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            w.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
        }
    }

    private fun updateBattery() {
        if (!::batteryView.isInitialized) return
        val bm = getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager ?: return
        val pct = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val bars = when {
            pct <= 0 -> "▯▯▯"
            pct < 20 -> "▮▯▯"
            pct < 60 -> "▮▮▯"
            else -> "▮▮▮"
        }
        batteryView.text = bars
    }
}
