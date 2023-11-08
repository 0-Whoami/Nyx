package com.termux.shared.shell.command.result;

import androidx.annotation.NonNull;

import com.termux.shared.errors.Error;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ResultData implements Serializable {

    /**
     * The stdout of command.
     */
    public final StringBuilder stdout = new StringBuilder();

    /**
     * The internal errors list of command.
     */
    public List<Error> errorsList = new ArrayList<>();

    public ResultData() {
    }

    public boolean isStateFailed() {
        if (errorsList != null) {
            for (Error error : errorsList) if (error.isStateFailed())
                return true;
        }
        return false;
    }

    @NonNull
    @Override
    public String toString() {
        return getResultDataLogString(this);
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



    public static String getErrorsListLogString(final ResultData resultData) {
        if (resultData == null)
            return "null";
        StringBuilder logString = new StringBuilder();
        if (resultData.errorsList != null) {
            for (Error error : resultData.errorsList) {
                if (error.isStateFailed()) {
                    if (!logString.toString().isEmpty())
                        logString.append("\n");

                }
            }
        }
        return logString.toString();
    }

}
