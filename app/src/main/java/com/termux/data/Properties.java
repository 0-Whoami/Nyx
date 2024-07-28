package com.termux.data;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

public final class Properties {
    private static final Pattern PATTERN = Pattern.compile(" : ");
    private final Map<String, String> map = new HashMap<>();

    public Properties(final String filepath) {
        try {
            final BufferedReader br = new BufferedReader(new FileReader(filepath));
            String line;
            while (null != (line = br.readLine())) {
                final String[] args = PATTERN.split(line);
                if (args.length == 2) map.put(args[0], args[1]);
            }
        } catch (final Throwable ignored) {
        }
    }

    public void forEach(final BiConsumer<? super String, ? super String> action) {
        map.forEach(action);
    }

    String get(final String key) {
        return map.get(key);
    }

    public int getInt(final String key, final int defaultValue) {
        final var s = map.get(key);
        return null == s ? defaultValue : Integer.parseInt(s);
    }

    public boolean getBoolean(final String key, final boolean defaultValue) {
        final var s = map.get(key);
        return null == s ? defaultValue : Boolean.parseBoolean(s);
    }
}
