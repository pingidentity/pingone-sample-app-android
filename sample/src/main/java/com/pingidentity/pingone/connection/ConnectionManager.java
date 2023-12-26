package com.pingidentity.pingone.connection;

import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;

/**
 * Singleton class
 * This class is responsible for executing HTTP requests using OkHttp library
 */
public class ConnectionManager {

    private static OkHttpClient client;
    private static ConnectionManager instance;

    private ConnectionManager (){}

    synchronized public static ConnectionManager getInstance() {
        if (instance == null) {
            client = new OkHttpClient();
            instance = new ConnectionManager();
        }
        return instance;
    }

    public void executeGetRequest(String url, Callback callback) {
        Request request = new Request.Builder()
                .url(url)
                .build();
        executeRequest(request, callback);
    }

    private void executeRequest(Request request, Callback callback) {
        client.newCall(request).enqueue(callback);
    }
}
