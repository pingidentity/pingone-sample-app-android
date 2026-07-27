package com.pingidentity.pingone.camera

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.pingidentity.pingidsdkv2.AuthenticationObject
import com.pingidentity.pingidsdkv2.PingOne
import com.pingidentity.pingidsdkv2.PingOneSDKException
import com.pingidentity.pingidsdkv2.communication.models.PingOneDataModel
import com.pingidentity.pingone.R
import com.pingidentity.pingone.models.UserModel
import com.pingidentity.pingone.models.UsersAdapter
import kotlinx.coroutines.launch

class QrParserFragment : Fragment() {

    companion object {
        private const val TAG = "QrParserFragment"
    }

    private lateinit var progressBar: ProgressBar
    private lateinit var clientContextTextView: TextView
    private lateinit var listView: ListView
    private var authenticationObject: AuthenticationObject? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_qr_parsing, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        progressBar = view.findViewById(R.id.qr_authentication_progress_bar)
        clientContextTextView = view.findViewById(R.id.client_context_view)
        listView = view.findViewById(R.id.usersList)

        val qrCodeContent = arguments?.getString("qrCodeContent")
        if (qrCodeContent != null) {
            showVerifyingLayout()
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val obj = PingOne.authenticate(requireContext(), qrCodeContent)
                    if (obj != null) {
                        authenticationObject = obj
                        parseAuthenticationObject(obj)
                    }
                } catch (e: PingOneSDKException) {
                    Log.e(TAG, e.error.toString())
                    showDialog(e.error.toString())
                }
            }
        } else {
            showDialog("Error: insufficient parameters")
        }
    }

    private fun showVerifyingLayout() {
        requireActivity().runOnUiThread {
            progressBar.visibility = View.VISIBLE
        }
    }

    private fun hideVerifyingLayout() {
        requireActivity().runOnUiThread {
            progressBar.visibility = View.GONE
        }
    }

    private fun parseAuthenticationObject(authObj: AuthenticationObject) {
        hideVerifyingLayout()
        when (authObj.status?.uppercase()) {
            "COMPLETED", "DENIED", "EXPIRED" ->
                showDialog(getString(R.string.alert_dialog_format_message, authObj.status))
            "CLAIMED" ->
                handleClaimedStatusOfAuthentication(authObj)
            else -> {
                Log.e(TAG, "Error: unexpected status")
                showDialog("Error: unexpected status")
            }
        }
    }

    private fun approveManualAuthentication(userId: String?) {
        showVerifyingLayout()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val status = authenticationObject?.approve(requireContext(), userId)
                hideVerifyingLayout()
                if (status != null) {
                    showDialog(getString(R.string.alert_dialog_format_message, status))
                }
            } catch (e: PingOneSDKException) {
                hideVerifyingLayout()
                Log.e(TAG, e.error.toString())
                showDialog(e.error.toString())
            }
        }
    }

    private fun denyManualAuthentication(userId: String?) {
        showVerifyingLayout()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val status = authenticationObject?.deny(requireContext(), userId)
                hideVerifyingLayout()
                if (status != null) {
                    showDialog(getString(R.string.alert_dialog_format_message, status))
                }
            } catch (e: PingOneSDKException) {
                hideVerifyingLayout()
                Log.e(TAG, e.error.toString())
                showDialog(e.error.toString())
            }
        }
    }

    private fun handleClaimedStatusOfAuthentication(authObj: AuthenticationObject) {
        handleClientContext(authObj.clientContext)
        val users = authObj.users
        if (users != null && users.size() > 1) {
            handleSeveralUsersFlow(authObj)
        } else {
            handleSingleUserFlow(authObj)
        }
    }

    private fun handleSeveralUsersFlow(authObj: AuthenticationObject) {
        val users = ArrayList<UserModel>()
        authObj.users?.forEach { userElement ->
            val jsonUser = userElement.asJsonObject
            val userModel = UserModel().apply {
                // mandatory fields
                username = jsonUser.get("username")?.asString
                userId = jsonUser.get("id")?.asString
                /*
                 * non-mandatory fields — getAsString would throw on null,
                 * so we use toString() which returns "null" string if absent;
                 * consider checking has() + isJsonNull for production code.
                 */
                email = jsonUser.get("email")?.toString()
                given = jsonUser.get("name")?.asJsonObject?.get("given")?.toString()
                family = jsonUser.get("name")?.asJsonObject?.get("family")?.toString()
            }
            users.add(userModel)
        }
        val adapter = UsersAdapter(requireContext(), users)
        requireActivity().runOnUiThread {
            listView.adapter = adapter
            listView.setOnItemClickListener { _, _, position, _ ->
                if (authObj.needsApproval?.equals(
                        PingOneDataModel.AUTHENTICATION_USER_APPROVAL_VALUE.REQUIRED.name,
                        ignoreCase = true
                    ) == true
                ) {
                    showApproveDenyDialog(users[position].userId)
                } else {
                    approveManualAuthentication(users[position].userId)
                }
            }
        }
    }

    private fun handleSingleUserFlow(authObj: AuthenticationObject) {
        if (authObj.needsApproval?.equals(
                PingOneDataModel.AUTHENTICATION_USER_APPROVAL_VALUE.REQUIRED.name,
                ignoreCase = true
            ) == true
        ) {
            val userId = authObj.users?.get(0)?.asJsonObject?.get("id")?.asString
            showApproveDenyDialog(userId)
        } else {
            // should never get here
            Log.e(TAG, "Error: unexpected single user status")
            showDialog("Error: unexpected single user status")
        }
    }

    private fun showDialog(message: String) {
        hideVerifyingLayout()
        Handler(Looper.getMainLooper()).post {
            AlertDialog.Builder(requireContext())
                .setMessage(message)
                .setPositiveButton("OK") { _, _ -> requireActivity().finish() }
                .setOnCancelListener { requireActivity().finish() }
                .show()
        }
    }

    private fun showApproveDenyDialog(userId: String?) {
        hideVerifyingLayout()
        Handler(Looper.getMainLooper()).post {
            AlertDialog.Builder(requireContext())
                .setMessage("Do you want to approve authentication for a user $userId")
                .setPositiveButton("Approve") { _, _ -> approveManualAuthentication(userId) }
                .setNegativeButton("Deny") { _, _ -> denyManualAuthentication(userId) }
                .setOnCancelListener {
                    // close if single user canceled dialog
                    if ((authenticationObject?.users?.size() ?: 0) < 2) {
                        requireActivity().finish()
                    }
                }
                .create()
                .also { dialog ->
                    dialog.setCanceledOnTouchOutside(true)
                    dialog.show()
                }
        }
    }

    /*
     * handle client context value retrieved from the server in the AuthenticationObject
     */
    private fun handleClientContext(clientContext: String?) {
        if (clientContext.isNullOrEmpty()) {
            Log.d(TAG, "Client context is empty")
            return
        }
        clientContextTextView.text = clientContext
    }
}

