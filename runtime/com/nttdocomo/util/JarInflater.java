package com.nttdocomo.util;

import doja.Resources;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 直接讀取建置時預先解包好的資源，以節約真機解壓 ZIP/JAR 的運算成本；
 * 針對 MLD 檔名則會自動映射至轉碼後的音訊資料。
 */
public class JarInflater {
    private static final String ASSET_PREFIX = "/assets/";
    private final String archiveId;

    public JarInflater(InputStream input) throws JarFormatException {
        archiveId = readArchiveId(input);
    }

    public JarInflater(byte[] bytes) throws JarFormatException {
        this(new ByteArrayInputStream(bytes == null ? new byte[0] : bytes));
    }

    public InputStream getInputStream(String name) throws JarFormatException {
        String path = resourcePath(name);
        if (isMld(name)) return new ByteArrayInputStream(soundToken(path));
        InputStream input = Resources.open(path);
        if (input == null) throw new JarFormatException("archive entry not found: " + name);
        return input;
    }

    public long getSize(String name) throws JarFormatException {
        InputStream input = Resources.open(resourcePath(name));
        if (input == null) throw new JarFormatException("archive entry not found: " + name);
        long size = 0;
        byte[] buffer = new byte[1024];
        try {
            int read;
            while ((read = input.read(buffer)) >= 0) if (read > 0) size += read;
            return size;
        } catch (IOException failure) {
            throw new JarFormatException(failure.toString());
        } finally {
            try { input.close(); } catch (IOException ignored) {}
        }
    }

    public void close() {}

    private static String readArchiveId(InputStream input) throws JarFormatException {
        if (input == null) throw new JarFormatException("null archive stream");
        StringBuffer token = new StringBuffer();
        try {
            for (int i = 0; i < 32; i++) {
                int value = input.read();
                if (value < 0 || value == '\n') break;
                token.append((char)value);
            }
        } catch (IOException failure) {
            throw new JarFormatException(failure.toString());
        }
        String text = token.toString();
        if (!text.startsWith("SPARC:") || text.length() <= 6) {
            throw new JarFormatException("unrecognized scratchpad archive");
        }
        return text.substring(6);
    }

    private String resourcePath(String name) throws JarFormatException {
        if (name == null || name.length() == 0 || name.startsWith("/")
                || name.indexOf("..") >= 0 || name.indexOf('\\') >= 0) {
            throw new JarFormatException("unsafe archive entry: " + name);
        }
        String resolved = resolveAssetName(name);
        return ASSET_PREFIX + archiveId + "/" + resolved;
    }

    private String resolveAssetName(String name) throws JarFormatException {
        if (!isMld(name)) return name;
        String stem = name.substring(0, name.length() - 4);
        String midi = stem + ".mid";
        if (resourceExists(midi)) return midi;
        String wav = stem + ".wav";
        if (resourceExists(wav)) return wav;
        throw new JarFormatException("converted sound not found: " + name);
    }

    private boolean resourceExists(String name) {
        InputStream input = Resources.open(ASSET_PREFIX + archiveId + "/" + name);
        if (input == null) return false;
        try { input.close(); } catch (IOException ignored) {}
        return true;
    }

    private static boolean isMld(String name) {
        return name.toLowerCase().endsWith(".mld");
    }

    private static byte[] soundToken(String path) {
        String token = "SND:" + path + "\n";
        try { return token.getBytes("ISO-8859-1"); }
        catch (Exception ignored) { return token.getBytes(); }
    }
}
