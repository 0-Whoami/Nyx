package com.termux.shared.termux.shell;


import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TermuxShellUtils {


    /**
     * Setup shell command arguments for the execute. The file interpreter may be prefixed to
     * command arguments if needed.
     */

    public static String[] setupShellCommandArguments(final String executable, final String[] arguments) {
        // The file to execute may either be:
        // - An elf file, in which we execute it directly.
        // - A script file without shebang, which we execute with our standard shell $PREFIX/bin/sh instead of the
        //   system /system/bin/sh. The system shell may vary and may not work at all due to LD_LIBRARY_PATH.
        // - A file with shebang, which we try to handle with e.g. /bin/foo -> $PREFIX/bin/foo.
        String interpreter = null;
        try {
            final File file = new File(executable);
            try (final FileInputStream in = new FileInputStream(file)) {
                final byte[] buffer = new byte[256];
                final int bytesRead = in.read(buffer);
                if (4 < bytesRead) {
                    if (0x7F == buffer[0] && 'E' == buffer[1] && 'L' == buffer[2] && 'F' == buffer[3]) {
                        // Elf file, do nothing.
                    } else if ('#' == buffer[0] && '!' == buffer[1]) {
                        // Try to parse shebang.
                        final StringBuilder builder = new StringBuilder();
                        for (int i = 2; i < bytesRead; i++) {
                            final char c = (char) buffer[i];
                            if (' ' == c || '\n' == c) {
                                if (0 != builder.length()) {
                                    // End of shebang.
                                    final String shebangExecutable = builder.toString();
                                    if (shebangExecutable.startsWith("/usr") || shebangExecutable.startsWith("/bin")) {
                                        final String[] parts = shebangExecutable.split("/");
                                        final String binary = parts[parts.length - 1];
                                        interpreter = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/" + binary;
                                    }
                                    break;
                                }
                            } else {
                                builder.append(c);
                            }
                        }
                    } else {
                        // No shebang and no ELF, use standard shell.
                        interpreter = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/sh";
                    }
                }
            }
        } catch (final IOException e) {
            // Ignore.
        }
        final List<String> result = new ArrayList<>();
        if (null != interpreter)
            result.add(interpreter);
        result.add(executable);
        if (null != arguments)
            Collections.addAll(result, arguments);
        return result.toArray(new String[0]);
    }

}
