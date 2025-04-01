package com.pingidentity.pingone;

import android.app.Application;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.firebase.FirebaseApp;
import com.pingidentity.pingidsdkv2.PingOne;
import com.pingidentity.pingidsdkv2.PingOneGeo;
import com.pingidentity.pingidsdkv2.PingOneSDKError;

public class SampleApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        /*
         * Starting Since August 17 2020 all Firebase versions have been updated and now you have to
         * call Firebase.initializeApp() before using any Firebase product
         */
        FirebaseApp.initializeApp(this);

        /*
         * Initialize PingOne SDK component manually in an appropriate place of your application.
         * At first you need to disable lazy initialization by uncommenting the following lines in the
         * Manifest.xml:
         *
         *   <!--        <provider-->
         *   <!--            android:name="androidx.startup.InitializationProvider"-->
         *   <!--            android:authorities="${applicationId}.androidx-startup"-->
         *   <!--            android:exported="false"-->
         *   <!--            tools:node="merge">-->
         *   <!--            <meta-data android:name="com.pingidentity.pingidsdkv2.PingOneSDKInitializer"-->
         *   <!--                tools:node="remove" />-->
         *   <!--        </provider>-->
         *
         * Then call the following method to initialize PingOne SDK component in your application:
         *
         * AppInitializer.getInstance(this).initializeComponent(PingOneSDKInitializer.class);
         */

        /*
         * Configure PingOne SDK component by providing the PingOneRegion. If you use manual
         * initialization of PingOne SDK component, make sure to initialize PingOne SDK component
         * before calling configure() method.
         */
        PingOne.configure(this, PingOneGeo.NORTH_AMERICA, error -> {
            if (error != null) {
                // Handle error
                Log.e(SampleApplication.class.getSimpleName(), "Error: " + error.getMessage());
            }else{
                Log.d(SampleApplication.class.getSimpleName(), "PingOne SDK initialized successfully");
            }
        });

        // Set Google Cloud Project Number to allow the Google Play Integrity verification of the
        // device
        PingOne.setGooglePlayIntegrityProjectNumber(this, BuildConfig.GOOGLE_CLOUD_PROJECT_NUMBER);

    }
}
