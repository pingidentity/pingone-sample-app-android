package com.pingidentity.pingone;

import static com.pingidentity.pingone.notification.SampleNotificationsActionsReceiver.ACTION_APPROVE;
import static com.pingidentity.pingone.notification.SampleNotificationsManager.NOTIFICATION_ID_SAMPLE_APP;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.pingidentity.pingidsdkv2.NotificationObject;
import com.pingidentity.pingidsdkv2.types.DenyReason;

public class SampleActivity extends AppCompatActivity {
    AlertDialog alertDialog;

    private static final String NUMBER_MATCHING_SELECT_NUM = "SELECT_NUMBER";
    private static final String NUMBER_MATCHING_ENTER_MANUALLY = "ENTER_MANUALLY";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(getIntent().hasExtra("PingOneNotification")){
            handleNotificationObjectIntent(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if(intent.hasExtra("cancelAuth")) {
            if (alertDialog != null && alertDialog.isShowing()) {
                alertDialog.cancel();
            }
        } else if(intent.hasExtra("PingOneNotification")){
            handleNotificationObjectIntent(intent);
        }
    }

    private void handleNotificationObjectIntent(@NonNull Intent intent){
        if (intent.getExtras() == null) {
            Log.e("Sample activity", "Intent extras are null");
            return;
        }
        NotificationObject pingOneNotificationObject = (NotificationObject) intent.getExtras().get("PingOneNotification");
        if (pingOneNotificationObject!=null) {
            /*
             * in "auth_open" push category scenario we want to silent-approve the auth, this means
             * we trigger the approve() method of the NotificationObject without asking user approval
             * and dismiss the notification
             */
            if (intent.getAction()!=null && intent.getAction().equalsIgnoreCase(ACTION_APPROVE)){
                NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID_SAMPLE_APP);
                pingOneNotificationObject.approve(
                        this,
                        "auth_approve",
                        null,
                        (confirmationInfo, error) -> {
                            if (error!=null){
                                Log.e("Sample activity", "Silent approve action returned error " + error.getMessage());
                            }else{
                                Log.i("Sample activity", "Silent approve action completed with confirmation info: " + confirmationInfo);
                            }
                        });
                /*
                 * in "auth_open" push category scenario do not build the approval/deny user dialog
                 * as notification object already approved at this point
                 */
                return;
            }
            String title = "Authenticate?";
            String body = null;
            if (intent.hasExtra("title")) {
                title = intent.getStringExtra("title");
            }
            if (intent.hasExtra("body")) {
                body = intent.getStringExtra("body");
            }
            if (pingOneNotificationObject.getClientContext() != null) {
                JsonObject jsonObject = new Gson().fromJson(pingOneNotificationObject.getClientContext(), JsonObject.class);
                if (jsonObject.has("header_font_color")) {
                    changeTitleColor(jsonObject.get("header_font_color").getAsString());
                }
            }  else if(pingOneNotificationObject.getNumberMatchingType()!=null) {
                // Handle number matching push
                switch (pingOneNotificationObject.getNumberMatchingType()) {
                    case NUMBER_MATCHING_SELECT_NUM:
                        showNumMatchingDialog(pingOneNotificationObject);
                        break;
                    case NUMBER_MATCHING_ENTER_MANUALLY:
                        showNumMatchingTextInput(pingOneNotificationObject);
                        break;
                }
            } else if (pingOneNotificationObject.isTest()) {
                showOkDialog(body);
            } else {
                showApproveDenyDialog(pingOneNotificationObject, title, body);
            }
        }
    }

