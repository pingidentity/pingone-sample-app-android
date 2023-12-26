package com.pingidentity.pingone.passkeys;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pingidentity.pingone.BuildConfig;

import org.json.JSONException;
import org.json.JSONObject;
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
    public void setOrigin(String origin) {
        this.origin = origin;
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
    public String preparePublicKeyCredentialCreationOptions(JsonObject publicKeyCredentialCreationOptions) throws JSONException {
        // convert user id to String
        JsonArray byteArrayUserId = publicKeyCredentialCreationOptions.getAsJsonObject("user").getAsJsonArray("id");
        String userId = convertByteArrayToString(byteArrayUserId);
        publicKeyCredentialCreationOptions.get("user").getAsJsonObject().addProperty("id", userId);
        // convert challenge
        JsonArray byteArrayChallenge = publicKeyCredentialCreationOptions.getAsJsonArray("challenge");
        String challengeAsString = convertByteArrayToString(byteArrayChallenge);
        publicKeyCredentialCreationOptions.addProperty("challenge", challengeAsString);
        // set origin
        publicKeyCredentialCreationOptions.addProperty("origin", BuildConfig.PASSKEYS_RP_ID);;
        return publicKeyCredentialCreationOptions.toString();
    }
    public static String getOriginFromAssertion(String json) throws JSONException {
        JSONObject assertionObj = new JSONObject(json);
        JSONObject originParentObj;
        if (assertionObj.has("response")) {
            JSONObject responseObj = assertionObj.getJSONObject("response");
            String clientJSONString = responseObj.getString("clientDataJSON");
            String decodedJSONString = new String(Base64.getDecoder().decode(clientJSONString));
            originParentObj = new JSONObject(decodedJSONString);
        } else {
            originParentObj = assertionObj;
        }
        return originParentObj.getString("origin");
    }

    public static String convertByteArrayToString(JsonArray arrayOfBytes) {
        byte[] newChallenge = new byte[arrayOfBytes.size()];
        for (int i = 0; i < arrayOfBytes.size(); i++) {
            newChallenge[i] = (byte) (arrayOfBytes.get(i).getAsInt() & 0xFF);
        }
        return Base64.getUrlEncoder().encodeToString(newChallenge).replace("=", "");
    }
}
