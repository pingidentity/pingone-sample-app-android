package com.pingidentity.pingone.ui.notification;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;


import com.pingidentity.pingidsdkv2.NotificationTest;
import com.pingidentity.pingone.R;

import java.util.ArrayList;

public class NotificationTestAdapter extends ArrayAdapter<NotificationTest> {

    private final Context context;
    private final ArrayList<NotificationTest> notificationTests;
    /*
     * build an adapter for all possible test types
     */
    public NotificationTestAdapter(Context context, ArrayList<NotificationTest> notificationTests){
        super(context, R.layout.notification_test_row, notificationTests);
        this.context = context;
        this.notificationTests = notificationTests;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        final NotificationTest notificationTest = getItem(position);
        final ViewHolder viewHolder; // view lookup cache stored in tag
        if (convertView == null){
            viewHolder = new ViewHolder();
            convertView = LayoutInflater.from(context).inflate(R.layout.notification_test_row, parent, false);

            viewHolder.textView = convertView.findViewById(R.id.notification_test_message);
            viewHolder.progressBar = convertView.findViewById(R.id.notification_test_progress_bar);
            viewHolder.checkmark = convertView.findViewById(R.id.notification_test_checkmark);
            viewHolder.warning = convertView.findViewById(R.id.notification_test_warning);
            viewHolder.error = convertView.findViewById(R.id.notification_test_error);
            // Cache the viewHolder object inside the fresh view
            convertView.setTag(viewHolder);
        } else {
            // View is being recycled, retrieve the viewHolder object from tag
            viewHolder = (ViewHolder) convertView.getTag();
        }

        if (notificationTest!=null) {
            viewHolder.textView.setText(notificationTest.getName());
            if (notificationTest.getResult()==null){
                //do nothing
            }else if (notificationTest.getResult().equals(NotificationTest.TestResult.PASS)) {
                viewHolder.progressBar.setVisibility(View.GONE);
                viewHolder.checkmark.setVisibility(View.VISIBLE);
            }else if (notificationTest.getResult().equals(NotificationTest.TestResult.WARNING)){
                viewHolder.progressBar.setVisibility(View.GONE);
                viewHolder.warning.setVisibility(View.VISIBLE);
            }else if (notificationTest.getResult().equals(NotificationTest.TestResult.FAIL)){
                viewHolder.progressBar.setVisibility(View.GONE);
                viewHolder.error.setVisibility(View.VISIBLE);
            }
        }
        return convertView;
    }

    @Override
    public int getCount() {
        return notificationTests.size();
    }

    private static class ViewHolder {
        TextView textView;
        ProgressBar progressBar;
        ImageView checkmark;
        ImageView error;
        ImageView warning;
    }
}
