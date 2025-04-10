# PingOne MFA Mobile SDK sample app

## Overview

PingOne MFA Mobile SDK is a set of components and services targeted at enabling organizations to include multifactor authentication (MFA) into native applications.
This solution leverages Ping Identity’s expertise in MFA technology, as a component that can be embedded easily and quickly into a new or existing application. The PingOne MFA Mobile SDK package comprises of the following components:

* A sample app example source code for Android.
* Mobile Authentication Framework for Android Developers (integrated into the sample app).

Release notes can be found [here](./release-notes.md).

**Note:** The PingOne MFA Mobile SDK library for Android applications can be found [here](https://github.com/pingidentity/pingone-mobile-sdk-android).

### Documentation

Reference documentation is available for PingOne MFA Mobile SDK, describing its capabilities, features, installation and setup, integration with mobile apps, deployment and more:

* [Introduction to PingOne MFA](https://docs.pingidentity.com/csh?Product=p1&context=p1mfa_c_introduction)
* [PingOne MFA Mobile SDK Overview](https://apidocs.pingidentity.com/pingone/native-sdks/v1/api/#pingone-mfa-sdk-for-android)

### Content 
1. [Getting started](#getting_started)
   1. [Configure](#11-configure)
   2. [Pairing](#12-pairing)
   3. [Send logs](#13-send-logs)
   4. [Get one time passcode](#14-get-one-time-passcode)
   5. [Authentication via QR code scanning](#15-authentication-via-qr-code-scanning)
   6. [Test remote notification](#16-testPush)
2. [Mobile Authentication Framework](#2-mobile-authentication-framework)
3. [Migrate from PingID SDK to PingOne MFA SDK](#3-migrate-from-pingid-sdk-to-pingone-mfa-sdk)
   1. [Manual flow](#31-manual-flow)
   2. [Push notification flow](#32-push-notification-flow)
4. [Passkeys Implementation](./Passkeys/Passkeys_Implementation.md)

<a name="getting_started"></a>
### 1.0 Getting started

The PingOne MFA Mobile SDK bundle provides a sample app that includes all the basic flows in order to help you get started.

<a name="11-configure"></a>
#### 1.1 Configure the SDK

You must configure the SDK at least once before using it. See the SDK documentation [here](https://github.com/pingidentity/pingone-mobile-sdk-android#17-configure-sdk).

<a name="12-pairing"></a>
#### 1.2 Pairing

To manually pair the device, call the following method with your pairing key:

```java  
public static void pair(Context context, String pairingKey, PingOneSDKPairingCallback callback);  
```  

To automatically pair the device using OpenID Connect:

1. Call this method to get the PingOne MFA SDK mobile payload:
```java  
public static String generateMobilePayload(Context context);
```  
2. Pass the received mobile payload on the OIDC request as the value of query param: `mobilePayload`
3. Call this function with the ID token after the OIDC authentication completes:
```java  
public static void processIdToken(String idToken, PingOnePairingObjectCallback callback);  
```  

<a name="13-send-logs"></a>
#### 1.3 Send Logs

The PingOne MFA Mobile SDK bundle writes fixed size, log messages to memory. To send these logs to our server for support, call the
```public static void sendLogs(Context context, PingOneSendLogsCallback callback)``` method.  
For example:
 ```java
 PingOne.sendLogs(context, new PingOne.PingOneSendLogsCallback() {  
     @Override public void onComplete(@Nullable final String supportId, @Nullable PingOneSDKError pingOneSDKError) {
         if(supportId!=null){ 
             // pass the supportId value to PingOne support team 
         } 
     }
});  
```  

<a name="14-get-one-time-passcode"></a>
#### 1.4 Get One Time Passcode

Requests the SDK to provide One Time Passcode.

Signature:
```java  
public static void getOneTimePassCode(Context context, PingOneOneTimePasscodeCallback callback); 
```

For example:
 ```java  
 PingOne.getOneTimePassCode(context, new PingOne.PingOneOneTimePasscodeCallback() {
     @Override public void onComplete(@Nullable OneTimePasscodeInfo otpData, @Nullable PingOneSDKError error) {
         //handle response 
      }
 };  
```  

<a name="15-authentication-via-qr-code-scanning"></a>
#### 1.5 Authentication via QR code scanning

PingOne MFA SDK provides an ability to authenticate via scanning the QR code (or typing the code manually). The code should  be passed to the PingOne MFA SDK using the following API method:

```java  
PingOne.authenticate(context, authCode, new PingOne.PingOneAuthenticationCallback() {
    @Override public void onComplete(@Nullable AuthenticationObject authObject, @Nullable PingOneSDKError error){
        if (authObject != null){
            //parse authObject (see below) 
        } 
    }
});  
```  

authCode should be passed as is or inside a URI. For example: "7F45HGf5", "https://myapp.com/pingonesdk?authentication_code=7F45HGf5", "pingonesdk?authentication_code=7F45HGf5"

AuthenticationObject is implemented as Parcelable to provide the developers an ability to  
pass it between activities and/or fragments and contains the following fields and methods:
```java  
public class AuthenticationObject {
    //for inner use
    String requestId;
    //for inner use 
    String authCode; 
    /* 
     * a JsonArray of users. See UserModel below for further understanding 
     * what it contains. 
     */ 
    JsonArray users;
    //for passing any data from server to end-user 
    String clientContext; 
    /* 
     * a status String value returned from a server when user calls an authenticate API method 
     * Possible values at this step: 
     * CLAIMED 
     * EXPIRED 
     * COMPLETED 
     */ 
     String status; 
    /* 
     * String that determines if user approval is required to complete an authentication
     * Possible values: 
     * REQUIRED 
     * NOT_REQUIRED 
     */ 
     String needsApproval;
     /* 
      * if userApproval is "REQUIRED" the approve or deny method should be called with a userId
      * of the user, who triggered the method. The application should register for a callback 
      * that will return Status with one of the following values: 
      * COMPLETED 
      * EXPIRED 
      * DENIED 
      */ 
      public void approve(Context context, String userId, PingOneAuthenticationStatusCallback callback); 
      public void deny(Context context, String userId, PingOneAuthenticationStatusCallback callback);
}  
```  
The JsonArray of users may be parsed to the array of following model:
```java  
public class UserModel{  
    String userId; 
    String email; 
    String given; 
    String family; 
    String username;
}  
```  
<a name="16-testPush"></a>
#### 1.6 Test remote notification

For paired devices, it is possible to test push notification functionality using the `testRemoteNotification` method:

```java 
public static void testRemoteNotification(Context context, PingOneRegion region, PingOneTestRemoteNotificationCallback callback);
```

<a name="2-mobile-authentication-framework"></a>
### 2. Mobile Authentication Framework

The sample code contains two modules: `PingAuthenticationUI` and `PingAuthenticationCore`.  
The following method starts an authentication process when the user taps "Authentication API" on the main screen. The authentication process is completed by the PingFederate Authentication API.  
**Note:** Before calling this method, you must set-up your `OIDC_ISSUER` and `CLIENT_ID` in the `gradle.properties` class at `PingAuthenticationCore` module. See [Authentication API for Android Developers](https://github.com/pingidentity/mobile-authentication-framework-android)
 ```java 
 public void authenticate(@NonNull Activity context, @NonNull String mobilePayload, @Nullable String dynamicData)  
```  
This is public method of PingAuthenticationUI module, which should be instantiated first as follows:
```java  
PingAuthenticationUI authenticationUI = new PingAuthenticationUI();  
authenticationUI.authenticate(context, mobilePayload, dynamicData);  
```

<a name="3-migrate-from-pingid-sdk-to-pingone-mfa-sdk"></a>
### 3. Migrate from PingID SDK to PingOne MFA SDK

If your application is currently integrated with PingID SDK, it is possible to migrate to PingOne MFA SDK.
First, make sure to set up the PingOne environment in the admin console following the convergence documentation.
Then set up mobile application as follows:
1. Remove the `PingID_SDK.aar` library file from the `libs` folder of your application and remove any calls to that SDK.
2. Setup a PingOne MFA mobile SDK as described in the [set-up section](https://github.com/pingidentity/pingone-mobile-sdk-android/blob/master/README.md#16-add-the-pingone-sdk-component-into-your-existing-project) and implement the API methods as described in the [PingOne MFA Mobile SDK sample app](#1-sample-app).

<a name="31-manual-flow"></a>
#### 3.1 Manual flow

Call the migration API method:
```java 
/**
 * Migrates the PingID SDK application to the PingOne platform
 *
 * @param context the context of calling application
 * @param callback the PingOneMigrationStatusCallback object that will receive the result
 */
PingOne.migrateFromPingID(Context context, PingOneMigrationStatusCallback callback);
```
The `onComplete()` method of the callback will be triggered at the migration process completion and will receive `MigrationStatus` object, `PairingInfo` object and `PingOneSDKError` object, where `MigrationStatus` is one of the following:
```java
/**
 * enum that represents the migration status returned from the SDK
 */
public enum MigrationStatus {
   /*
    * There is no PingID data that has to be migrated
    */
   NOT_NEEDED,
   /*
    * The migration process was completed successfully
    */
   DONE,
   /*
    * The migration process failed
    */
   FAILED,
   /*
    * The migration process failed due to server error, client can try again
    */
   TEMPORARILY_FAILED,
   /*
    * The migration process is in progress
    */
   IN_PROGRESS
}
```
For example:
```java 
PingOne.migrateFromPingID(context, new PingOne.PingOneMigrationStatusCallback() {
   @Override
   public void onComplete(MigrationStatus migrationStatus, @Nullable PairingInfo pairingInfo, @Nullable PingOneSDKError error) {
       /*
        * check migrationStatus and continue accordingly:
        * if the status is FAILED the error object will contain the details of the error
        * if the status is TEMPORARILY_FAILED the client can retry the process (may happen due to connectivity issues, etc.)
        */  
   } 
});
```
Possible errors returned from the migration API:
```java 
MIGRATION_ALREADY_RUNNING(10014, "Migration is already in progress - you cannot make another API call until it is completed")
MIGRATION_NOT_NEEDED(10015, "The device does not have to be migrated because it is already paired.")
```

<a name="32-push-notification-flow"></a>
#### 3.2 Push notification flow

Upon getting authentication push notification, the migration will start **automatically** in a background thread.
When migration is completed, the PingOne `NotificationObject` will be returned to the application in the `PingOne.processRemoteNotification()` callback response.


## Disclaimer

THE SAMPLE CODE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR  
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,  
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE  
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER  
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,  
OUT OF OR IN CONNECTION WITH THE SAMPLE CODE OR THE USE OR OTHER DEALINGS IN  
THE SAMPLE CODE.  FURTHERMORE, THIS SAMPLE CODE IS NOT COMMERCIALLY SUPPORTED BY PING IDENTITY BUT QUESTIONS MAY BE ADDRESSED TO PING'S SUPPORT CENTER OR MAY BE OTHERWISE ADDRESSED IN THE RELATED DOCUMENTATION.

Any questions or issues should go to the support center, or may be discussed in the [Ping Identity developer communities](https://support.pingidentity.com/s/topic/0TO1W000000atTxWAI/pingone-mfa)
