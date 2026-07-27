package com.pingidentity.pingone.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.pingidentity.pingidsdkv2.NotificationObject
import com.pingidentity.pingidsdkv2.PingOneSDKException
import com.pingidentity.pingidsdkv2.types.DenyReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class SampleNotificationsActionsReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_APPROVE = "sample_action_approve"
        const val ACTION_DENY = "sample_action_deny"
        private const val TAG = "SampleActionsReceiver"
        private const val TIMEOUT_MS = 9_000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val bundle = intent.getBundleExtra("extra")
        if (bundle == null) {
            // This should never happen as the notification action buttons are only created when the bundle is properly set, but we check it just in case.
            Log.e(TAG, "No bundle found in intent")
            return
        }

        val notificationObject = bundle.getParcelable<NotificationObject>("NotificationObject")
        if (notificationObject == null) {
            // This should never happen as the notification action buttons are only created when the NotificationObject is properly set in the bundle, but we check it just in case.
            Log.e(TAG, "No NotificationObject found in bundle")
            return
        }

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                withTimeout(TIMEOUT_MS) {
                    when {
                        intent.action?.equals(ACTION_APPROVE, ignoreCase = true) == true -> {
                            try {
                                notificationObject.approve(context, "user", null)
                                Log.i(TAG, "Approve action completed successfully")
                            } catch (e: PingOneSDKException) {
                                Log.e(TAG, "Approve action failed: ${e.error.message}")
                            }
                        }
                        intent.action?.equals(ACTION_DENY, ignoreCase = true) == true -> {
                            try {
                                notificationObject.deny(context, DenyReason.NONE)
                                Log.i(TAG, "Deny action completed successfully")
                            } catch (e: PingOneSDKException) {
                                Log.e(TAG, "Deny action failed: ${e.error.message}")
                            }
                        }
                    }
                }
            } finally {
                NotificationManagerCompat.from(context).cancel(SampleNotificationsManager.NOTIFICATION_ID_SAMPLE_APP)
                pendingResult.finish()
            }
        }
    }
}



