package doja;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Optional exact raw Shift-JIS byte translations. Translated strings bypass SJIS decoding entirely. */
final class TextTranslations {
    private static final String RESOURCE = "/assets/translation.bin";

    private static boolean loadAttempted;
    private static byte[][] sources;
    private static String[] translations;

    private TextTranslations() {}

    static String resolve(byte[] source, int offset, int length) {
        if (source == null || length == 0) return null;
        ensureLoaded();
        if (sources == null) return null;

        int low = 0;
        int high = sources.length - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int diff = compare(sources[mid], source, offset, length);
            if (diff == 0) return translations[mid];
            if (diff < 0) low = mid + 1;
            else high = mid - 1;
        }
        return null;
    }

    private static int compare(byte[] a, byte[] b, int offset, int length) {
        int common = a.length < length ? a.length : length;
        for (int i = 0; i < common; i++) {
            int av = a[i] & 0xFF;
            int bv = b[offset + i] & 0xFF;
            if (av != bv) return av < bv ? -1 : 1;
        }
        if (a.length == length) return 0;
        return a.length < length ? -1 : 1;
    }

    private static synchronized void ensureLoaded() {
        if (loadAttempted) return;
        loadAttempted = true;

        DataInputStream in = null;
        try {
            InputStream raw = Resources.open(RESOURCE);
            if (raw == null) return;
            in = new DataInputStream(raw);
            int count = in.readUnsignedShort();
            if (count == 0) return;
            byte[][] loadedSources = new byte[count][];
            String[] loadedTranslations = new String[count];
            byte[] previous = null;
            for (int i = 0; i < count; i++) {
                int length = in.readUnsignedShort();
                if (length == 0) return;
                byte[] source = new byte[length];
                in.readFully(source);
                String translation = readString(in);
                if (translation.length() == 0 || (previous != null
                        && compare(previous, source, 0, source.length) >= 0)) {
                    return;
                }
                loadedSources[i] = source;
                loadedTranslations[i] = translation;
                previous = source;
            }
            sources = loadedSources;
            translations = loadedTranslations;
        } catch (Throwable ignored) {
            sources = null;
            translations = null;
        } finally {
            if (in != null) {
                try { in.close(); } catch (IOException ignored) {}
            }
        }
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort();
        char[] chars = new char[length];
        for (int i = 0; i < length; i++) chars[i] = in.readChar();
        return new String(chars);
    }
}
