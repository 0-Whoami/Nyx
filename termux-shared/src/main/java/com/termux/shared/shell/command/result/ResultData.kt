package com.termux.shared.shell.command.result

import com.termux.shared.errors.Error


class ResultData {
    /**
     * The stdout of command.
     */
    @JvmField
    val stdout: StringBuilder = StringBuilder()

    /**
     * The internal errors list of command.
     */
    private val errorsList: Iterable<Error> = ArrayList()

    val isStateFailed: Boolean
        get() {
            for (error in errorsList) {
                if (error.isStateFailed) return true
            }
            return false
        }

    override fun toString(): String {
        return getResultDataLogString(
            this
        )
    }

    companion object {
        /**
         * Get a log friendly [String] for [ResultData] parameters.
         *
         * @param resultData The [ResultData] to convert.
         * @return Returns the log friendly [String].
         */
        fun getResultDataLogString(resultData: ResultData?): String {
            if (resultData == null) return "null"

            return """
                 
                 
                 ${getErrorsListLogString(resultData)}
                 """.trimIndent()
        }

        private fun getErrorsListLogString(resultData: ResultData?): String {
            if (resultData == null) return "null"
            val logString = StringBuilder()
            for (error in resultData.errorsList) {
                if (error.isStateFailed) {
                    if (!logString.toString().isEmpty()) logString.append("\n")
                }
            }
            return logString.toString()
        }
    }
}
