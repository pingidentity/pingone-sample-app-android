package com.pingidentity.pingone.passkeys;

import com.pingidentity.pingone.BuildConfig;

/*
 * Simple helper class that creates full URLs by request
 */
public class PasskeysUrlHelper {

    /*
     * takes a passkey assertion and device info, then returns a full GET URL that the server can
     * call to complete the sign-in
     */
    String configureCompleteSignInUri(String assertion, PKDataManager dataManager) {
        String baseUrl = getBaseUrl();
        return baseUrl
                /*
                 * This is the UUID you got earlier when initiating the passkey flow. It ties the
                 * challenge to a specific device authentication session.
                 */
                .concat("&deviceAuthenticationId=").concat(dataManager.getDeviceAuthenticationId())
                /*
                 * This is your relying party origin (like android:apk-key-hash:XYZ). It must match
                 * what the passkey challenge contained.
                 */
                .concat("&origin=").concat(dataManager.getOrigin())
                /*
                 * This is your relying party origin (like android:apk-key-hash:XYZ). It must match
                 * what the passkey challenge contained.
                 */
                .concat("&rpId=").concat(dataManager.getOrigin())
                /*
                 * The signed passkey assertion you got back from the Credential Manager. This includes:
                 * clientDataJSON
                 * authenticatorData
                 * signature
                 * (optionally) userHandle
                 * Together they prove “this device, with this private key, signed the challenge.”
                 */
                .concat("&assertion=").concat(assertion);
    }

    /*
     * prepares an HTTP GET request URL to register a new user with a username and password
     */
    String configureSignUpUrl(String username, String password){
        String baseUrl = getBaseUrl();
        /*
         * Do not do this in production. This is for demo purposes only.
         * Instead you may want to hash it then the server must be expecting hashed values.
         */
        return baseUrl.
                concat("&username=").concat(username).
                concat("&password=").concat(password);
    }

    /*
     * Construct an HTTP GET request to complete a Passkeys (FIDO/WebAuthn) sign-up flow.
     * This request sends the device/user info plus the attestation to your server to finish
     * registering a passkey for the user. The server can then validate the attestation and link
     * the credential to the user account.
     */
    String configureCompleteSignUpUri(String attestation, PKDataManager dataManager){
        String baseUrl = getBaseUrl();
        return baseUrl.
                /*
                 * unique ID of the device performing the registration
                 */
                concat("&deviceId=").concat(dataManager.getDeviceId()).
                /*
                 * identifies the user
                 */
                concat("&userId=").concat(dataManager.getUserId()).
                /*
                 * attestation object returned by the platform credential creation API. It proves the
                 * device generated a credential securely.
                 */
                concat("&attestation=").concat(attestation).
                /*
                 * the Relying Party ID, usually your app’s origin or package hash, must match the
                 * credential registration.
                 */
                concat("&rpId=").concat(dataManager.getOrigin());
    }

    /*
     * Base Url
     * https://auth.pingone.com/<your-environment-id>/as/authorize
     * ?client_id=<your-client-id>
     * &scope=openid
     * &response_type=code
     * &response_mode=pi.flow
     */
    String getBaseUrl(){
        return BuildConfig.PASSKEYS_ENDPOINT_PREFIX +
                BuildConfig.PASSKEYS_ENVIRONMENT_ID +
                /*
                 * That’s the authorize endpoint of PingOne. Normally, in OIDC, /as/authorize is
                 * where the OAuth2 Authorization Code flow begins.
                 */
                "/as/authorize?" +
                /*
                 * Your app’s registered client ID. The authorization server uses this to know
                 * which app is calling.
                 */
                "client_id=" + BuildConfig.PASSKEYS_CLIENT_ID +
                /*
                 * OIDC requires the openid scope. This tells PingOne “I want an ID token, not just OAuth.”
                 */
                "&scope=openid" +
                /*
                 * Standard OAuth2/OIDC: you’re asking for an authorization code, not a token
                 * directly. That code will later be exchanged for tokens.
                 */
                "&response_type=code" +
                /*
                 * This is PingOne–specific. Instead of a redirect-based web response, you’re
                 * asking the server to package the response into a PingOne Flow (JSON).
                 */
                "&response_mode=pi.flow";
    }
}
