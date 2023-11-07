package com.termux.shared.settings.preferences;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A class that holds {@link SharedPreferences} objects for apps.
 */
public class AppSharedPreferences {

    /**
     * The {@link Context} for operations.
     */
    protected final Context mContext;

    /**
     * The {@link SharedPreferences} that ideally should be created with {@link SharedPreferenceUtils#getPrivateSharedPreferences(Context, String)}.
     */
    protected final SharedPreferences mSharedPreferences;


    protected AppSharedPreferences(@NonNull Context context, @Nullable SharedPreferences sharedPreferences) {
        mContext = context;
        mSharedPreferences = sharedPreferences;
    }

    /**
     * Get {@link #mContext}.
     */
    public Context getContext() {
        return mContext;
    }

}
