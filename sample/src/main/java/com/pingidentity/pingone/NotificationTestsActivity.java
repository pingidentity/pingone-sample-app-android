package com.pingidentity.pingone;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.pingidentity.pingone.ui.notification.NotificationTestFragment;

public class NotificationTestsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_test);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, NotificationTestFragment.newInstance())
                    .commitNow();
        }
    }
}
