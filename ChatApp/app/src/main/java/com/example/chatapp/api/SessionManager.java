package com.example.chatapp.api;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private static final String PREF_NAME = "ChatAppSession";
    private static final String KEY_TOKEN = "jwt_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USERNAME = "username";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveAuthToken(String token) {
        prefs.edit().putString(KEY_TOKEN, token).apply();
    }

    public String fetchAuthToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public void saveUserDetails(String id, String username) {
        prefs.edit()
             .putString(KEY_USER_ID, id)
             .putString(KEY_USERNAME, username)
             .apply();
    }

    public String getUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }

    public String getUsername() {
        return prefs.getString(KEY_USERNAME, null);
    }

    public void clearSession() {
        prefs.edit().clear().apply();
    }
}
