package com.pingidentity.pingone.ui.notification

import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.pingidentity.pingidsdkv2.NotificationTest
import com.pingidentity.pingidsdkv2.PingOne
import com.pingidentity.pingidsdkv2.PingOneGeo
import com.pingidentity.pingidsdkv2.PingOneSDKException
import com.pingidentity.pingone.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Fragment that calls PingOne.testRemoteNotification() API and shows its results
 */
class NotificationTestFragment : Fragment() {

    companion object {
        @JvmStatic
        fun newInstance(): NotificationTestFragment = NotificationTestFragment()
    }

    private lateinit var notificationTestAdapter: NotificationTestAdapter
    private val notificationTestArrayList = ArrayList<NotificationTest>()

    // TextView that will display error or warning message if needed
    private lateinit var warningMessageTextView: TextView
    private lateinit var errorMessageTextView: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_notification_test, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // a TextView that will display a warning message of the test with result "warning". Note:
        // the tests after the one with "warning" result will continue to work
        warningMessageTextView = view.findViewById(R.id.notification_test_fragment_warning_text_view)
        // a TextView that will display an error message of the failed test. Note: all the tests
        // that come after a failed one will not be triggered at all
        errorMessageTextView = view.findViewById(R.id.notification_test_fragment_error_text_view)

        val notificationTestsList = view.findViewById<ListView>(R.id.listview_notification_tests)
        fillNotificationTestsArrayList()
        notificationTestAdapter = NotificationTestAdapter(requireContext(), notificationTestArrayList)
        notificationTestsList.adapter = notificationTestAdapter

        startTestNotificationProcess()
    }

    private fun startTestNotificationProcess() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val (result, notificationTests) = PingOne.testRemoteNotification(
                    requireContext(),
                    PingOneGeo.NORTH_AMERICA
                )
                // check the joint result of the sequence of the tests
                if (result == NotificationTest.TestResult.PASS) {
                    // all the tests are succeeded
                    Log.i(NotificationTestFragment::class.java.name, "All the tests are succeeded")
                    // you can return here or continue to fill the UI if needed
                    // return@launch ...
                }

                notificationTests?.forEachIndexed { index, notificationTest ->
                    // Use delay to update UI in a smooth way (replaces Timer)
                    delay(index * 1000L)
                    // avoid application crash where fragment gets result while detached from activity
                    if (isAdded) {
                        notificationTestArrayList[index] = notificationTest ?: return@forEachIndexed
                        requireActivity().runOnUiThread {
                            notificationTestAdapter.notifyDataSetChanged()
                        }
                        // check test result, on "WARNING" show warning message and continue
                        // on "FAIL" show error message and return
                        val testResult = notificationTest.result ?: return@forEachIndexed
                        if (testResult != NotificationTest.TestResult.PASS) {
                            if (testResult == NotificationTest.TestResult.WARNING) {
                                requireActivity().runOnUiThread {
                                    warningMessageTextView.text = notificationTest.resultsInfo
                                    warningMessageTextView.visibility = View.VISIBLE
                                }
                            } else {
                                // show error message only for the first test that failed
                                if (isAdded && errorMessageTextView.visibility != View.VISIBLE) {
                                    requireActivity().runOnUiThread {
                                        errorMessageTextView.text = notificationTest.resultsInfo
                                        errorMessageTextView.visibility = View.VISIBLE
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: PingOneSDKException) {
                // unrecoverable error
                notificationTestArrayList.clear()
                if (isAdded) {
                    requireActivity().runOnUiThread {
                        notificationTestAdapter.notifyDataSetChanged()
                    }
                    showErrorDialog(e.error.message ?: "Unknown error")
                }
            }
        }
    }

    private fun fillNotificationTestsArrayList() {
        NotificationTest.TestType.entries.forEachIndexed { index, testType ->
            notificationTestArrayList.add(index, NotificationTest(testType))
        }
    }

    // simple error dialog that closes fragment
    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(requireActivity())
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ -> requireActivity().finish() }
            .setOnCancelListener { requireActivity().finish() }
            .show()
            .getButton(DialogInterface.BUTTON_POSITIVE)
            .setContentDescription(getString(R.string.alert_dialog_button_ok))
    }
}


