package com.pingidentity.pingone

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.pingidentity.pingidsdkv2.NotificationObject
import com.pingidentity.pingidsdkv2.PingOneSDKException
import com.pingidentity.pingidsdkv2.types.DenyReason
import com.pingidentity.pingone.notification.SampleNotificationsActionsReceiver
import com.pingidentity.pingone.notification.SampleNotificationsManager
import kotlinx.coroutines.launch

open class SampleActivity : AppCompatActivity() {

    var alertDialog: AlertDialog? = null

    companion object {
        private const val NUMBER_MATCHING_SELECT_NUM = "SELECT_NUMBER"
        private const val NUMBER_MATCHING_ENTER_MANUALLY = "ENTER_MANUALLY"
        private const val TAG = "SampleActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.hasExtra("PingOneNotification")) {
            handleNotificationObjectIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        when {
            intent.hasExtra("cancelAuth") -> {
                alertDialog?.takeIf { it.isShowing }?.cancel()
            }
            intent.hasExtra("PingOneNotification") -> {
                handleNotificationObjectIntent(intent)
            }
        }
    }

    private fun handleNotificationObjectIntent(intent: Intent) {
        val pingOneNotificationObject = intent.extras?.getParcelable<NotificationObject>("PingOneNotification")
            ?: return

        /*
         * in "auth_open" push category scenario we want to silent-approve the auth, this means
         * we trigger the approve() method of the NotificationObject without asking user approval
         * and dismiss the notification
         */
        if (intent.action?.equals(SampleNotificationsActionsReceiver.ACTION_APPROVE, ignoreCase = true) == true) {
            NotificationManagerCompat.from(this).cancel(SampleNotificationsManager.NOTIFICATION_ID_SAMPLE_APP)
            lifecycleScope.launch {
                try {
                    val confirmationInfo =  pingOneNotificationObject.approve(this@SampleActivity, "auth_approve", null)
                    Log.i(TAG, "Silent approve action completed with confirmation info: $confirmationInfo")
                } catch (e: PingOneSDKException) {
                    Log.e(TAG, "Silent approve action returned error ${e.error.message}")
                }
            }
            /*
             * in "auth_open" push category scenario do not build the approval/deny user dialog
             * as notification object already approved at this point
             */
            return
        }

        var title = "Authenticate?"
        var body: String? = null
        if (intent.hasExtra("title")) {
            title = intent.getStringExtra("title") ?: title
        }
        if (intent.hasExtra("body")) {
            body = intent.getStringExtra("body")
        }

        val clientContext = pingOneNotificationObject.clientContext
        if (clientContext != null) {
            val jsonObject = Gson().fromJson(clientContext, com.google.gson.JsonObject::class.java)
            if (jsonObject?.has("header_font_color") == true) {
                changeTitleColor(jsonObject.get("header_font_color").asString)
            }
        } else if (pingOneNotificationObject.numberMatchingType != null) {
            // Handle number matching push
            when (pingOneNotificationObject.numberMatchingType) {
                NUMBER_MATCHING_SELECT_NUM -> showNumMatchingDialog(pingOneNotificationObject)
                NUMBER_MATCHING_ENTER_MANUALLY -> showNumMatchingTextInput(pingOneNotificationObject)
            }
        } else if (pingOneNotificationObject.isTest) {
            showOkDialog(body)
        } else {
            showApproveDenyDialog(pingOneNotificationObject, title, body)
        }
    }

