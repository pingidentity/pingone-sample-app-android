package com.pingidentity.pingone

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.pingidentity.pingidsdkv2.PingOne
import com.pingidentity.pingidsdkv2.PingOneSDKException
import kotlinx.coroutines.launch
import java.io.File

class PairActivity : SampleActivity() {

    companion object {
        private val TAG = PairActivity::class.java.canonicalName
    }

    private lateinit var activationCodeInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pair)
        activationCodeInput = findViewById(R.id.activation_code_input)
        val buttonPair = findViewById<Button>(R.id.button_pair)
        buttonPair.setOnClickListener {
            buttonPair.isEnabled = false
            val activationCode = activationCodeInput.text.toString()
            lifecycleScope.launch {
                try {
                    val pairingInfo = PingOne.pair(this@PairActivity, activationCode)
                    /*
                     * you can parse a pairingInfo if needed:
                     *
                     * pairingInfo?.let {
                     *     // do what you need
                     * }
                     */
                    Log.i(TAG, "Pairing completed successfully")
                    getSharedPreferences("InternalPrefs", MODE_PRIVATE).edit()
                        .putBoolean("paired", true)
                        .apply()
                    showOkDialog("Device is paired successfully")
                } catch (e: PingOneSDKException) {
                    Log.e(TAG, e.error.toString())
                    showOkDialog(e.error.toString())
                } finally {
                    buttonPair.isEnabled = true
                }
            }
        }
    }

    private fun getPairingStatus(): Boolean =
        getSharedPreferences("InternalPrefs", MODE_PRIVATE).getBoolean("paired", false)

    private fun showOkDialog(message: String) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton("Copy") { _, _ ->
                val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                manager.setPrimaryClip(ClipData.newPlainText("Message Content", message))
                Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
            }
            .show()
            .getButton(android.content.DialogInterface.BUTTON_POSITIVE)
            .setContentDescription(getString(R.string.alert_dialog_button_ok))
    }

    /*
     * To share log file use this method. The FileProvider for your application_id and
     * PingOne log file is provided by the SDK component.
     */
    private fun shareLogFile(context: Context) {
        /*
         * This is the full path to the PingOne log file
         */
        val file = File(
            context.filesDir.absolutePath
                .plus(File.separator)
                .plus("pingone.log")
        )
        /*
         * Create a URI using FileProvider to make sure it will work on every Android version
         */
        val uri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID, file)
        /*
         * Bundle a Uri into Intent and activate it
         */
        val sharingIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            /*
             * Set flag to give temporary permission to external app to use your FileProvider
             */
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        /*
         * Activate an intent (title is mandatory and configurable)
         */
        startActivity(Intent.createChooser(sharingIntent, "Choose an application"))
    }
}

