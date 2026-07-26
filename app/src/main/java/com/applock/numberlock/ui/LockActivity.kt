package com.applock.numberlock.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.applock.numberlock.R
import com.applock.numberlock.data.LockSessionManager
import com.applock.numberlock.data.PrefsManager
import com.applock.numberlock.databinding.ActivityLockBinding
import com.applock.numberlock.util.PinKeypadBinder
import com.applock.numberlock.util.shake
import com.applock.numberlock.util.updatePinDots

private const val PIN_LENGTH = 4

/**
 * Full-screen PIN prompt shown on top of a locked app. Launched by
 * [com.applock.numberlock.service.AppLockAccessibilityService] whenever the
 * foreground app changes to one the user has locked.
 */
class LockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockBinding
    private lateinit var prefsManager: PrefsManager
    private var enteredPin = StringBuilder()
    private var targetPackage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefsManager = PrefsManager.getInstance(this)
        targetPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        binding.lockedAppLabel.text = labelFor(targetPackage)

        PinKeypadBinder.bind(
            root = binding.keypad.root,
            onDigit = { digit -> onDigitEntered(digit) },
            onBackspace = { onBackspace() }
        )
    }

    @Suppress("DEPRECATION")
    private fun labelFor(packageName: String?): String {
        if (packageName == null) return getString(R.string.app_name)
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun onDigitEntered(digit: Char) {
        if (enteredPin.length >= PIN_LENGTH) return
        enteredPin.append(digit)
        updatePinDots(dotViews(), enteredPin.length)
        if (enteredPin.length == PIN_LENGTH) {
            checkPin(enteredPin.toString())
        }
    }

    private fun onBackspace() {
        if (enteredPin.isNotEmpty()) {
            enteredPin.deleteCharAt(enteredPin.length - 1)
            updatePinDots(dotViews(), enteredPin.length)
        }
    }

    private fun checkPin(pin: String) {
        if (prefsManager.verifyPin(pin)) {
            targetPackage?.let { LockSessionManager.markUnlocked(it) }
            finish()
        } else {
            Toast.makeText(this, R.string.pin_incorrect, Toast.LENGTH_SHORT).show()
            shake(binding.pinDotsContainer)
            enteredPin = StringBuilder()
            updatePinDots(dotViews(), 0)
        }
    }

    private fun dotViews() = listOf(binding.dot1, binding.dot2, binding.dot3, binding.dot4)

    /** Block bypassing the lock via back navigation: send the user home instead. */
    override fun onBackPressed() {
        goHome()
    }

    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
    }
}
