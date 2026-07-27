package com.pingidentity.pingone

import android.Manifest
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.ToggleButton
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.messaging.FirebaseMessaging
/* EnableHuaweiMobileServices
import com.huawei.hms.api.HuaweiApiAvailability
import com.huawei.hms.push.HmsMessaging
EnableHuaweiMobileServices */
import com.pingidentity.pingidsdkv2.PingOne
import com.pingidentity.pingidsdkv2.PingOneSDKException
import kotlinx.coroutines.launch
import androidx.core.content.edit

class MainActivity : SampleActivity() {

    companion object {
        private const val ALLOW_PUSH_KEY = "allow_push"
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        /* EnableHuaweiMobileServices
        if (isHmsAvailable(this))
            HmsMessaging.getInstance(this).setAutoInitEnabled(true)
        else
         EnableHuaweiMobileServices */
        logFCMRegistrationToken()
        /*
         * since Android 13 (API level 33) notifications must be explicitly approved by the
         * user at the runtime.
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationsPermission()
        }

        // show the version in a text field
        findViewById<TextView>(R.id.text_view_app_version).text =
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

        findViewById<android.widget.Button>(R.id.button_pairing_key).setOnClickListener {
            startActivity(Intent(this, PairActivity::class.java))
        }
        findViewById<android.widget.Button>(R.id.button_pairing_oidc).setOnClickListener {
            startActivity(Intent(this, OIDCActivity::class.java))
        }
        findViewById<android.widget.Button>(R.id.button_authentication_api).setOnClickListener {
            startActivity(Intent(this, MobileAuthenticationFrameworkActivity::class.java))
        }

        findViewById<android.widget.Button>(R.id.button_send_logs).setOnClickListener {
            lifecycleScope.launch {
                try {
                    val supportId = PingOne.sendLogs(this@MainActivity)
                    if (supportId != null) {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Logs sent")
                            .setMessage("Support id : $supportId")
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                            .getButton(DialogInterface.BUTTON_POSITIVE)
                            .setContentDescription(getString(R.string.alert_dialog_button_ok))
                    }
                } catch (e: PingOneSDKException) {
                    Log.e(TAG, e.error.message ?: "sendLogs error")
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Error")
                        .setMessage(e.error.toString())
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }

        val toggleAllowPush = findViewById<ToggleButton>(R.id.toggle_allow_push)
        val allowPush = getSharedPreferences().getBoolean(ALLOW_PUSH_KEY, true)
        toggleAllowPush.isChecked = allowPush
        toggleAllowPush.setOnCheckedChangeListener { buttonView, isChecked ->
            getSharedPreferences().edit { putBoolean(ALLOW_PUSH_KEY, isChecked) }
            PingOne.allowPushNotifications(buttonView.context, isChecked)
        }

        findViewById<android.view.View>(R.id.button_one_time_passcode).setOnClickListener {
            startActivity(Intent(this, TOTPActivity::class.java))
        }
        findViewById<android.view.View>(R.id.button_scan_qr).setOnClickListener {
            startActivity(Intent(this, ManualAuthActivity::class.java))
        }
        findViewById<android.view.View>(R.id.button_passkeys).setOnClickListener {
            startActivity(Intent(this, PasskeysActivity::class.java))
        }
        findViewById<android.view.View>(R.id.button_test_notification).setOnClickListener {
            startActivity(Intent(this, NotificationTestsActivity::class.java))
        }
    }

    fun getSharedPreferences(): SharedPreferences =
        getSharedPreferences("InternalPrefs", Context.MODE_PRIVATE)

    private fun logFCMRegistrationToken() {
        if (GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(this) != ConnectionResult.SUCCESS
        ) {
            Log.d(TAG, "GooglePlayServices is not available on this device.")
            return
        }
        val prefs = getSharedPreferences("InternalPrefs", MODE_PRIVATE)
        val token = prefs.getString("pushToken", null)
        if (token == null) {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    getSharedPreferences("InternalPrefs", MODE_PRIVATE).edit {
                        putString("pushToken", task.result)
                    }
                    Log.d(TAG, "FCM Token = ${task.result}")
                } else {
                    Log.e("FCM Token Retrieval failed", task.exception?.message ?: "unknown error")
                }
            }
        } else {
            Log.d(TAG, "FCM Token = $token")
        }
    }

    /*
     * Don't call the following method if your application targets API lower than 33 (Android Tiramisu)
     */
    @RequiresApi(api = 33)
    private fun requestNotificationsPermission() {
        /*
         * request the notifications permission if needed
         * NOTE: if user explicitly denied the permission - the system will not present the
         * dialog again, the user will be able to allow the application to show notifications only
         * from the application permissions settings
         */
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            registerActivityResultLauncher().launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun registerActivityResultLauncher(): ActivityResultLauncher<String> {
        /*
         * Register the permissions callback, which handles the user's response to the
         * system permissions prompt dialog. For more information see documentation at
         * https://developer.android.com/reference/androidx/activity/result/ActivityResultLauncher
         */
        return registerForActivityResult(ActivityResultContracts.RequestPermission()) { isPermissionGranted ->
            if (isPermissionGranted) {
                // do nothing
                Log.i(TAG, "Notifications permission granted")
            } else {
                if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    /*
                     * the user disallowed the permission (for the first time only):
                     * you can choose to do nothing or present the user the explanation why are you
                     * requesting the permission and what will be the consequences of denying it
                     */
                    Log.i(TAG, "Notifications permission rejected with rationale")
                } else {
                    /*
                     * the user has denied the notifications permission
                     * the application will not be able to show the notifications to the user
                     */
                    Log.i(TAG, "Notifications permission rejected")
                }
            }
        }
    }

    /* EnableHuaweiMobileServices
    private fun isHmsAvailable(context: Context): Boolean {
        var isAvailable = false
        val result = HuaweiApiAvailability.getInstance().isHuaweiMobileServicesAvailable(context)
        isAvailable = (com.huawei.hms.api.ConnectionResult.SUCCESS == result)
        Log.i(TAG, "isHmsAvailable: $isAvailable")
        return isAvailable
    }
    EnableHuaweiMobileServices */
}

