package com.termux.shared.shell.command.result;

import android.app.PendingIntent;

import androidx.annotation.NonNull;

import java.util.Formatter;

public class ResultConfig {

    /**
     * Defines {@link PendingIntent} that should be sent with the result of the command. We cannot
     * implement {@link java.io.Serializable} because {@link PendingIntent} cannot be serialized.
     */
    public PendingIntent resultPendingIntent;



    /**
     * Defines the directory path in which to write the result of the command.
     */
    public String resultDirectoryPath;

    /**
     * Defines whether the result should be written to a single file or multiple files
     * (err, error, stdout, stderr, exit_code) in {@link #resultDirectoryPath}.
     */
    public boolean resultSingleFile;

    /**
     * Defines the basename of the result file that should be created in {@link #resultDirectoryPath}
     * if {@link #resultSingleFile} is {@code true}.
     */
    public String resultFileBasename;

    /**
     * Defines the output {@link Formatter} format of the {@link #resultFileBasename} result file.
     */
    public String resultFileOutputFormat;

    /**
     * Defines the error {@link Formatter} format of the {@link #resultFileBasename} result file.
     */
    public String resultFileErrorFormat;

    /**
     * Defines the suffix of the result files that should be created in {@link #resultDirectoryPath}
     * if {@link #resultSingleFile} is {@code true}.
     */
    public String resultFilesSuffix;

    public ResultConfig() {
    }

    public boolean isCommandWithPendingResult() {
        return resultPendingIntent != null || resultDirectoryPath != null;
    }

    @NonNull
    @Override
    public String toString() {
        return getResultConfigLogString(this, true);
    }

    /**
     * Get a log friendly {@link String} for {@link ResultConfig} parameters.
     *
     * @param resultConfig The {@link ResultConfig} to convert.
     * @param ignoreNull Set to {@code true} if non-critical {@code null} values are to be ignored.
     * @return Returns the log friendly {@link String}.
     */
    public static String getResultConfigLogString(final ResultConfig resultConfig, boolean ignoreNull) {
        if (resultConfig == null)
            return "null";
        StringBuilder logString = new StringBuilder();
        logString.append("Result Pending: `").append(resultConfig.isCommandWithPendingResult()).append("`\n");
        if (resultConfig.resultPendingIntent != null) {
            logString.append(resultConfig.getResultPendingIntentVariablesLogString(ignoreNull));
            if (resultConfig.resultDirectoryPath != null)
                logString.append("\n");
        }
        if (resultConfig.resultDirectoryPath != null && !resultConfig.resultDirectoryPath.isEmpty())
            logString.append(resultConfig.getResultDirectoryVariablesLogString(ignoreNull));
        return logString.toString();
    }

    public String getResultPendingIntentVariablesLogString(boolean ignoreNull) {
        if (resultPendingIntent == null)
            return "Result PendingIntent Creator: -";
        return "Result PendingIntent Creator: `" + resultPendingIntent.getCreatorPackage() + "`";
    }

    public String getResultDirectoryVariablesLogString(boolean ignoreNull) {
        if (resultDirectoryPath == null)
            return "Result Directory Path: -";
        return "";
    }

}
