package com.pingidentity.pingone

import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pingidentity.pingidsdkv2.PairingObject
import com.pingidentity.pingidsdkv2.PingOne
import com.pingidentity.pingidsdkv2.PingOneSDKException
import kotlinx.coroutines.launch
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import androidx.core.content.edit

/*
 * This is sample OIDC Activity which shows how to use the AppAuth open code library
 * to implement login via OpenID Connect protocol.
 */
class OIDCActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "OIDC_Activity"
    }

    private lateinit var authorizationService: AuthorizationService
    private var alertDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_oidc)

        authorizationService = AuthorizationService(this, AppAuthConfiguration.Builder().build())

        findViewById<Button>(R.id.button_pair_oidc).setOnClickListener {
            discoverAndAuthorize()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume triggered")

        val resp = AuthorizationResponse.fromIntent(intent)
        if (resp != null) {
            Log.i(TAG, "Authorization response retrieved")
            authorizationService.performTokenRequest(resp.createTokenExchangeRequest()) { response, _ ->
                val idToken = response?.idToken ?: return@performTokenRequest
                Log.i(TAG, "OpenIDConnect Authorization token retrieved")
                createAndShowTokenDialogue(idToken)
                lifecycleScope.launch {
                    try {
                        val pairingObject = PingOne.processIdToken(idToken)
                        if (pairingObject != null) {
                            // should show approve/deny dialogue
                            showApproveDenyDialog(pairingObject)
                        } else {
                            showOkDialog("Auth completed. No further action needed.")
                        }
                        Log.i(TAG, "token: $idToken")
                    } catch (e: PingOneSDKException) {
                        Log.i(TAG, e.error.toString())
                        showOkDialog(e.error.toString())
                    }
                }
            }
        }
    }

    private fun createAndShowTokenDialogue(token: String) {
        AlertDialog.Builder(this)
            .setTitle("OIDC Success")
            .setMessage("Authentication completed successfully")
            .setPositiveButton("COPY TOKEN AND CLOSE") { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("OIDC Token", token))
                Toast.makeText(this, "OIDC token is copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun discoverAndAuthorize() {
        val issuer = Uri.parse(BuildConfig.OIDC_ISSUER)
        if (issuer == null || issuer.scheme.isNullOrEmpty() || !issuer.scheme!!.startsWith("https")) {
            showOkDialog("Error: OIDC Issuer must start with https scheme")
            return
        }
        AuthorizationServiceConfiguration.fetchFromIssuer(issuer) { serviceConfiguration, ex ->
            if (ex != null) {
                Log.e(TAG, "failed to fetch configuration")
                ex.printStackTrace()
                return@fetchFromIssuer
            }
            if (serviceConfiguration == null) {
                Log.e(TAG, "failed to fetch configuration")
                return@fetchFromIssuer
            }
            /*
             * We need to retrieve from the PingOne SDK an Object called MobilePayload
             * and add it to the AuthorizationRequest. Since v.2.0.0 the generateMobilePayload(Context)
             * is deprecated and will return null on untrusted device. A new method is provided called
             * generateMobilePayload(Context, PingOne.PingOneSDKMobilePayloadCallback) which returns
             * mobile payload asynchronously.
             */
            lifecycleScope.launch {
                try {
                    val mobilePayload = PingOne.generateMobilePayload(this@OIDCActivity)
                    val payload = mapOf("mobilePayload" to (mobilePayload ?: ""))

                    val authRequest = AuthorizationRequest.Builder(
                        serviceConfiguration,
                        BuildConfig.CLIENT_ID,
                        ResponseTypeValues.CODE,
                        Uri.parse(BuildConfig.OIDC_REDIRECT_URI)
                    )
                        .setScope(BuildConfig.SCOPE)
                        .setAdditionalParameters(payload)
                        .build()

                    // to handle possible case where result returned in secondary thread
                    Handler(Looper.getMainLooper()).post {
                        authorizationService.performAuthorizationRequest(
                            authRequest,
                            createPostAuthorizationIntent()
                        )
                        authorizationService.dispose()
                        finish()
                    }
                } catch (e: PingOneSDKException) {
                    Log.e(TAG, e.error.toString())
                    showOkDialog(e.error.toString())
                }
            }
        }
    }

    private fun createPostAuthorizationIntent(): PendingIntent {
        val intent = Intent(this, this::class.java)
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    /*
     * Overriding the onBackPressed to dispose authorization service correctly
     * to avoid unnecessary error log messages
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        authorizationService.dispose()
        super.onBackPressed()
    }

    private fun showApproveDenyDialog(pairingObject: PairingObject) {
        alertDialog?.takeIf { it.isShowing }?.cancel()

        alertDialog = AlertDialog.Builder(this)
            .setTitle("Allow pairing?")
            .setMessage("Do you want to pair this device?")
            .setPositiveButton(R.string.approve_button_text) { _, _ ->
                lifecycleScope.launch {
                    try {
                        val pairingInfo = pairingObject.approve(this@OIDCActivity)
                        /*
                         * you can parse a pairingInfo if needed:
                         *
                         * pairingInfo?.let {
                         *     // do what you need
                         * }
                         */
                        Log.i(TAG, "PingOneSDKPairingCallback onComplete method triggered")
                        getSharedPreferences("InternalPrefs", MODE_PRIVATE).edit {
                            putBoolean("paired", true)
                        }
                        showOkDialog("Device is paired successfully")
                    } catch (e: PingOneSDKException) {
                        showOkDialog(e.error.toString())
                    }
                }
            }
            .setNegativeButton(R.string.deny_button_text) { _, _ ->
                Toast.makeText(this, "Pairing declined", Toast.LENGTH_LONG).show()
                alertDialog?.dismiss()
            }
            .create()
            .also { dialog ->
                dialog.show()
                dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                    .setContentDescription(getString(R.string.button_approve))
                dialog.getButton(DialogInterface.BUTTON_NEGATIVE)
                    .setContentDescription(getString(R.string.button_deny))
            }
    }

    private fun showOkDialog(message: String) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton("Copy") { _, _ ->
                val manager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                manager.setPrimaryClip(ClipData.newPlainText("Message Content", message))
                Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
            }
            .show()
            .getButton(DialogInterface.BUTTON_POSITIVE)
            .setContentDescription(getString(R.string.alert_dialog_button_ok))
    }
}

