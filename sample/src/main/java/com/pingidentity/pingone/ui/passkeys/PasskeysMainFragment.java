package com.pingidentity.pingone.ui.passkeys;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.pingidentity.pingone.R;
import com.pingidentity.pingone.passkeys.PKDeviceFlowManager;

public class PasskeysMainFragment extends Fragment {

    public static PasskeysMainFragment newInstance() {
        return new PasskeysMainFragment();
    }
    private View buttonSignIn;

    private PKDeviceFlowManager passkeysFlowManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_passkeys_main, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        passkeysFlowManager = new PKDeviceFlowManager(requireContext(),this);
        buttonSignIn = view.findViewById(R.id.button_sign_in_passkeys);

        buttonSignIn.setOnClickListener(
                view1 -> passkeysFlowManager.startAuthorizationFlow()
        );
        hideLoading();
    }

    /*
     * Sets the view to a "loading" state
     */
    public void showLoading(){
        new Handler(Looper.getMainLooper()).post(() -> {
            ProgressBar circleProgressBar = getView().findViewById(R.id.progressBar);
            circleProgressBar.setVisibility(View.VISIBLE);
            buttonSignIn.setEnabled(false);
        });
    }

    /*
     * The view exits "loading" state and ready for interaction
     */
    public void hideLoading(){
        new Handler(Looper.getMainLooper()).post(() -> {
            ProgressBar circleProgressBar = getView().findViewById(R.id.progressBar);
            circleProgressBar.setVisibility(View.GONE);
            buttonSignIn.setEnabled(true);
        });
    }

    /*
     * displays a username-password login dialogue
     */
    public void promptSignUp(){
        new Handler(Looper.getMainLooper()).post(this::showSignUpDialog);
    }

    /*
     * shows a disappearing short message
     */
    public void showToastMessage(CharSequence message){
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
        });
    }

    /*
     * Simple username - password input dialogue
     * TODO enhance UI
     */
    public void showSignUpDialog(){
        LayoutInflater factory = LayoutInflater.from(requireContext());
        final View textEntryView = factory.inflate(R.layout.dialog_login, null);
        final EditText usernameInput = textEntryView.findViewById(R.id.userNameEditText);
        final EditText passwordInput = textEntryView.findViewById(R.id.passwordEditText);
        final TextView usernameTitle = textEntryView.findViewById(R.id.userNameTextView);
        final TextView passwordTile = textEntryView.findViewById(R.id.passwordTextView);

        float dpi = getContext().getResources().getDisplayMetrics().density;
        textEntryView.setPadding((int)(20*dpi), (int)(8*dpi), (int)(20*dpi), (int)(5*dpi));
        usernameTitle.setPadding(7,0,0,0);
        passwordTile.setPadding(7,0,0,0);

        AlertDialog.Builder alert = new AlertDialog.Builder(requireContext());
        alert.setTitle("No Passkey found.\nLogin to register a Passkey.");
        alert.setView(textEntryView);
        alert.setPositiveButton("Submit", (dialog, whichButton) -> {
            passkeysFlowManager.signUp(
                    usernameInput.getText().toString(),
                    passwordInput.getText().toString()
            );
        });
        alert.setNegativeButton("Cancel", (dialog, whichButton) -> {
            hideLoading();
            // Canceled.
        });
        alert.create().show();
    }
    public void showSimpleAlertDialogue(String title, String message){
        new Handler(Looper.getMainLooper()).post(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
            builder.setTitle(title);
            builder.setMessage(message);
            builder.setPositiveButton("OK", (dialog, which) -> {
                // User clicked OK button
                dialog.dismiss();
            });
            builder.create().show();
        });
    }
}


