package doja;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Shift-JIS 解碼器。
 * 優先使用譯文，未翻譯的文字再由 Shift-JIS 轉成 Unicode。
 */
public final class Sjis {
    private static final String MAP_RESOURCE = "/font/sjis_map.bin";
    private static boolean mapAttempted;
    private static char[] sjisCodes;
    private static char[] sjisUnicode;

    private Sjis() {}

    public static String decode(byte[] bytes) {
        if (bytes == null) throw new NullPointerException("bytes");
        return decode(bytes, 0, bytes.length);
    }

    public static String decode(byte[] bytes, int offset, int length) {
        if (bytes == null) throw new NullPointerException("bytes");
        if (offset < 0 || length < 0 || offset > bytes.length || length > bytes.length - offset) {
            throw new IndexOutOfBoundsException();
        }
        String translated = TextTranslations.resolve(bytes, offset, length);
        if (translated != null) return translated;

        StringBuffer out = new StringBuffer(length);
        int end = offset + length;
        int p = offset;
        while (p < end) {
            int b = bytes[p] & 0xff;
            if (b <= 0x7f) {
                out.append((char)b);
                p++;
            } else if (b >= 0xa1 && b <= 0xdf) {
                out.append((char)(0xff61 + b - 0xa1));
                p++;
            } else if (isLead(b) && p + 1 < end) {
                int trail = bytes[p + 1] & 0xff;
                if (isTrail(trail)) {
                    int mapped = map((b << 8) | trail);
                    out.append(mapped != 0 ? (char)mapped : '\u53E3');
                    p += 2;
                } else {
                    out.append('\u53E3');
                    p++;
                }
            } else {
                out.append('\u53E3');
                p++;
            }
        }
        return out.toString();
    }

    private static boolean isLead(int value) {
        return (value >= 0x81 && value <= 0x9f) || (value >= 0xe0 && value <= 0xfc);
    }

    private static boolean isTrail(int value) {
        return value >= 0x40 && value <= 0xfc && value != 0x7f;
    }

    private static int map(int code) {
        ensureMapLoaded();
        if (sjisCodes == null) return 0;
        int low = 0;
        int high = sjisCodes.length - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int diff = (sjisCodes[mid] & 0xffff) - code;
            if (diff == 0) return sjisUnicode[mid] & 0xffff;
            if (diff < 0) low = mid + 1;
            else high = mid - 1;
        }
        return 0;
    }

    private static synchronized void ensureMapLoaded() {
        if (mapAttempted) return;
        mapAttempted = true;
        DataInputStream in = null;
        try {
            InputStream raw = Resources.open(MAP_RESOURCE);
            if (raw == null) return;
            in = new DataInputStream(raw);
            if (in.readUnsignedByte() != 'S' || in.readUnsignedByte() != 'M'
                    || in.readUnsignedByte() != 'P' || in.readUnsignedByte() != '1') return;
            int count = in.readUnsignedShort();
            if (count <= 0) return;
            sjisCodes = new char[count];
            sjisUnicode = new char[count];
            for (int i = 0; i < count; i++) {
                sjisCodes[i] = (char)in.readUnsignedShort();
                sjisUnicode[i] = (char)in.readUnsignedShort();
            }
        } catch (Throwable ignored) {
            sjisCodes = null;
            sjisUnicode = null;
        } finally {
            if (in != null) {
                try { in.close(); } catch (IOException ignored) {}
            }
        }
    }
}
