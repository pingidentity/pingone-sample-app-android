package com.pingidentity.pingone

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pingidentity.authenticationui.PingAuthenticationUI
import com.pingidentity.pingidsdkv2.PairingObject
import com.pingidentity.pingidsdkv2.PingOne
import com.pingidentity.pingidsdkv2.PingOneSDKException
import kotlinx.coroutines.launch

class MobileAuthenticationFrameworkActivity : AppCompatActivity() {

    private var pingAuthenticationUI: PingAuthenticationUI? = null
    private var alertDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mob_auth_framework)
        findViewById<Button>(R.id.button_start).setOnClickListener {
            lifecycleScope.launch {
                try {
                    val mobilePayload = PingOne.generateMobilePayload(this@MobileAuthenticationFrameworkActivity)
                    pingAuthenticationUI = PingAuthenticationUI()
                    pingAuthenticationUI?.authenticate(
                        this@MobileAuthenticationFrameworkActivity,
                        mobilePayload ?: "",
                        null
                    )
                } catch (e: PingOneSDKException) {
                    Log.e("MobileAuthActivity", "generateMobilePayload failed: ${e.error.message}")
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PingAuthenticationUI.AUTHENTICATION_UI_ACTIVITY_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                val serverPayload = data?.getStringExtra("serverPayload")
                if (serverPayload != null) {
                    lifecycleScope.launch {
                        try {
                            val pairingObject = PingOne.processIdToken(serverPayload)
                            if (pairingObject != null) {
                                showApproveDenyDialog(pairingObject)
                            }
                        } catch (e: PingOneSDKException) {
                            Log.e("MobileAuthActivity", "processIdToken failed: ${e.error.message}")
                        }
                    }
                }
                val accessToken = data?.getStringExtra("access_token")
                if (accessToken != null) {
                    Log.i("Main Activity", "Retrieved access token")
                    Log.i(
                        "Access token",
                        String(
                            Base64.decode(
                                accessToken.substring(
                                    accessToken.indexOf('.'),
                                    accessToken.lastIndexOf('.')
                                ),
                                Base64.DEFAULT
                            )
                        )
                    )
                }
            }
        }
    }

    private fun showApproveDenyDialog(pairingObject: PairingObject) {
        alertDialog?.takeIf { it.isShowing }?.cancel()

        alertDialog = AlertDialog.Builder(this)
            .setTitle("Allow pairing?")
            .setMessage("Do you want to pair this device?")
            .setPositiveButton(R.string.approve_button_text) { _, _ ->
                lifecycleScope.launch {
                    try {
                        pairingObject.approve(this@MobileAuthenticationFrameworkActivity)
                        pingAuthenticationUI?.continueAuthentication(
                            this@MobileAuthenticationFrameworkActivity
                        )
                    } catch (e: PingOneSDKException) {
                        Log.e("Pairing failed", e.error.message ?: "unknown error")
                    }
                }
            }
            .setNegativeButton(R.string.deny_button_text) { _, _ ->
                Toast.makeText(this, "Pairing declined", Toast.LENGTH_LONG).show()
                alertDialog?.dismiss()
            }
            .create()
            .also { dialog ->
                dialog.show()
                // for automation tests
                dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                    .setContentDescription(getString(R.string.button_approve))
                dialog.getButton(DialogInterface.BUTTON_NEGATIVE)
                    .setContentDescription(getString(R.string.button_deny))
            }
    }
}