    private void showOkDialog(String message){
        new AlertDialog.Builder(SampleActivity.this)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok,
                        (dialog, which) -> finish())
                .show()
                .getButton(DialogInterface.BUTTON_POSITIVE).setContentDescription(this.getString(R.string.alert_dialog_button_ok));
    }

    private void showApproveDenyDialog(final NotificationObject pingOneNotificationObject, String title, String body){
        if(alertDialog!=null && alertDialog.isShowing()){
            alertDialog.cancel();
        }
        alertDialog = new AlertDialog.Builder(this)
                .setTitle(title==null?"Authenticate?":title)
                .setMessage(body==null?"":body)
                .setPositiveButton(R.string.approve_button_text, (dialog, which) -> pingOneNotificationObject.approve(
                        SampleActivity.this,
                        "user",
                        null,
                        (confirmationInfo, error) -> runOnUiThread(() -> {
                            if (error != null) {
                                showOkDialog(error.toString());
                            } else {
                                String confirmationMessage = confirmationInfo != null
                                        ? confirmationInfo.getAsString()
                                        : "Authentication approved";
                                showOkDialog(confirmationMessage);
                            }
                        })))
                .setNegativeButton(R.string.deny_button_text, (dialog, which) -> pingOneNotificationObject.deny(
                        SampleActivity.this,
                        DenyReason.NONE,
                        pingOneSDKError -> runOnUiThread(() -> {
                            if (pingOneSDKError != null) {
                                showOkDialog(pingOneSDKError.toString());
                            }else{
                                finish();
                            }
                        })
                ))
                .setOnCancelListener(
                        dialog -> finish())
                .create();
        alertDialog.setCanceledOnTouchOutside(true);
        alertDialog.show();

        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setContentDescription(getString(R.string.button_approve));
        alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setContentDescription(getString(R.string.button_deny));
    }

    private void changeTitleColor(String color){
        if (getSupportActionBar()!=null) {
            Spannable text = new SpannableString(getSupportActionBar().getTitle());
            text.setSpan(new ForegroundColorSpan(Color.parseColor(color)), 0, text.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
            getSupportActionBar().setTitle(text);
        }
    }

    private void showNumMatchingDialog(final NotificationObject pingOneNotificationObject) {
        // Dismiss the current alert dialog if it's already showing
        if (alertDialog != null && alertDialog.isShowing()) {
            alertDialog.cancel();
        }

        // Create a new AlertDialog builder
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Authenticate")
                .setMessage("Set a number for authentication");

        // Create a linear layout to hold the buttons
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        // Get the number matching options from the notification object
        int[] options = pingOneNotificationObject.getNumberMatchingOptions();
        for (int option : options) {
            // Create a button for each option
            Button optionButton = new Button(this);
            optionButton.setText(String.valueOf(option));
            optionButton.setOnClickListener(view -> {
                // Handle button click, approve with the selected number
                pingOneNotificationObject.approve(
                        SampleActivity.this,
                        "user",
                        option,
                        (confirmationInfo, error) -> runOnUiThread(() -> {
                            if (error != null) {
                                showOkDialog(error.toString());
                            } else {
                                String confirmationMessage = confirmationInfo != null
                                        ? confirmationInfo.getAsString()
                                        : "Authentication approved";
                                showOkDialog(confirmationMessage);
                            }
                        })
                );
            });
            // Add the button to the layout
            layout.addView(optionButton);
        }

        // Set the custom layout to the AlertDialog builder
        builder.setView(layout);

        // Create and show the alert dialog
        alertDialog = builder.create();
        alertDialog.show();
    }

    private void showNumMatchingTextInput(final NotificationObject pingOneNotificationObject) {
        // Dismiss the current alert dialog if it's already showing
        if (alertDialog != null && alertDialog.isShowing()) {
            alertDialog.cancel();
        }

        // Create a new AlertDialog builder
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Authenticate")
                .setMessage("Enter a number for authentication");

        // Create a linear layout to hold the input field and buttons
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        // Add a TextView for the input field label
        TextView inputLabel = new TextView(this);
        inputLabel.setText(R.string.text_input_enter_a_number);
        layout.addView(inputLabel);

        // Add an EditText for manual input
        EditText inputField = new EditText(this);
        inputField.setInputType(InputType.TYPE_CLASS_NUMBER);
        layout.addView(inputField);

        // Set the custom layout to the AlertDialog builder
        builder.setView(layout);

        // Add an approval button for the manually entered number
        builder.setPositiveButton("Approve", (dialog, which) -> {
            String enteredNumberStr = inputField.getText().toString();
            if (!enteredNumberStr.isEmpty()) {
                int enteredNumber;
                try {
                    enteredNumber = Integer.parseInt(enteredNumberStr);
                } catch (NumberFormatException e) {
                    Toast.makeText(SampleActivity.this, "Please enter a valid number", Toast.LENGTH_SHORT).show();
                    return;
                }
                pingOneNotificationObject.approve(
                        SampleActivity.this,
                        "user",
                        enteredNumber,
                        (confirmationInfo, error) -> runOnUiThread(() -> {
                            if (error != null) {
                                showOkDialog(error.toString());
                            } else {
                                String confirmationMessage = confirmationInfo != null
                                        ? confirmationInfo.getAsString()
                                        : "Authentication approved";
                                showOkDialog(confirmationMessage);
                            }
                        })
                );
            } else {
                // Show an error message if the input field is empty
                Toast.makeText(SampleActivity.this, "Please enter a number", Toast.LENGTH_SHORT).show();
            }
        });

        // Add a deny button
        builder.setNegativeButton("Deny", (dialog, which) -> pingOneNotificationObject.deny(
                SampleActivity.this,
                DenyReason.NONE,
                pingOneSDKError -> runOnUiThread(() -> {
                    if (pingOneSDKError != null) {
                        showOkDialog(pingOneSDKError.toString());
                    }
                })
        ));

        // Create and show the alert dialog
        alertDialog = builder.create();
        alertDialog.show();
    }
}
