package com.pingidentity.pingone.passkeys;


import android.annotation.SuppressLint;
import android.content.Context;
import android.os.CancellationSignal;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.credentials.CreateCredentialResponse;
import androidx.credentials.CreatePasswordRequest;
import androidx.credentials.CreatePublicKeyCredentialRequest;
import androidx.credentials.CreatePublicKeyCredentialResponse;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.GetPublicKeyCredentialOption;
import androidx.credentials.PublicKeyCredential;
import androidx.credentials.exceptions.CreateCredentialException;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

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
import java.util.Base64;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/*
 * Passkeys Device Flow Manager
 * Responsible for passkeys related flows
 */
public class PKDeviceFlowManager {

    private final Context context;
    private final PasskeysMainFragment view;
    /*
     * Credential Manager unifies the sign-in interface across authentication methods, making it
     * clearer and easier for users to sign into apps
     */
    private final CredentialManager credentialManager;
    private final PKDataManager dataManager;
    private final PasskeysUrlHelper passkeysUrlHelper;

    public PKDeviceFlowManager(Context context, PasskeysMainFragment view){
        this.context = context;
        this.view = view;
        this.credentialManager = CredentialManager.create(context);
        this.passkeysUrlHelper = new PasskeysUrlHelper();
        this.dataManager = new PKDataManager();
    }


