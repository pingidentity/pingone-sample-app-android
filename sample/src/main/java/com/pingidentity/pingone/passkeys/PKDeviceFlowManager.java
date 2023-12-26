package com.pingidentity.pingone.passkeys;


import android.content.Context;
import android.os.CancellationSignal;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.credentials.CreateCredentialResponse;
import androidx.credentials.CreatePublicKeyCredentialRequest;
import androidx.credentials.CreatePublicKeyCredentialResponse;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.GetPublicKeyCredentialOption;
import androidx.credentials.exceptions.CreateCredentialException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;
import androidx.credentials.PublicKeyCredential;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pingidentity.pingone.BuildConfig;
import com.pingidentity.pingone.connection.ConnectionManager;
import com.pingidentity.pingone.ui.passkeys.PasskeysMainFragment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class PKDeviceFlowManager {

    private final Context context;
    private final PasskeysMainFragment view;
    private final CredentialManager credentialManager;
    private final PKDataManager dataManager;

    public PKDeviceFlowManager(Context context, PasskeysMainFragment view){
        this.context = context;
        this.view = view;
        this.credentialManager = CredentialManager.create(context);
        this.dataManager = new PKDataManager();
    }

    private String configureSignInUri(){
        String url = BuildConfig.PASSKEYS_ENDPOINT_PREFIX +
                BuildConfig.PASSKEYS_ENVIRONMENT_ID +
                "/as/authorize?client_id=" +
                BuildConfig.PASSKEYS_CLIENT_ID +
                "&scope=openid&response_type=code&response_mode=pi.flow";
        Log.d("Passkeys flow manager", "SignIn url:" + url);
        return url;
    }

    private String configureCompleteSignInUri(String assertion){
        String url = BuildConfig.PASSKEYS_ENDPOINT_PREFIX +
                BuildConfig.PASSKEYS_ENVIRONMENT_ID +
                "/as/authorize?client_id=" +
                BuildConfig.PASSKEYS_CLIENT_ID +
                "&scope=openid&response_type=code&response_mode=pi.flow&deviceAuthenticationId=" +
                dataManager.getDeviceAuthenticationId() +
                "&rpId=" +
                dataManager.getOrigin() +
                "&assertion=" +
                assertion;
        Log.d("Passkeys flow manager", "CompleteSignIn url:" + url);
        return url;
    }

    private String configureSignUpUri(String username, String password){
        String url = BuildConfig.PASSKEYS_ENDPOINT_PREFIX +
                BuildConfig.PASSKEYS_ENVIRONMENT_ID +
                "/as/authorize?client_id=" +
                BuildConfig.PASSKEYS_CLIENT_ID +
                "&scope=openid&response_type=code&response_mode=pi.flow&username=" +
                username +
                "&password=" +
                password;
        Log.d("Passkeys flow manager", "SignUp url:" + url);
        return url;
    }

    private String configureCompleteSignUpUri(String attestation){
        String url = BuildConfig.PASSKEYS_ENDPOINT_PREFIX +
                BuildConfig.PASSKEYS_ENVIRONMENT_ID +
                "/as/authorize?client_id=" +
                BuildConfig.PASSKEYS_CLIENT_ID +
                "&scope=openid&response_type=code&response_mode=pi.flow"+
                "&deviceId=" +
                dataManager.getDeviceId() +
                "&userId=" +
                dataManager.getUserId() +
                "&attestation=" +
                attestation +
                "&origin=" +
                dataManager.getOrigin();
        Log.d("Passkeys flow manager", "CompleteSignUp url:" + url);
        return url;
    }

    /*
     * the start point of every passkeys-related flow
     */
    public void startPasskeysSignIn(){
        view.showLoading();
        //send signIn get request
        ConnectionManager.getInstance().executeGetRequest(configureSignInUri(), new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                view.hideLoading();
                view.showToastMessage(e.getMessage());
                e.printStackTrace();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                Log.i("Passkeys flow manager", "Received response for SignIn");
                if (response.body() != null){
                    String responseBody = response.body().string();
                    try {
                        JSONObject responseJson = new JSONObject(responseBody);
                        JSONObject additionalProperties = responseJson.getJSONObject("additionalProperties");
                        dataManager.setDeviceAuthenticationId(additionalProperties.get("deviceAuthenticationId").toString());
                        JsonObject publicKeyCredentialRequestOptions = JsonParser.parseString(
                                        additionalProperties.get("publicKeyCredentialRequestOptions").toString()).
                                        getAsJsonObject();

                        publicKeyCredentialRequestOptions.addProperty("challenge", PKDataManager.convertByteArrayToString(publicKeyCredentialRequestOptions.getAsJsonArray("challenge")));
                        publicKeyCredentialRequestOptions.addProperty("username", dataManager.getUsername());
                        GetPublicKeyCredentialOption getPublicKeyCredentialOption = new GetPublicKeyCredentialOption(publicKeyCredentialRequestOptions.toString());
                        signInPasskey(getPublicKeyCredentialOption);

                    } catch (JSONException e) {
                        view.showToastMessage(e.getMessage());
                        view.hideLoading();
                        throw new RuntimeException(e);
                    }
                }
            }
        });
    }

    /*
     * Tries to signUp with user provided username - password pair
     */
    public void signUp(String username, String password){
        view.showLoading();
        Log.i("Passkeys flow manager", "Executes signUp request");
        ConnectionManager.getInstance().executeGetRequest(
                configureSignUpUri(
                        username,
                        password
                )
                , new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        Log.e("Passkeys flow manager", "Error in communication ", e);
                        view.hideLoading();
                        view.showToastMessage(e.getMessage());
                        e.printStackTrace();
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        Log.i("Passkeys flow manager", "Received signUp response");
                        try {
                            // Get attestation from google credential manager
                            parseSignUpResponse(response);
                        } catch (JSONException e) {
                            view.hideLoading();
                            view.showToastMessage(e.getMessage());
                            throw new RuntimeException(e);
                        }
                    }
                });
    }

    /*
     * Parses login response
     */
    private void parseSignUpResponse(Response response) throws IOException, JSONException {
        Log.i("Passkeys flow manager", "Parses signUp response");
        if(response.body()!=null){
            JSONObject responseBodyAsJson = new JSONObject(response.body().string());
            if (responseBodyAsJson.has("additionalProperties")){
                // Get user data
                JSONObject additionalProperties = responseBodyAsJson.getJSONObject("additionalProperties");
                JsonObject publicKeyCredentialCreationOptions =
                        JsonParser.parseString(additionalProperties.get("publicKeyCredentialCreationOptions").toString()).getAsJsonObject();
                dataManager.setDeviceId(additionalProperties.get("deviceId").toString());
                dataManager.setUserId(additionalProperties.get("userId").toString());

                // Get username
                JSONObject parameters = responseBodyAsJson.getJSONObject("parameters");
                JSONObject authorizationRequest = parameters.getJSONObject("authorizationRequest");
                dataManager.setUsername(authorizationRequest.get("username").toString());

                String createCredentialRequest = dataManager.preparePublicKeyCredentialCreationOptions(publicKeyCredentialCreationOptions);
                Log.d("createCredentialRequest:", createCredentialRequest);

                signUpPasskey(createCredentialRequest);
            }else{
                Log.e("Passkey flow manager", "Error response:" + response);
                view.showToastMessage("Error, see details in console");
                view.hideLoading();
            }
        }else{
            Log.e("Passkey flow manager", "Error response:" + response);
            view.showToastMessage("Error, the response has no body");
            view.hideLoading();
        }
    }

    private void handlePasskeysRegister(CreateCredentialResponse createCredentialResponse) throws IOException, JSONException {
        Log.d("Passkeys flow manager", "Start complete register with CreateCredentialResponse");
        try {
            CreatePublicKeyCredentialResponse response = (CreatePublicKeyCredentialResponse) createCredentialResponse;
            String responseString = response.getRegistrationResponseJson();
            dataManager.setOrigin(PKDataManager.getOriginFromAssertion(responseString));
            String regResponseInBase64 = URLEncoder.encode(responseString, StandardCharsets.UTF_8.toString());

            ConnectionManager.getInstance().executeGetRequest(configureCompleteSignUpUri(regResponseInBase64), new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    view.hideLoading();
                    view.showToastMessage(e.getMessage());
                    e.printStackTrace();
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.body() != null && response.code() == 200) {
                        String responseBody = response.body().string();
                        try {
                            JSONObject responseJson = new JSONObject(responseBody);
                            if (responseJson.has("httpBody")) {
                                JSONObject httpBody = responseJson.getJSONObject("httpBody");
                                JSONObject error = httpBody.getJSONObject("error");
                                JSONArray detailsArr = error.getJSONArray("details");
                                JSONObject detailsJson = detailsArr.getJSONObject(0);
                                JSONObject rawResponse = detailsJson.getJSONObject("rawResponse");
                                String message = rawResponse.get("message").toString();
                                Log.d("Passkeys flow manager", "Received signUp response: " + message);
                                view.showToastMessage("Register to passkey error: " + message);
                            }
                            else if (!dataManager.getUsername().isEmpty()) {
                                String welcomeMsg = String.format("%s is registered!", dataManager.getUsername());
                                view.showToastMessage(welcomeMsg);
                            }
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                    } else {
                        view.hideLoading();
                        Log.e("Passkey flow manager", "Error response:" + response);
                        view.showToastMessage("Register to passkey failed");
                    }
                }
            });
        } catch (final Exception e) {
            view.hideLoading();
            view.showToastMessage(e.getMessage());
            e.printStackTrace();
        }
    }

    private void parseSignInResponse(PublicKeyCredential credential) throws IOException, JSONException {
        Log.i("Passkeys flow manager", "parseSignInResponse");
        try {
            String authResponseString = credential.getAuthenticationResponseJson();
            dataManager.setOrigin(PKDataManager.getOriginFromAssertion(authResponseString));
            String authResponseStringBase64 = URLEncoder.encode(authResponseString, StandardCharsets.UTF_8.toString());

            ConnectionManager.getInstance().executeGetRequest(configureCompleteSignInUri(authResponseStringBase64), new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    view.hideLoading();
                    view.showToastMessage(e.getMessage());
                    e.printStackTrace();
                }
                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    Log.i("Passkeys flow manager", "Complete SignIn");
                    view.hideLoading();
                    if (response.body() != null) {
                        String responseBody = response.body().string();
                        try {
                            JSONObject responseJson = new JSONObject(responseBody);
                            if (responseJson.has("httpBody")) {
                                JSONObject httpBody = responseJson.getJSONObject("httpBody");
                                JSONObject error = httpBody.getJSONObject("error");
                                JSONArray detailsArr = error.getJSONArray("details");
                                JSONObject detailsJson = detailsArr.getJSONObject(0);
                                JSONObject rawResponse = detailsJson.getJSONObject("rawResponse");
                                Log.d("Passkeys flow manager", "rawResponse: " + rawResponse.toString());
                                String code = rawResponse.get("code").toString();
                                Log.d("Passkeys flow manager", "code: " + code);
                                if (response.code() == 200 && code.equals("NOT_FOUND")) {
                                    // Need to register
                                    view.promptSignUp();
                                }
                            } else if (responseJson.has("additionalProperties")) {
                                JSONObject additionalProperties = responseJson.getJSONObject("additionalProperties");
                                dataManager.setUsername(additionalProperties.get("username").toString());
                                String welcomeMsg = String.format("Hello %s!", dataManager.getUsername());
                                view.showToastMessage(welcomeMsg);
                            }
                        } catch (JSONException e) {
                            view.showToastMessage(e.getMessage());
                            throw new RuntimeException(e);
                        }
                    }
                }
            });
        } catch( final Exception e){
            view.hideLoading();
            view.showToastMessage(e.getMessage());
            e.printStackTrace();
        }
    }

    private void signInPasskey(GetPublicKeyCredentialOption getPublicKeyCredentialOption){
        GetCredentialRequest getCredentialRequest = new GetCredentialRequest.Builder()
                .addCredentialOption(getPublicKeyCredentialOption)
                .setPreferImmediatelyAvailableCredentials(true)
                .build();

        // Get assertion from google credential manager
        credentialManager.getCredentialAsync(
                context,
                getCredentialRequest,
                new CancellationSignal(),
                Runnable::run,
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse getCredentialResponse) {
                        Log.i("Passkeys flow manager", "Retrieved credential with type " + getCredentialResponse.getCredential().getType());
                        PublicKeyCredential credential = ((PublicKeyCredential) getCredentialResponse.getCredential());//.getAuthenticationResponseJson();
                        try {
                            parseSignInResponse(credential);
                        } catch (IOException | JSONException e) {
                            view.hideLoading();
                            Log.d("Passkeys flow manager", "No credential available, starting singUp flow. Error:" + e.getMessage());
                            view.promptSignUp();
                        }
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        view.showToastMessage(e.getMessage());
                        view.hideLoading();
                        e.printStackTrace();
                    }
                }
        );
    }
    private void signUpPasskey(String passkeyCreationRequest){
        CreatePublicKeyCredentialRequest createPublicKeyCredentialRequest = new CreatePublicKeyCredentialRequest(passkeyCreationRequest,null,true);
        credentialManager.createCredentialAsync(
                context,
                createPublicKeyCredentialRequest,
                new CancellationSignal(),
                Runnable::run,
                new CredentialManagerCallback<CreateCredentialResponse, CreateCredentialException>() {
                    @Override
                    public void onResult(CreateCredentialResponse createCredentialResponse) throws RuntimeException {
                        view.hideLoading();
                        Log.i("Passkeys flow manager", "Created credential with type " + createCredentialResponse.getType());
                        try {
                            handlePasskeysRegister(createCredentialResponse);
                        } catch (IOException | JSONException e) {
                            view.showToastMessage(e.getMessage());
                            e.printStackTrace();
                            throw new RuntimeException(e);
                        }
                    }
                    @Override
                    public void onError(@NonNull CreateCredentialException e) {
                        view.hideLoading();
                        view.showToastMessage(e.getMessage());
                        e.printStackTrace();
                    }
                }
        );
    }
}
