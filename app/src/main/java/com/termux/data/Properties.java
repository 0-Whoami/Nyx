package com.termux.data;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.function.BiConsumer;

public final class Properties {
    private final HashMap<String, String> map = new HashMap<>();

    public Properties(String filepath) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(filepath));
            String line;
            while ((line = br.readLine()) != null) {
                String[] args = line.split(" : ");
                map.put(args[0], args[1]);
            }
        } catch (Throwable t) {
        }
    }

    public void forEach(BiConsumer<String, String> action) {
        map.forEach(action);
    }

    String get(String key, String defaultValue) {
        var s = map.get(key);
        return s == null ? defaultValue : s;
    }

    int getInt(String key, int defaultValue) {
        var s = map.get(key);
        return s == null ? defaultValue : Integer.parseInt(s);
    }

    boolean getBoolean(String key, boolean defaultValue) {
        var s = map.get(key);
        return s == null ? defaultValue : Boolean.parseBoolean(s);
    }
}
