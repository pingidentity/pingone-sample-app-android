package com.pingidentity.pingone

import android.content.DialogInterface
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.pingidentity.pingidsdkv2.PingOne
import com.pingidentity.pingidsdkv2.PingOneSDKException
import com.pingidentity.pingidsdkv2.types.OneTimePasscodeInfo
import kotlinx.coroutines.launch

class TOTPActivity : SampleActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var passcode: TextView
    private lateinit var passcodeTimer: TextView
    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_totp)
        passcode = findViewById(R.id.tv_passcode)
        passcodeTimer = findViewById(R.id.tv_timer)
    }

    override fun onDestroy() {
        countDownTimer?.cancel()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        startPassCodeSequence()
    }

    override fun onPause() {
        super.onPause()
        countDownTimer?.cancel()
    }

    private fun startPassCodeSequence() {
        if (getPairingStatus()) {
            lifecycleScope.launch {
                try {
                    val otpData = PingOne.getOneTimePassCode(applicationContext)
                    if (otpData != null) {
                        startAnimation(otpData)
                    } else {
                        passcode.text = "Passcode error"
                        passcodeTimer.text = ""
                    }
                } catch (e: PingOneSDKException) {
                    showOkDialog(e.error.toString())
                    passcode.text = "Passcode error"
                    passcodeTimer.text = ""
                }
            }
        } else {
            passcodeTimer.text = ""
            passcode.text = "Not Initialized"
        }
    }

    private fun getPairingStatus(): Boolean =
        getSharedPreferences("InternalPrefs", MODE_PRIVATE).getBoolean("paired", false)

    private fun startAnimation(otpData: OneTimePasscodeInfo) {
        val runTime = (otpData.validUntil * 1000 - System.currentTimeMillis()).toLong()
        handler.post {
            // Cancel any previously running timer before starting a new one. Without this,
            // each onResume() while a timer is already running leaks an extra CountDownTimer
            // whose onFinish() fires startPassCodeSequence() concurrently with the new one,
            // causing duplicate getOneTimePassCode() calls that multiply on every resume cycle.
            countDownTimer?.cancel()
            passcode.text = otpData.passcode
            countDownTimer = object : CountDownTimer(runTime, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    passcodeTimer.text = "${millisUntilFinished / 1000}s"
                }

                override fun onFinish() {
                    startPassCodeSequence()
                }
            }.start()
        }
    }

    private fun showOkDialog(message: String) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
            .show()
            .getButton(DialogInterface.BUTTON_POSITIVE)
            .setContentDescription(getString(R.string.alert_dialog_button_ok))
    }
}


