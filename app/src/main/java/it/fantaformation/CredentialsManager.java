package it.fantaformation;

import android.content.Context;
import android.content.SharedPreferences;

public class CredentialsManager {

    private static final String PREF_NAME = "fanta_credentials";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";

    private final SharedPreferences prefs;

    public CredentialsManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveCredentials(String username, String password) {
        prefs.edit()
                .putString(KEY_USERNAME, username != null ? username.trim() : "")
                .putString(KEY_PASSWORD, password != null ? password.trim() : "")
                .apply();
    }

    public String getUsername() {
        return prefs.getString(KEY_USERNAME, "");
    }

    public String getPassword() {
        return prefs.getString(KEY_PASSWORD, "");
    }

    public boolean hasCredentials() {
        return !getUsername().isEmpty() && !getPassword().isEmpty();
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}