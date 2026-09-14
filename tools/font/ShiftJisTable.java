package doja.tools.font;

import doja.tools.io.FileIO;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Builds only the double-byte Shift-JIS mappings actually required by the Runtime decoder. */
final class ShiftJisTable {
    private ShiftJisTable() {}

    static Map<Integer,Integer> build(Set<Integer> codes) throws IOException {
        TreeMap<Integer,Integer> map = new TreeMap<Integer,Integer>();
        Charset charset = findCharset();
        for (Iterator<Integer> it = codes.iterator(); it.hasNext();) {
            int code = it.next().intValue();
            Integer value = decode(charset, code);
            if (value == null) {
                throw new IOException("cannot decode required Shift-JIS code 0x"
                        + Integer.toHexString(code).toUpperCase());
            }
            map.put(Integer.valueOf(code), value);
        }
        return map;
    }

    static void write(Map<Integer,Integer> map, File output) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(6 + map.size() * 4);
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeBytes("SMP1");
        out.writeShort(map.size());
        for (Iterator<Map.Entry<Integer,Integer>> it = map.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Integer,Integer> entry = it.next();
            out.writeShort(entry.getKey().intValue());
            out.writeShort(entry.getValue().intValue());
        }
        out.close();
        FileIO.write(output, bytes.toByteArray());
    }

    private static Charset findCharset() {
        String[] names = { "windows-31j", "MS932", "Shift_JIS" };
        for (int i = 0; i < names.length; i++) {
            try { return Charset.forName(names[i]); }
            catch (Throwable ignored) {}
        }
        throw new IllegalStateException("No Shift-JIS compatible charset available in build JRE");
    }

    private static Integer decode(Charset charset, int code) {
        byte[] bytes = { (byte)(code >>> 8), (byte)code };
        String text;
        try { text = new String(bytes, charset); }
        catch (Throwable ignored) { return null; }
        if (text.length() != 1) return null;
        char value = text.charAt(0);
        return value == '\uFFFD' || value == 0 ? null : Integer.valueOf(value);
    }
}
