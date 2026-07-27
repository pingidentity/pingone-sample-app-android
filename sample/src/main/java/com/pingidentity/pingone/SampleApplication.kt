package com.pingidentity.pingone

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.pingidentity.pingidsdkv2.PingOne
import com.pingidentity.pingidsdkv2.PingOneGeo
import com.pingidentity.pingidsdkv2.PingOneSDKException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SampleApplication : Application() {

    /**
     * Application-wide coroutine scope used by messaging services and other components whose
     * work must outlive individual Android component lifecycles.
     *
     * Exposed as an internal val so that [SampleMessagingService] and [SampleHmsMessagingService]
     * can launch push-handling coroutines here instead of on a service-owned scope.
     * See [SampleMessagingService] for a detailed explanation of why this matters.
     *
     * SupervisorJob ensures a failure in one child coroutine does not cancel unrelated work.
     * This scope is intentionally never canceled; it lives for the full process lifetime.
     */
    internal val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        /*
         * Starting Since August 17, 2020 all Firebase versions have been updated, and now you have to
         * call Firebase.initializeApp() before using any Firebase product
         */
        FirebaseApp.initializeApp(this)

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
        applicationScope.launch {
            try {
                PingOne.configure(this@SampleApplication, PingOneGeo.NORTH_AMERICA)
                Log.d(TAG, "PingOne SDK initialized successfully")
            } catch (e: PingOneSDKException) {
                Log.e(TAG, "Error: ${e.error.message}")
            }
        }

        // Set Google Cloud Project Number to allow the Google Play Integrity verification of the
        // device
        PingOne.setGooglePlayIntegrityProjectNumber(this, BuildConfig.GOOGLE_CLOUD_PROJECT_NUMBER)
    }

}

private val TAG = SampleApplication::class.java.simpleName