    /*
     * 1. the entry point of authorization flow
     *
     */
    public void startAuthorizationFlow(){
        view.showLoading();
        /*
         * Create an authorization request
         * (This tells PingOne “I want to authenticate with passkeys”.)
         */
        String request = passkeysUrlHelper.getBaseUrl();
        //send GET request to the base authorization endpoint
        ConnectionManager.getInstance().executeGetRequest(request, new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                view.hideLoading();
                view.showToastMessage(e.getMessage());
                Log.e("Passkeys flow manager", "Error in communication ", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                Log.i("Passkeys flow manager", "Received response for authorization");
                /*
                 * Parse successful response of the authorization request, example response:
                 *   {
                 *     "flowId" : "ad8a945eb529713321e39ee36a78598e",
                 *     "parameters" : {
                 *       "authorizationRequest" : {
                 *         "client_id" : "2af0ae6c-4d9c-4210-a722-bff6bb4c9fdf",
                 *         "response_type" : "code",
                 *         "response_mode" : "pi.flow",
                 *         "scope" : "openid",
                 *         "acr_values" : ""
                 *       },
                 *       "application" : {
                 *         "id" : "2af0ae6c-4d9c-4210-a722-bff6bb4c9fdf",
                 *         "type" : "WEB_APP",
                 *         "name" : "Passkeys DaVinci App",
                 *         "protocol" : "OPENID_CONNECT"
                 *       },
                 *       "relayState" : {
                 *         "organizationId" : "1fe9e229-17ef-47ff-b488-7ab2a70715c5",
                 *         "flowResponseUrl" : "https://auth.pingone.com/846d642c-f42c-4f82-ad08-3268e4e159db/as/resume",
                 *         "nonce" : "f37cb008-be64-4558-ab7a-eb8029475d00",
                 *         "state" : "00e2fe2e-91b0-4eed-ace8-095d4a5a7545",
                 *         "contextForMapping" : null
                 *       }
                 *     },
                 *     "additionalProperties" : {
                 *       "publicKeyCredentialRequestOptions" : "{\\"challenge\\":[46,-53,34,42,-58,-1,118,46,62,-7,-92,-43,60,-110,-105,-1,98,-36,79,-44,79,-105,9,88,63,106,119,18,110,-107,103,45],\\"timeout\\":120000,\\"rpId\\":\\"pingone.mobile.ping-eng.com\\",\\"allowCredentials\\\":[],\\"userVerification\\":\\"required\\"}",
                 *       "deviceAuthenticationId" : "00d90efa-7bdf-4e80-abf0-332a64579341"
                 *     },
                 *     "additionalPropertiesName" : "additionalProperties",
                 *     "success" : true,
                 *     "id" : "18m07r894d",
                 *     "interactionId" : "00534429-bb36-47fe-81ad-508248e1d3f9",
                 *     "companyId" : "846d642c-f42c-4f82-ad08-3268e4e159db",
                 *     "connectionId" : "867ed4363b2bc21c860085ad2baa817d",
                 *     "connectorId" : "httpConnector",
                 *     "capabilityName" : "createSuccessResponse",
                 *     "skProxyApiEnvironmentId" : "deprecated",
                 *     "interactionToken" : "deprecated",
                 *     "_links" : {
                 *       "self" : {
                 *         "href" : "https://auth.pingone.com/846d642c-f42c-4f82-ad08-3268e4e159db/davinci/policy/fc24438ea81be8f981a72b1190b8310b/start"
                 *       }
                 *     }
                 *   }
                 */
                if (response.body() != null){
                    String responseBody = response.body().string();
                    Log.d("Passkeys flow manager", "start authorization response body: " + responseBody);
                    fetchCredentialAccordingToServerResponse(responseBody);
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
        String signUpRequest = passkeysUrlHelper.configureSignUpUrl(username, password);
        ConnectionManager.getInstance().executeGetRequest(signUpRequest,
                new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        view.hideLoading();
                        view.showToastMessage(e.getMessage());
                        Log.e("Passkeys flow manager", "Error in communication ", e);
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        Log.i("Passkeys flow manager", "Received signUp response");
                        try {
                            // Get attestation from google credential manager
                            parseSignUpResponse(response, password);
                        } catch (JSONException e) {
                            view.hideLoading();
                            view.showToastMessage(e.getMessage());
                            Log.e("Passkeys flow manager", "Error parsing response", e);
                        }
                    }
                });
    }

    /*
     * Parses login response
     */
    private void parseSignUpResponse(@NonNull Response response, String password) throws IOException, JSONException {
        Log.i("Passkeys flow manager", "Parses signUp response");
        if(response.body()!=null){
            String responseBody = response.body().string();
            Log.d("Passkeys flow manager", responseBody);
            JSONObject responseBodyAsJson = new JSONObject(responseBody);
            if (responseBodyAsJson.has("additionalProperties")){
                // Get user data
                JSONObject additionalProperties = responseBodyAsJson.getJSONObject("additionalProperties");
                Log.d("Passkeys flow manager", additionalProperties.toString());
                if (additionalProperties.has("publicKeyCredentialCreationOptions")){
                    createCredentialAccordingToServerResponse(responseBodyAsJson, password);
                }else if (additionalProperties.has("publicKeyCredentialRequestOptions")){
                    String publicKeyCredentialRequestOptionsStr = additionalProperties.getString("publicKeyCredentialRequestOptions");
                    // Parse the JSON string
                    JSONObject options = new JSONObject(publicKeyCredentialRequestOptionsStr);
                    // Check allowCredentials array
                    JSONArray allowCredentials = options.getJSONArray("allowCredentials");
                    if (allowCredentials.length() == 0) {
                        createCredentialAccordingToServerResponse(responseBodyAsJson, password);
                    }else{
                        fetchCredentialAccordingToServerResponse(responseBody);
                    }
                }
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

    private void createCredentialAccordingToServerResponse(@NonNull JSONObject responseBodyAsJson, String password) throws JSONException {
        JSONObject additionalProperties = responseBodyAsJson.getJSONObject("additionalProperties");

        JsonObject publicKeyCredentialCreationOptions;
        if (additionalProperties.has("publicKeyCredentialCreationOptions")) {
            publicKeyCredentialCreationOptions = JsonParser.parseString(additionalProperties.get("publicKeyCredentialCreationOptions").toString()).getAsJsonObject();
            Log.d("Challenge", publicKeyCredentialCreationOptions.toString());
            dataManager.setDeviceId(additionalProperties.get("deviceId").toString());
            dataManager.setUserId(additionalProperties.get("userId").toString());

            // Get username
            JSONObject parameters = responseBodyAsJson.getJSONObject("parameters");
            JSONObject authorizationRequest = parameters.getJSONObject("authorizationRequest");
            dataManager.setUsername(authorizationRequest.get("username").toString());
            String createCredentialRequest = dataManager.preparePublicKeyCredentialCreationOptions(publicKeyCredentialCreationOptions);
            if(BuildConfig.IS_AUTO_ENROLLMENT_ENABLED) {
                savePassword(password, dataManager.getUsername(), createCredentialRequest);
            } else  {
                createPasskey(createCredentialRequest);
            }

        }else if (additionalProperties.has("publicKeyCredentialRequestOptions")){
            publicKeyCredentialCreationOptions = JsonParser.parseString(additionalProperties.get("publicKeyCredentialRequestOptions").toString()).getAsJsonObject();
            Log.d("Challenge", publicKeyCredentialCreationOptions.toString());
        }



    }

    private void savePassword(String password, String username,String passkeyCreationRequest){
        CreatePasswordRequest createPasswordRequest = new CreatePasswordRequest(username, password);
        credentialManager.createCredentialAsync(
                context,
                createPasswordRequest,
                new CancellationSignal(),
                ContextCompat.getMainExecutor(context),
                new CredentialManagerCallback<CreateCredentialResponse, CreateCredentialException>() {
                    @Override
                    public void onResult(CreateCredentialResponse result) {
                        Log.i("Passkeys flow manager", "Password saved");
                        // After saving the password, proceed to create the passkey
                        createPasskey(passkeyCreationRequest);
                    }

                    @Override
                    public void onError(@NonNull CreateCredentialException e) {
                        Log.e("Passkeys flow manager", "Error saving password", e);
                        view.hideLoading();
                        view.showToastMessage(e.getMessage());
                    }
                }
        );
    }

    private void registerPasskeyOnTheServer(CreateCredentialResponse createCredentialResponse) {
        Log.d("Passkeys flow manager", "Start complete register with CreateCredentialResponse");
        try {
            CreatePublicKeyCredentialResponse response = (CreatePublicKeyCredentialResponse) createCredentialResponse;
            String responseString = response.getRegistrationResponseJson();
            dataManager.setOrigin(responseString);
            String regResponseInBase64 = URLEncoder.encode(responseString, StandardCharsets.UTF_8.toString());
            String completeSignUpRequest = passkeysUrlHelper.configureCompleteSignUpUri(regResponseInBase64, dataManager);
            ConnectionManager.getInstance().executeGetRequest(completeSignUpRequest, new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    view.hideLoading();
                    view.showToastMessage(e.getMessage());
                    Log.e("Passkeys flow manager", "Error in complete registration ", e);
                }
                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.body() != null && response.code() == 200) {
                        String responseBody = response.body().string();
                        Log.d("Passkeys flow manager", "Registration complete response body: " + responseBody);
                        try {
                            JSONObject responseJson = new JSONObject(responseBody);
                            Log.d("Passkeys flow manager", responseJson.toString());
                            if (responseJson.has("httpBody")) {
                                JSONObject httpBody = responseJson.getJSONObject("httpBody");
                                JSONObject error = httpBody.getJSONObject("error");
                                JSONArray detailsArr = error.getJSONArray("details");
                                JSONObject detailsJson = detailsArr.getJSONObject(0);
                                JSONObject rawResponse = detailsJson.getJSONObject("rawResponse");
                                String message = rawResponse.get("message").toString();
                                Log.d("Passkeys flow manager", "Received signUp response: " + message);
                                view.showToastMessage("Register to passkey error: " + message);
                            } else if (!dataManager.getUsername().isEmpty()) {
                                String welcomeMsg = String.format("%s is registered!", dataManager.getUsername());
                                view.showToastMessage(welcomeMsg);
                            }
                        } catch (JSONException e) {
                            view.hideLoading();
                            view.showToastMessage("Register to passkey failed");
                            Log.e("Passkeys flow manager", "Error parsing response", e);
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
            Log.e("Passkeys flow manager", "Error in complete registration", e);
        }
    }

    private void proceedWithPasskeyCredential(PublicKeyCredential credential) throws IOException, JSONException {
        Log.i("Passkeys flow manager", "proceedWithPasskeyCredential");
        try {
            /*
             * Get attestation response object from google credential manager res
             * EXAMPLE:
             * {
             * "rawId": "PRGK5tnIh6k4LbhQQmp_fQ",
             * "authenticatorAttachment": "platform",
             * "type": "public-key",
             * "id": "PRGK5tnIh6k4LbhQQmp_fQ",
             * "response": {
             *   "clientDataJSON": "eyJ0eXBlIjoid2ViYXV0aG4uZ2V0IiwiY2hhbGxlbmdlIjoiRDVCM3hLZkUzaG9CRnZiM29fREZiVTdnZzR1dFZVTGdSVDNkbWZBU04xSSIsIm9yaWdpbiI6ImFuZHJvaWQ6YXBrLWtleS1oYXNoOjE3VXdyYmhLQWk3QktCZGUyZkVpT013Y3huMlNaQ0ltQjB0ZVpMcW1vSGsiLCJhbmRyb2lkUGFja2FnZU5hbWUiOiJjb20ucGluZ2lkZW50aXR5LnBpbmdvbmUifQ",
             *   "authenticatorData": "iNBZt8M8FphGhMJY3AT_ibgBzF6jTUHqfw4QTiK809IdAAAAAA",
             *   "signature": "MEQCIDG76OAR1NGS3nQHY3yo33XtEYG60Ju6mkVxI0-EWppgAiB7Zl0nS2sOLnWfVVN3jp8pCcaBLT_wewUUfN92-ueE5Q",
             *   "userHandle": "Hb1l-zHAvkSnpzeJcQK1Wt271aUcfcP5DwF9l1jGSV0"
             * },
             * "clientExtensionResults": {}
             * }
             */
            String authResponseString = credential.getAuthenticationResponseJson();
            Log.d("Passkeys flow manager", authResponseString);
            dataManager.setOrigin(authResponseString);

            String authResponseStringBase64 = URLEncoder.encode(authResponseString, StandardCharsets.UTF_8.toString());
            String url = passkeysUrlHelper.configureCompleteSignInUri(authResponseStringBase64, dataManager);
            Log.d("Passkeys flow manager", "Complete SignIn request");
            ConnectionManager.getInstance().executeGetRequest(
                    url,
                    new Callback(){
                        @Override
                        public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                            Log.i("Passkeys flow manager", "Complete SignIn response");
                            view.hideLoading();
                            if (response.body() != null) {
                                String responseBody = response.body().string();
                                Log.d("Passkeys flow manager", "Complete SignIn response: " + responseBody);
                                try {
                                    JSONObject responseJson = new JSONObject(responseBody);
                                    if (responseJson.has("httpBody")) {
                                        JSONObject httpBody = responseJson.getJSONObject("httpBody");
                                        JSONObject error = httpBody.getJSONObject("error");
                                        JSONArray detailsArr = error.getJSONArray("details");
                                        JSONObject detailsJson = detailsArr.getJSONObject(0);
                                        JSONObject rawResponse = detailsJson.getJSONObject("rawResponse");
                                        Log.d("Passkeys flow manager", "rawResponse: " + rawResponse);
                                        String code = rawResponse.get("code").toString();
                                        Log.d("Passkeys flow manager", "code: " + code);
                                        if (response.code() == 200 && code.equals("NOT_FOUND")) {
                                            // Need to register, no passkey found for this user
                                            view.promptSignUp();
                                            return;
                                        }
                                    }
                                    if (responseJson.has("additionalProperties")){
                                        JSONObject additionalProperties = responseJson.getJSONObject("additionalProperties");
                                        dataManager.setUsername(additionalProperties.get("username").toString());
                                    }
                                    if (responseJson.has("success") && responseJson.getBoolean("success")){
                                        view.showSimpleAlertDialogue(
                                                "Success",
                                                String.format(
                                                        "Hello, %s, you have successfully signed in!",
                                                        dataManager.getUsername()
                                                )
                                        );
                                    }
                                } catch (JSONException e) {
                                    throw new RuntimeException(e);
                                }
                            }

                        }

                        @Override
                        public void onFailure(@NonNull Call call, @NonNull IOException e) {
                            view.hideLoading();
                            view.showToastMessage(e.getMessage());
                            Log.e("Passkeys flow manager", "Sign in request failed", e);
                        }
                    }
            );
        } catch( final Exception e){
            view.hideLoading();
            view.showToastMessage(e.getMessage());
            Log.e("Passkeys flow manager", "Error in complete sign in", e);
        }
    }

    private void fetchCredentialAccordingToServerResponse(String responseBody){
        try {
            JSONObject responseJson = new JSONObject(responseBody);
            /*
             * additionalProperties.publicKeyCredentialRequestOptions → this is the
             * actual FIDO2/WebAuthn challenge your app must now pass to Android’s
             * passkey APIs.
             */
            JSONObject additionalProperties = responseJson.getJSONObject("additionalProperties");
            Log.d("Passkeys flow manager", additionalProperties.toString());
            /*
             * deviceAuthenticationId → you’ll need to echo this back when sending the
             * signed assertion
             */
            dataManager.setDeviceAuthenticationId(additionalProperties.get("deviceAuthenticationId").toString());

            // Parse options for passkey
            JSONObject publicKeyCredentialRequestOptions = new JSONObject(
                    additionalProperties.get("publicKeyCredentialRequestOptions").toString());
            // Convert challenge array to Base64URL
            JSONArray challengeArray = publicKeyCredentialRequestOptions.getJSONArray("challenge");
            byte[] challengeBytes = new byte[challengeArray.length()];
            for (int i = 0; i < challengeArray.length(); i++) {
                challengeBytes[i] = (byte) ((challengeArray.getInt(i)) & 0xFF);
            }
            // encode challenge bytes to String
            String challengeBase64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes);
            publicKeyCredentialRequestOptions.put("challenge", challengeBase64Url);
            publicKeyCredentialRequestOptions.put("username", dataManager.getUsername());
            GetPublicKeyCredentialOption getPublicKeyCredentialOption
                    = new GetPublicKeyCredentialOption(
                    publicKeyCredentialRequestOptions.toString());
            tryToRetrievePasskey(getPublicKeyCredentialOption);
        } catch (JSONException e) {
            view.showToastMessage(e.getMessage());
            view.hideLoading();
            Log.e("Passkeys flow manager", "Error parsing authorization response", e);
        }
    }

    private void tryToRetrievePasskey(GetPublicKeyCredentialOption getPublicKeyCredentialOption){
        // Build a get credential request for Credential Manager
        GetCredentialRequest getCredentialRequest = new GetCredentialRequest.Builder()
                .addCredentialOption(getPublicKeyCredentialOption)
                .setPreferImmediatelyAvailableCredentials(false)
                .build();
        // Get credential from google credential manager
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
                            proceedWithPasskeyCredential(credential);
                        } catch (IOException | JSONException e) {
                            view.hideLoading();
                            Log.d("Passkeys flow manager", "No credential available, starting singUp flow. Error:" + e.getMessage());
                            view.promptSignUp();
                        }
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        if (e instanceof NoCredentialException || e instanceof GetCredentialCancellationException) {
                            Log.e("Passkeys flow manager", "No credential available, starting singUp flow.");
                            view.promptSignUp();
                        } else {
                            view.showToastMessage(e.getMessage());
                            view.hideLoading();
                            Log.e("Passkeys flow manager", "Error retrieving credential", e);
                        }
                    }
                }
        );
    }
    private void createPasskey(String passkeyCreationRequest){
        Log.d("createCredentialRequest:", passkeyCreationRequest);
        Log.i("Passkeys flow manager", "Create passkey");
        @SuppressLint("PublicKeyCredential")
        CreatePublicKeyCredentialRequest createPublicKeyCredentialRequest =
                new CreatePublicKeyCredentialRequest(
                        /*
                         * Contains the request in JSON format. Uses the standard WebAuthN
                         * web JSON spec.
                         */
                        passkeyCreationRequest,
                        null,
                        /*
                         * Defines whether you prefer to use only immediately available
                         * credentials, not hybrid credentials, to fulfill this request.
                         */
                        true,
                        null,
                        true,
                        BuildConfig.IS_AUTO_ENROLLMENT_ENABLED

        );
        /*
         * Execute CreateCredentialRequest asynchronously to register credentials
         * for a user account. Handle success and failure cases with the result and exceptions.
         */
        credentialManager.createCredentialAsync(
                context,
                createPublicKeyCredentialRequest,
                new CancellationSignal(),
                ContextCompat.getMainExecutor(context),
                new CredentialManagerCallback<CreateCredentialResponse, CreateCredentialException>() {
                    @Override
                    public void onResult(CreateCredentialResponse createCredentialResponse){
                        view.hideLoading();
                        Log.i("Passkeys flow manager", "Created credential with type " + createCredentialResponse.getType());
                        registerPasskeyOnTheServer(createCredentialResponse);
                    }
                    @Override
                    public void onError(@NonNull CreateCredentialException e) {
                        view.hideLoading();
                        view.showToastMessage(e.getMessage());
                        Log.e("Passkeys flow manager", "Error creating passkey", e);
                    }
                }
        );
    }

}
