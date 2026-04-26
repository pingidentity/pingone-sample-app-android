package com.pingidentity.pingone.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;

import com.pingidentity.pingidsdkv2.NotificationObject;
import com.pingidentity.pingidsdkv2.types.DenyReason;

import static com.pingidentity.pingone.notification.SampleNotificationsManager.NOTIFICATION_ID_SAMPLE_APP;

public class SampleNotificationsActionsReceiver extends BroadcastReceiver {

    public static final String ACTION_APPROVE = "sample_action_approve";
    public static final String ACTION_DENY = "sample_action_deny";

    @Override
    public void onReceive(Context context, @NonNull Intent intent) {
        Bundle bundle = intent.getBundleExtra("extra");
        if (bundle == null) {
            // This should never happen as the notification action buttons are only created when the bundle is properly set, but we check it just in case.
            Log.e("ActionReceiver", "No bundle found in intent");
            return;
        }
        NotificationObject notificationObject = bundle.getParcelable("NotificationObject");
        if (notificationObject == null) {
            // This should never happen as the notification action buttons are only created when the NotificationObject is properly set in the bundle, but we check it just in case.
            Log.e("ActionReceiver", "No NotificationObject found in bundle");
            return;
        }
        if (intent.getAction()!=null && intent.getAction().equalsIgnoreCase(ACTION_APPROVE)){

            notificationObject.approve(context, "user" , null, (confirmationInfo, error) -> {
                if (error != null) {
                    Log.e("ActionReceiver", "Approve action failed with error: " + error.getMessage());
                    return;
                }
                    Log.i("ActionReceiver", "Approve action completed successfully with confirmation info: " + confirmationInfo);
            });
        }
        if (intent.getAction()!=null && intent.getAction().equalsIgnoreCase(ACTION_DENY)){

            notificationObject.deny(context, DenyReason.NONE, error -> {
                if (error != null) {
                    Log.e("ActionReceiver", "Deny action failed with error: " + error.getMessage());
                    return;
                }
                Log.i("ActionReceiver", "Deny action completed successfully");
            });
        }
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.cancel(NOTIFICATION_ID_SAMPLE_APP);
    }
}
