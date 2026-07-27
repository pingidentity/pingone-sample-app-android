package com.pingidentity.pingone

import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.huawei.hms.push.HmsMessageService
import com.huawei.hms.push.RemoteMessage
import com.pingidentity.pingidsdkv2.NotificationObject
import com.pingidentity.pingidsdkv2.PingOne
import com.pingidentity.pingidsdkv2.PingOneSDKException
import com.pingidentity.pingidsdkv2.types.NotificationProvider
import com.pingidentity.pingone.notification.SampleNotificationsManager
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject

class SampleHmsMessagingService : HmsMessageService() {

    /**
     * Use the application-wide scope rather than a service-owned one.
     *
     * [HmsMessageService.onMessageReceived] is synchronous from Android's perspective — the
     * framework may destroy this service immediately after the method returns. A service-owned
     * scope would be cancelled in onDestroy() before [PingOne.processRemoteNotification] completes,
     * producing `JobCancellationException: Job was cancelled; job=SupervisorJobImpl{Cancelling}`.
     *
     * [SampleApplication.applicationScope] lives for the full process lifetime, so the coroutine
     * runs to completion regardless of when the service is destroyed.
     */
    private val applicationScope
        get() = (application as SampleApplication).applicationScope

    override fun onMessageReceived(remoteMessage: RemoteMessage?) {
        Log.i(TAG, "HMS RemoteMessage received")
        // Check whether the message is empty.
        if (remoteMessage == null) {
            Log.e(TAG, "Received message entity is null!")
            return
        }

        // Launch on the application scope so the work survives service destruction.
        applicationScope.launch {
            try {
                val pingOneNotificationObject = PingOne.processRemoteNotification(
                    this@SampleHmsMessagingService,
                    remoteMessage.data
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
                            Log.i(TAG, "Test push received")
                        }
                        pingOneNotificationObject.isCancelAuth -> {
                            Log.i(TAG, "Cancel auth push received")
                            handleNotificationObjectIntent.putExtra("cancelAuth", true)
                            startActivity(handleNotificationObjectIntent)
                        }
                        pingOneNotificationObject.numberMatchingType != null -> {
                            Log.i(TAG, "Number matching auth push received")
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
                    val sampleNotificationsManager = SampleNotificationsManager(this@SampleHmsMessagingService)
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
                            parseCategoryFromRemoteMessage(remoteMessage)
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

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        saveHmsRegistrationToken(token)

        // Same reasoning — use application scope so token registration survives service teardown.
        applicationScope.launch {
            val errors = PingOne.setDeviceToken(this@SampleHmsMessagingService, token, NotificationProvider.HMS)
            if (errors != null) {
                Log.w(TAG, "Setting HMS token failed")
                errors.forEach { error -> Log.e(TAG, "Error: $error") }
                // re-schedule
            }
        }
    }

    private fun createPingOneNotificationIntent(
        remoteMessage: RemoteMessage,
        notificationObject: NotificationObject
    ): Intent {
        return Intent(this, SampleActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            /*
             * provide a NotificationObject received from PingOne for Customer SDK as a Parcelable
             * to the intent
             */
            putExtra("PingOneNotification", notificationObject)
            /*
             * Optional: parse title and message body from RemoteMessage as well
             */
            parseTitleAndBody(remoteMessage, this)
        }
    }

    /*
     * Parse the HMS RemoteMessage to get notification title and body
     */
    private fun parseTitleAndBody(remoteMessage: RemoteMessage, intent: Intent) {
        try {
            val jsonObject = JSONObject(remoteMessage.data)
            if (remoteMessage.data.contains(TITLE)) {
                intent.putExtra(TITLE, jsonObject.getString(TITLE))
            }
            if (remoteMessage.data.contains(BODY)) {
                intent.putExtra(BODY, jsonObject.getString(BODY))
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private fun parseCategoryFromRemoteMessage(remoteMessage: RemoteMessage): String? {
        return try {
            val jsonObject = JSONObject(remoteMessage.data)
            if (jsonObject.has("category")) jsonObject.getString("category") else null
        } catch (e: JSONException) {
            e.printStackTrace()
            null
        }
    }

    private fun saveHmsRegistrationToken(token: String) {
        getSharedPreferences("InternalPrefs", MODE_PRIVATE).edit()
            .putString("pushToken", token)
            .apply()
    }

    // onDestroy() override not needed — there is no service-owned scope to cancel.
    // applicationScope lives for the full process lifetime and must not be cancelled here.

}

private const val TAG = "SampleHmsMessagingService"
private const val TITLE = "title"
private const val BODY = "body"
