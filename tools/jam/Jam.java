package doja.tools.jam;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;

/** 讀取 JAM 檔的鍵值對，保留原始順序與編碼。 */
public final class Jam {
    private final Map<String, String> values = new LinkedHashMap<String, String>();

    private Jam() {}

    public static Jam read(File file) throws IOException {
        Jam jam = new Jam();
        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), "ISO-8859-1"));
        try {
            String line;
            while ((line = in.readLine()) != null) {
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                if (key.length() != 0) jam.values.put(key, value);
            }
        } finally {
            in.close();
        }
        return jam;
    }

    public String get(String key) {
        return values.get(key);
    }

    public String getOrEmpty(String key) {
        String value = get(key);
        return value == null ? "" : value;
    }

    public String require(String key) throws IOException {
        String value = get(key);
        if (value == null || value.length() == 0) throw new IOException("JAM has no " + key);
        return value;
    }

    public int requireInt(String key) throws IOException {
        String value = require(key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IOException("JAM has invalid " + key + ": " + value);
        }
    }
}
