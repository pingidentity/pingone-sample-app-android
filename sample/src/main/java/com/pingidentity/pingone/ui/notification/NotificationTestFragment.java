package com.pingidentity.pingone.ui.notification;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.pingidentity.pingidsdkv2.NotificationTest;
import com.pingidentity.pingidsdkv2.PingOne;
import com.pingidentity.pingidsdkv2.PingOneGeo;
import com.pingidentity.pingone.R;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

/*
 * Fragment that calls PingOne.testRemoteNotification() API and shows its results
 */
public class NotificationTestFragment extends Fragment {

    private NotificationTestAdapter notificationTestAdapter;

    private final ArrayList<NotificationTest> notificationTestArrayList = new ArrayList<>();

    // TextView that will display error or warning message if needed
    private TextView warningMessageTextView, errorMessageTextView;

    @NonNull
    public static NotificationTestFragment newInstance() {
        return new NotificationTestFragment();
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notification_test, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // a TextView that will display a warning message of the test with result "warning". Note:
        // the tests after the one with "warning" result will continue to work
        warningMessageTextView = view.findViewById(R.id.notification_test_fragment_warning_text_view);
        // a TextView that will display an error message of the failed test. Note: all the tests
        // that come after a failed one will not be triggered at all
        errorMessageTextView = view.findViewById(R.id.notification_test_fragment_error_text_view);

        ListView notificationTestsList = view.findViewById(R.id.listview_notification_tests);
        fillNotificationTestsArrayList();
        notificationTestAdapter = new NotificationTestAdapter(requireContext(), notificationTestArrayList);
        notificationTestsList.setAdapter(notificationTestAdapter);

        startTestNotificationProcess(requireContext());
    }

    private void startTestNotificationProcess(Context context){
        // run a sequence of tests for checking remoteNotification flow
        PingOne.testRemoteNotification(context, PingOneGeo.NORTH_AMERICA, (result, notificationTests, error) -> {
            // check the joint result of the sequence of the tests
            if (result.equals(NotificationTest.TestResult.PASS)){
                // all the tests are succeeded
                Log.i(NotificationTestFragment.class.getName(), "All the tests are succeeded");
                // you can return here or continue to fill the UI if needed
                // return; ...
            }
            // check for unrecoverable error (in this case the notificationTests array will be null)
            if (error!=null && notificationTests == null){
                //clear tests and show error message
                notificationTestArrayList.clear();
                // to update the UI we must explicitly call "runOnUiThread()" method
                requireActivity().runOnUiThread(() -> notificationTestAdapter.notifyDataSetChanged());
                showErrorDialog(error.getMessage());
                return;
            }
            if (notificationTests!=null){
                // Create a new Timer instance. We use timer to update UI in smooth way.
                Timer timer = new Timer();
                // Initialize the iterator
                int currentNotificationTestPosition = 0;

                for (NotificationTest notificationTest : notificationTests){
                    // Any variable defined in a method and accessed by an anonymous inner class must be final.
                    final int finalCurrentNotificationTestPosition = currentNotificationTestPosition;
                    TimerTask timerTask = new TimerTask() {
                        @Override
                        public void run() {
                            // avoid application crash where fragment gets result while detached from activity
                            if (NotificationTestFragment.this.isAdded()) {
                                notificationTestArrayList.set(finalCurrentNotificationTestPosition, notificationTest);
                                // to update the UI we must explicitly call "runOnUiThread()" method
                                requireActivity().runOnUiThread(() -> notificationTestAdapter.notifyDataSetChanged());
                                // check test result, on "WARNING" show warning message and continue
                                // on "FAIL" show error message and return
                                if (!notificationTest.getResult().equals(NotificationTest.TestResult.PASS)){
                                    if (notificationTest.getResult().equals(NotificationTest.TestResult.WARNING)){
                                        warningMessageTextView.setText(notificationTest.getResultsInfo());
                                        warningMessageTextView.setVisibility(View.VISIBLE);
                                    }else{
                                        // show error message only for the first test that failed
                                        if (!(errorMessageTextView.getVisibility()==View.VISIBLE)){
                                            requireActivity().runOnUiThread(() -> {
                                                errorMessageTextView.setText(notificationTest.getResultsInfo());
                                                errorMessageTextView.setVisibility(View.VISIBLE);
                                            });
                                        }
                                    }
                                }
                            }
                        }
                    };

                    timer.schedule(timerTask, currentNotificationTestPosition * 1000L);
                    currentNotificationTestPosition++;
                }

            }
        });
    }

    private void fillNotificationTestsArrayList(){
        for (NotificationTest.TestType notificationTestType : NotificationTest.TestType.values()){
            notificationTestArrayList.add(notificationTestType.ordinal(), new NotificationTest(notificationTestType));
        }
    }

    // simple error dialog that closes fragment
    private void showErrorDialog(String message){
        new AlertDialog.Builder(requireActivity())
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> requireActivity().finish())
                .setOnCancelListener(dialog -> requireActivity().finish())
                .show()
                .getButton(DialogInterface.BUTTON_POSITIVE).setContentDescription(this.getString(R.string.alert_dialog_button_ok));
    }
}
