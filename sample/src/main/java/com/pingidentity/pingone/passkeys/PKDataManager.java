package com.pingidentity.pingone.passkeys;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pingidentity.pingone.BuildConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class PKDataManager {

    private String userId;
    private String deviceId;
    private String deviceAuthenticationId;
    private String origin;
    private String username;

    public String getUserId() {
        return userId;
    }
    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDeviceId() {
        return deviceId;
    }
    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceAuthenticationId() {
        return deviceAuthenticationId;
    }
    public void setDeviceAuthenticationId(String deviceAuthenticationId) {
        this.deviceAuthenticationId = deviceAuthenticationId;
    }

    public String getOrigin() {
        return origin;
    }
    public void setOrigin(String assertion) {
        JSONObject assertionObj;
        try {
            assertionObj = new JSONObject(assertion);

            JSONObject originParentObj;
            if (assertionObj.has("response")) {
                // Step 1: Extract the response object
                JSONObject responseObj = assertionObj.getJSONObject("response");
                // Step 2: clientDataJSON is Base64URL-encoded
                String clientJSONString = responseObj.getString("clientDataJSON");
                // Step 3: Decode the JSON string
                String decodedJSONString = new String(
                        Base64.getUrlDecoder().decode(clientJSONString),  // Use URL decoder for WebAuthn
                        StandardCharsets.UTF_8
                );
                // Step 4: Parse decoded JSON
                originParentObj = new JSONObject(decodedJSONString);
            } else {
                originParentObj = assertionObj;
            }
            // Step 5: origin (e.g. "android:apk-key-hash:XXXX")
            this.origin = originParentObj.getString("origin");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }

    /*
     * Prepares a string request to the Credential Manager API's
     */
    public String preparePublicKeyCredentialCreationOptions(JsonObject publicKeyCredentialCreationOptions){
        // convert user id to String
        JsonArray byteArrayUserId = publicKeyCredentialCreationOptions.getAsJsonObject("user").getAsJsonArray("id");
        String userId = convertByteArrayToString(byteArrayUserId);
        publicKeyCredentialCreationOptions.get("user").getAsJsonObject().addProperty("id", userId);
        // convert challenge
        JsonArray byteArrayChallenge = publicKeyCredentialCreationOptions.getAsJsonArray("challenge");
        String challengeAsString = convertByteArrayToString(byteArrayChallenge);
        publicKeyCredentialCreationOptions.addProperty("challenge", challengeAsString);
        // set origin
        publicKeyCredentialCreationOptions.addProperty("origin", BuildConfig.PASSKEYS_RP_ID);
        // set excludeCredentials to empty array to avoid errors, in real implementations this should
        // contain the list of existing credentials to prevent creating duplicates
        publicKeyCredentialCreationOptions.add("excludeCredentials", new JsonArray());
        return publicKeyCredentialCreationOptions.toString();
    }

    public static String convertByteArrayToString(JsonArray arrayOfBytes) {
        byte[] newChallenge = new byte[arrayOfBytes.size()];
        for (int i = 0; i < arrayOfBytes.size(); i++) {
            newChallenge[i] = (byte) (arrayOfBytes.get(i).getAsInt() & 0xFF);
        }
        return Base64.getUrlEncoder().encodeToString(newChallenge).replace("=", "");
    }
}
