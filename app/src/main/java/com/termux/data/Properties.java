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

    public Properties(String filepath) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(filepath));
            String line;
            while (null != (line = br.readLine())) {
                String[] args = Properties.PATTERN.split(line);
                map.put(args[0], args[1]);
            }
        } catch (Throwable ignored) {
        }
    }

    public void forEach(BiConsumer<? super String, ? super String> action) {
        map.forEach(action);
    }

    String get(String key, String defaultValue) {
        var s = map.get(key);
        return null == s ? defaultValue : s;
    }

    public int getInt(String key, int defaultValue) {
        var s = map.get(key);
        return null == s ? defaultValue : Integer.parseInt(s);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        var s = map.get(key);
        return null == s ? defaultValue : Boolean.parseBoolean(s);
    }
}
