package com.pingidentity.pingone

import android.content.Intent
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pingidentity.pingidsdkv2.NotificationObject
import com.pingidentity.pingidsdkv2.PingOne
import com.pingidentity.pingidsdkv2.PingOneSDKException
import com.pingidentity.pingidsdkv2.types.NotificationProvider
import com.pingidentity.pingone.notification.SampleNotificationsManager
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject

/*
 * This is where you will receive FCM messages and new tokens, if previous token
 * was discarded by Google's security
 */
class SampleMessagingService : FirebaseMessagingService() {

    /**
     * Use the application-wide scope rather than a service-owned one.
     *
     * [FirebaseMessagingService.onMessageReceived] is synchronous from Android's perspective —
     * the framework may destroy this service immediately after the method returns. A service-owned
     * scope would be cancelled in onDestroy() before [PingOne.processRemoteNotification] completes,
     * producing `JobCancellationException: Job was cancelled; job=SupervisorJobImpl{Cancelling}`.
     *
     * [SampleApplication.applicationScope] lives for the full process lifetime, so the coroutine
     * runs to completion regardless of when the service is destroyed.
     */
    private val applicationScope
        get() = (application as SampleApplication).applicationScope

    // onDestroy() override removed — there is no longer a service-owned scope to cancel.
    // applicationScope lives for the full process lifetime and must not be cancelled here.

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.i(TAG, "Firebase RemoteMessage received")
        applicationScope.launch {
            try {
                val pingOneNotificationObject = PingOne.processRemoteNotification(
                    this@SampleMessagingService,
                    remoteMessage
                )

                if (pingOneNotificationObject == null) {
                    // the push is not from PingOne For Customer - handle it your way
                    Log.i(TAG, "Received push message is not from PingOne for Customer")
                    /*
                     * implement the logic to handle push that was received from the other service
                     * than PingOne for Customer
                     */
                    // ...
                    return@launch
                }

                /*
                 * create intent to handle NotificationObject received from PingOne for Customer
                 */
                val handleNotificationObjectIntent =
                    createPingOneNotificationIntent(remoteMessage, pingOneNotificationObject)

                /*
                 * handle state where application was open when push was received
                 */
                if (ProcessLifecycleOwner.get().lifecycle.currentState == Lifecycle.State.RESUMED) {
                    when {
                        pingOneNotificationObject.isTest -> {
                            /*
                             * received TEST push from PingOne service, you can choose to do nothing
                             */
                            Log.i("SampleMessagingService", "Test push received")
                        }
                        pingOneNotificationObject.isCancelAuth -> {
                            Log.i("SampleMessagingService", "Cancel auth push received")
                            handleNotificationObjectIntent.putExtra("cancelAuth", true)
                            startActivity(handleNotificationObjectIntent)
                        }
                        pingOneNotificationObject.numberMatchingType != null -> {
                            Log.i("SampleMessagingService", "Number matching auth push received")
                            handleNotificationObjectIntent.putExtra(
                                "numberMatchingType",
                                pingOneNotificationObject.numberMatchingType
                            )
                            pingOneNotificationObject.numberMatchingOptions?.let { options ->
                                handleNotificationObjectIntent.putExtra("numberMatchingOptions", options)
                            }
                            startActivity(handleNotificationObjectIntent)
                        }
                        else -> {
                            /*
                             * received push message from PingOne for Customer while app is in foreground.
                             */
                            startActivity(handleNotificationObjectIntent)
                        }
                    }
                    /*
                     * handle state where application was closed/background when push was received
                     */
                } else {
                    /*
                     * create notifications manager
                     */
                    val sampleNotificationsManager = SampleNotificationsManager(this@SampleMessagingService)
                    /*
                     * handle PingOne for Customers TEST push message
                     */
                    if (pingOneNotificationObject.isTest
                        || pingOneNotificationObject.isCancelAuth
                        || pingOneNotificationObject.numberMatchingType != null
                    ) {
                        sampleNotificationsManager.buildAndSendPlainNotification(
                            handleNotificationObjectIntent,
                            pingOneNotificationObject.numberMatchingType != null
                        )
                        /*
                         * handle PingOne for Customers push message
                         */
                    } else {
                        /*
                         * Parse category from remoteMessage data to build a notification accordingly.
                         * May be null if no category set at the server side.
                         */
                        handleNotificationObjectIntent.putExtra(
                            "category",
                            remoteMessage.data["category"]
                        )
                        sampleNotificationsManager.buildAndSendNotificationAccordingToCategory(
                            handleNotificationObjectIntent
                        )
                    }
                }
            } catch (e: PingOneSDKException) {
                Log.e(TAG, "processRemoteNotification failed: ${e.error.message}")
            }
        }
    }

    /*
     * Called if InstanceID token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the InstanceID token
     * is initially generated so this is where you would retrieve the token.
     */
    override fun onNewToken(token: String) {
        // If you want to send messages to this application instance or
        // manage this app subscriptions on the server side, send the
        // Instance ID token to your app server.
        // Same reasoning — use application scope so token registration survives service teardown.
        applicationScope.launch {
            val errors = PingOne.setDeviceToken(this@SampleMessagingService, token, NotificationProvider.FCM)
            if (errors != null) {
                Log.w(TAG, "Setting FCM token failed")
                errors.forEach { error -> Log.e(TAG, "Error: $error") }
            }
        }
        saveFcmRegistrationToken(token)
    }

    private fun createPingOneNotificationIntent(
        remoteMessage: RemoteMessage,
        notificationObject: NotificationObject
    ): Intent {
        val handleNotificationObjectIntent = Intent(this, SampleActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            /*
             * provide a NotificationObject received from PingOne for Customer SDK as a Parcelable
             * to the intent
             */
            putExtra("PingOneNotification", notificationObject)
        }
        /*
         * Optional: parse title and message body from RemoteMessage as well
         */
        parseTitleAndBody(remoteMessage, handleNotificationObjectIntent)
        return handleNotificationObjectIntent
    }

    /*
     * Parse the "aps" part of the RemoteMessage to get notifications' title and body
     */
    private fun parseTitleAndBody(remoteMessage: RemoteMessage, intent: Intent) {
        if (remoteMessage.data.containsKey("aps")) {
            try {
                val jsonObject = JSONObject(remoteMessage.data["aps"] ?: return)
                val alert = jsonObject.get("alert") as? JSONObject ?: return
                intent.putExtra("title", alert.get("title").toString())
                intent.putExtra("body", alert.get("body").toString())
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }

    private fun saveFcmRegistrationToken(token: String) {
        getSharedPreferences("InternalPrefs", MODE_PRIVATE).edit {
            putString("pushToken", token)
        }
    }
}

private val TAG = SampleMessagingService::class.java.canonicalName

