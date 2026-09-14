package doja.tools.translation;

import doja.tools.io.FileIO;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Build-side codec for the Runtime exact raw-byte translation resource. */
public final class RawTranslationResource {
    public static final class Entry {
        public final byte[] source;
        public final String translation;

        public Entry(byte[] source, String translation) {
            if (source == null) throw new NullPointerException("source");
            if (translation == null) throw new NullPointerException("translation");
            this.source = new byte[source.length];
            System.arraycopy(source, 0, this.source, 0, source.length);
            this.translation = translation;
        }
    }

    private RawTranslationResource() {}

    public static int write(File file, List<Entry> entries) throws IOException {
        List<Entry> normalized = normalize(entries);
        if (normalized.isEmpty()) {
            if (file.exists() && !file.delete()) throw new IOException("cannot delete stale " + file);
            return 0;
        }
        FileIO.ensureParent(file);
        DataOutputStream out = new DataOutputStream(new FileOutputStream(file));
        try {
            out.writeShort(normalized.size());
            for (int i = 0; i < normalized.size(); i++) {
                Entry entry = normalized.get(i);
                out.writeShort(entry.source.length);
                out.write(entry.source);
                writeString(out, entry.translation);
            }
        } finally {
            out.close();
        }
        return normalized.size();
    }

    public static Map<String,String> expected(List<Entry> entries) throws IOException {
        List<Entry> normalized = normalize(entries);
        LinkedHashMap<String,String> result = new LinkedHashMap<String,String>();
        for (int i = 0; i < normalized.size(); i++) {
            Entry entry = normalized.get(i);
            result.put(hex(entry.source), entry.translation);
        }
        return result;
    }

    public static Map<String,String> read(InputStream raw) throws IOException {
        DataInputStream in = new DataInputStream(raw);
        try {
            int count = in.readUnsignedShort();
            LinkedHashMap<String,String> result = new LinkedHashMap<String,String>();
            byte[] previous = null;
            for (int i = 0; i < count; i++) {
                int length = in.readUnsignedShort();
                if (length == 0) throw new IOException("empty translation source bytes");
                byte[] source = new byte[length];
                in.readFully(source);
                String translation = readString(in);
                if (translation.length() == 0 || (previous != null && compare(previous, source) >= 0)) {
                    throw new IOException("translation resource is not strictly sorted");
                }
                result.put(hex(source), translation);
                previous = source;
            }
            if (in.read() != -1) throw new IOException("translation resource has trailing bytes");
            return result;
        } catch (EOFException ex) {
            throw new IOException("truncated translation resource");
        }
    }

    private static List<Entry> normalize(List<Entry> entries) throws IOException {
        ArrayList<Entry> sorted = new ArrayList<Entry>();
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            validate(entry);
            sorted.add(new Entry(entry.source, entry.translation));
        }
        Collections.sort(sorted, new Comparator<Entry>() {
            public int compare(Entry a, Entry b) { return RawTranslationResource.compare(a.source, b.source); }
        });
        ArrayList<Entry> unique = new ArrayList<Entry>();
        for (int i = 0; i < sorted.size(); i++) {
            Entry entry = sorted.get(i);
            if (!unique.isEmpty() && compare(unique.get(unique.size() - 1).source, entry.source) == 0) {
                Entry previous = unique.get(unique.size() - 1);
                if (!previous.translation.equals(entry.translation)) {
                    throw new IOException("conflicting translations for raw key " + hex(entry.source));
                }
                continue;
            }
            unique.add(entry);
        }
        if (unique.size() > 65535) throw new IOException("too many Runtime translations");
        return unique;
    }

    private static void validate(Entry entry) throws IOException {
        if (entry.source.length == 0 || entry.source.length > 65535) {
            throw new IOException("invalid raw translation source length " + entry.source.length);
        }
        if (entry.translation.length() == 0 || entry.translation.length() > 65535) {
            throw new IOException("invalid Runtime translation length " + entry.translation.length());
        }
        for (int i = 0; i < entry.translation.length(); i++) {
            char c = entry.translation.charAt(i);
            if (c == 0 || Character.isSurrogate(c)) {
                throw new IOException("Runtime translation contains unsupported U+"
                        + Integer.toHexString(c).toUpperCase());
            }
        }
    }

    private static int compare(byte[] a, byte[] b) {
        int common = Math.min(a.length, b.length);
        for (int i = 0; i < common; i++) {
            int av = a[i] & 255;
            int bv = b[i] & 255;
            if (av != bv) return av < bv ? -1 : 1;
        }
        if (a.length == b.length) return 0;
        return a.length < b.length ? -1 : 1;
    }

    private static void writeString(DataOutputStream out, String text) throws IOException {
        out.writeShort(text.length());
        for (int i = 0; i < text.length(); i++) out.writeChar(text.charAt(i));
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort();
        char[] chars = new char[length];
        for (int i = 0; i < length; i++) chars[i] = in.readChar();
        return new String(chars);
    }

    private static String hex(byte[] data) {
        StringBuffer out = new StringBuffer(data.length * 2);
        for (int i = 0; i < data.length; i++) {
            int value = data[i] & 255;
            if (value < 16) out.append('0');
            out.append(Integer.toHexString(value));
        }
        return out.toString();
    }
}
