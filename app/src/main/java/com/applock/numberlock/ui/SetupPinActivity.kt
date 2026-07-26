package com.applock.numberlock.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.applock.numberlock.R
import com.applock.numberlock.data.PrefsManager
import com.applock.numberlock.databinding.ActivitySetupPinBinding
import com.applock.numberlock.util.PinKeypadBinder
import com.applock.numberlock.util.shake
import com.applock.numberlock.util.updatePinDots

private const val PIN_LENGTH = 4

private enum class Step { VERIFY_CURRENT, ENTER_NEW, CONFIRM_NEW }

class SetupPinActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupPinBinding
    private lateinit var prefsManager: PrefsManager

    private var step: Step = Step.ENTER_NEW
    private var enteredPin = StringBuilder()
    private var firstNewPin: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupPinBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefsManager = PrefsManager.getInstance(this)

        val isChangingPin = intent.getBooleanExtra(EXTRA_CHANGE_PIN, false)
        step = if (isChangingPin && prefsManager.isPinSet()) Step.VERIFY_CURRENT else Step.ENTER_NEW
        updatePrompt()

        PinKeypadBinder.bind(
            root = binding.keypad.root,
            onDigit = { digit -> onDigitEntered(digit) },
            onBackspace = { onBackspace() }
        )
    }

    private fun onDigitEntered(digit: Char) {
        if (enteredPin.length >= PIN_LENGTH) return
        enteredPin.append(digit)
        updatePinDots(dotViews(), enteredPin.length)
        if (enteredPin.length == PIN_LENGTH) {
            handlePinComplete(enteredPin.toString())
        }
    }

    private fun onBackspace() {
        if (enteredPin.isNotEmpty()) {
            enteredPin.deleteCharAt(enteredPin.length - 1)
            updatePinDots(dotViews(), enteredPin.length)
        }
    }

    private fun handlePinComplete(pin: String) {
        when (step) {
            Step.VERIFY_CURRENT -> {
                if (prefsManager.verifyPin(pin)) {
                    step = Step.ENTER_NEW
                    resetInput()
                    updatePrompt()
                } else {
                    failAndReset(getString(R.string.pin_incorrect))
                }
            }
            Step.ENTER_NEW -> {
                firstNewPin = pin
                step = Step.CONFIRM_NEW
                resetInput()
                updatePrompt()
            }
            Step.CONFIRM_NEW -> {
                if (pin == firstNewPin) {
                    prefsManager.setPin(pin)
                    Toast.makeText(this, R.string.pin_saved, Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    firstNewPin = null
                    step = Step.ENTER_NEW
                    failAndReset(getString(R.string.pin_mismatch))
                }
            }
        }
    }

    private fun failAndReset(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        shake(binding.pinDotsContainer)
        resetInput()
        updatePrompt()
    }

    private fun resetInput() {
        enteredPin = StringBuilder()
        updatePinDots(dotViews(), 0)
    }

    private fun dotViews() = listOf(binding.dot1, binding.dot2, binding.dot3, binding.dot4)

    private fun updatePrompt() {
        binding.pinPrompt.text = when (step) {
            Step.VERIFY_CURRENT -> getString(R.string.enter_current_pin)
            Step.ENTER_NEW -> getString(R.string.enter_new_pin)
            Step.CONFIRM_NEW -> getString(R.string.confirm_new_pin)
        }
    }

    companion object {
        const val EXTRA_CHANGE_PIN = "extra_change_pin"
    }
}
