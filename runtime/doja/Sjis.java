package doja;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/** 
 * Shift-JIS 解碼器。
 * Raw translation lookup, Shift-JIS decoding, and bitmap glyph rendering are independent stages.
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
                int mapped = isTrail(trail) ? map((b << 8) | trail) : 0;
                if (mapped != 0) {
                    out.append((char)mapped);
                    p += 2;
                } else {
                    out.append('?');
                    p++;
                }
            } else {
                out.append('?');
                p++;
            }
        }
        return out.toString();
    }

    /** 還原在 U+0000..U+00FF 裡的 Shift-JIS byte，一般 Unicode 內容會原樣保留。 */
    public static String decodePreserved(String value) {
        if (value == null || value.length() == 0) return value;
        boolean hasPair = false;
        int i;
        for (i = 0; i < value.length(); i++) {
            int c = value.charAt(i);
            if (c > 0xff) return value;
            if (isLead(c) && i + 1 < value.length() && isTrail(value.charAt(i + 1))) {
                hasPair = true;
            }
        }
        if (!hasPair) return value;
        byte[] bytes = new byte[value.length()];
        for (i = 0; i < bytes.length; i++) bytes[i] = (byte)value.charAt(i);
        return decode(bytes);
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
