package doja.tools.scratchpad;

import doja.tools.io.FileIO;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 記下轉檔後的聲音長度，Runtime 的 logical clock 不必依賴 MMAPI 回報。 */
final class SoundIndex {
    static final class Entry {
        final String resourcePath;
        final int durationMillis;

        Entry(String resourcePath, int durationMillis) {
            if (resourcePath == null || resourcePath.length() == 0) throw new IllegalArgumentException("empty sound resource path");
            this.resourcePath = resourcePath;
            this.durationMillis = Math.max(1, durationMillis);
        }
    }

    private SoundIndex() {}

    static void write(File file, List<Entry> entries) throws IOException {
        Map<Integer, String> hashes = new HashMap<Integer, String>();
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            Integer hash = Integer.valueOf(hash(entry.resourcePath));
            String previous = hashes.put(hash, entry.resourcePath);
            if (previous != null && !previous.equals(entry.resourcePath)) {
                throw new IOException("sound index hash collision: " + previous + " / " + entry.resourcePath);
            }
        }

        FileIO.ensureParent(file);
        DataOutputStream out = new DataOutputStream(new FileOutputStream(file));
        try {
            out.writeBytes("SNDI");
            out.writeShort(entries.size());
            for (int i = 0; i < entries.size(); i++) {
                Entry entry = entries.get(i);
                out.writeInt(hash(entry.resourcePath));
                out.writeInt(entry.durationMillis);
            }
        } finally {
            out.close();
        }
        System.out.println("SoundIndex: " + entries.size() + " sound duration entries");
    }

    static int hash(String value) {
        int hash = 0x811c9dc5;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i) & 0xff;
            hash *= 0x01000193;
        }
        return hash;
    }
}