    private fun showOkDialog(message: String?) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
            .show()
            .getButton(DialogInterface.BUTTON_POSITIVE)
            .setContentDescription(getString(R.string.alert_dialog_button_ok))
    }

    private fun showApproveDenyDialog(
        pingOneNotificationObject: NotificationObject,
        title: String?,
        body: String?
    ) {
        alertDialog?.takeIf { it.isShowing }?.cancel()

        alertDialog = AlertDialog.Builder(this)
            .setTitle(title ?: "Authenticate?")
            .setMessage(body ?: "")
            .setPositiveButton(R.string.approve_button_text) { _, _ ->
                lifecycleScope.launch {
                    try {
                        val confirmationInfo =  pingOneNotificationObject.approve(this@SampleActivity, "user", null)
                        val confirmationMessage = if (confirmationInfo != null)
                            confirmationInfo.asString
                        else
                            "Authentication approved"
                        showOkDialog(confirmationMessage)
                    } catch (e: PingOneSDKException) {
                        showOkDialog(e.error.toString())
                    }
                }
            }
            .setNegativeButton(R.string.deny_button_text) { _, _ ->
                lifecycleScope.launch {
                    try {
                        pingOneNotificationObject.deny(this@SampleActivity, DenyReason.NONE)
                        finish()
                    } catch (e: PingOneSDKException) {
                        showOkDialog(e.error.toString())
                    }
                }
            }
            .setOnCancelListener { finish() }
            .create()
            .also { dialog ->
                dialog.setCanceledOnTouchOutside(true)
                dialog.show()
                dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                    .setContentDescription(getString(R.string.button_approve))
                dialog.getButton(DialogInterface.BUTTON_NEGATIVE)
                    .setContentDescription(getString(R.string.button_deny))
            }
    }

    private fun changeTitleColor(color: String) {
        supportActionBar?.let { actionBar ->
            val text = SpannableString(actionBar.title)
            text.setSpan(
                ForegroundColorSpan(color.toColorInt()),
                0, text.length,
                Spannable.SPAN_INCLUSIVE_INCLUSIVE
            )
            actionBar.title = text
        }
    }

    private fun showNumMatchingDialog(pingOneNotificationObject: NotificationObject) {
        alertDialog?.takeIf { it.isShowing }?.cancel()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val options = pingOneNotificationObject.numberMatchingOptions
        options?.forEach { option ->
            Button(this).apply {
                text = option.toString()
                setOnClickListener {
                    lifecycleScope.launch {
                        try {
                            pingOneNotificationObject.approve(this@SampleActivity, "user", option)
                            finish()
                        } catch (e: PingOneSDKException) {
                            showOkDialog(e.error.toString())
                        }
                    }
                }
                layout.addView(this)
            }
        }

        alertDialog = AlertDialog.Builder(this)
            .setTitle("Authenticate")
            .setMessage("Set a number for authentication")
            .setView(layout)
            .create()
            .also { it.show() }
    }

    private fun showNumMatchingTextInput(pingOneNotificationObject: NotificationObject) {
        alertDialog?.takeIf { it.isShowing }?.cancel()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        TextView(this).apply {
            text = "Enter a number:"
            layout.addView(this)
        }

        val inputField = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            layout.addView(this)
        }

        alertDialog = AlertDialog.Builder(this)
            .setTitle("Authenticate")
            .setMessage("Enter a number for authentication")
            .setView(layout)
            .setPositiveButton("Approve") { _, _ ->
                val enteredNumberStr = inputField.text.toString()
                if (enteredNumberStr.isNotEmpty()) {
                    val enteredNumber = enteredNumberStr.toInt()
                    lifecycleScope.launch {
                        try {
                            val confirmationInfo = pingOneNotificationObject.approve(this@SampleActivity, "user", enteredNumber)
                            val confirmationMessage = if (confirmationInfo != null)
                                confirmationInfo.asString
                            else
                                "Authentication approved"
                            showOkDialog(confirmationMessage)
                        } catch (e: PingOneSDKException) {
                            showOkDialog(e.error.toString())
                        }
                    }
                } else {
                    Toast.makeText(this, "Please enter a number", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Deny") { _, _ ->
                lifecycleScope.launch {
                    try {
                        pingOneNotificationObject.deny(this@SampleActivity, DenyReason.NONE)
                    } catch (e: PingOneSDKException) {
                        showOkDialog(e.error.toString())
                    }
                }
            }
            .create()
            .also { it.show() }
    }
}



