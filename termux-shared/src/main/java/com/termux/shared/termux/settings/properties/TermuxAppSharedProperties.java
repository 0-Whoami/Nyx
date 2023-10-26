package com.termux.shared.termux.settings.properties;

import android.content.Context;
import androidx.annotation.NonNull;
import com.termux.shared.termux.TermuxConstants;

public class TermuxAppSharedProperties extends TermuxSharedProperties {

    private static TermuxAppSharedProperties properties;

    private TermuxAppSharedProperties(@NonNull Context context) {
        super(context, TermuxConstants.TERMUX_APP_NAME, TermuxConstants.TERMUX_PROPERTIES_FILE_PATHS_LIST, TermuxPropertyConstants.TERMUX_APP_PROPERTIES_LIST, new SharedPropertiesParserClient());
    }

    /**
     * Initialize the {@link #properties} and load properties from disk.
     *
     * @param context The {@link Context} for operations.
     */
    public static void init(@NonNull Context context) {
        if (properties == null)
            properties = new TermuxAppSharedProperties(context);
    }

    /**
     * Get the {@link #properties}.
     *
     * @return Returns the {@link TermuxAppSharedProperties}.
     */
    public static TermuxAppSharedProperties getProperties() {
        return properties;
    }
}
