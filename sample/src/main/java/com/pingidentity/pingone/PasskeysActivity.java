package com.pingidentity.pingone;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.pingidentity.pingone.ui.passkeys.PasskeysMainFragment;

public class PasskeysActivity extends SampleActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_passkeys);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, PasskeysMainFragment.newInstance())
                    .commitNow();
        }
    }
}
