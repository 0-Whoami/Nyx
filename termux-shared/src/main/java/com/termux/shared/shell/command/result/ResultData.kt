package com.termux.shared.shell.command.result;


import com.termux.shared.errors.Error;

import java.util.ArrayList;

public class ResultData {

    /**
     * The stdout of command.
     */
    public final StringBuilder stdout = new StringBuilder();

    /**
     * The internal errors list of command.
     */
    private final Iterable<Error> errorsList = new ArrayList<>();

    public ResultData() {
    }

    /**
     * Get a log friendly {@link String} for {@link ResultData} parameters.
     *
     * @param resultData The {@link ResultData} to convert.
     * @return Returns the log friendly {@link String}.
     */
    public static String getResultDataLogString(final ResultData resultData) {
        if (resultData == null)
            return "null";

        return "\n\n" + getErrorsListLogString(resultData);
    }

    private static String getErrorsListLogString(final ResultData resultData) {
        if (resultData == null)
            return "null";
        StringBuilder logString = new StringBuilder();
        for (Error error : resultData.errorsList) {
            if (error.isStateFailed()) {
                if (!logString.toString().isEmpty())
                    logString.append("\n");

            }
        }
        return logString.toString();
    }

    public final boolean isStateFailed() {
        for (Error error : errorsList) {
            if (error.isStateFailed())
                return true;
        }
        return false;
    }

    @Override
    public final String toString() {
        return getResultDataLogString(this);
    }

}
