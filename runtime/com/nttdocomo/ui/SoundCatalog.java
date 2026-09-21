package com.nttdocomo.ui;

import doja.Resources;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/** 
 * 建置時，聲音長度就已算好，因此它能夠直接拿來用。
 * 不必讓 logical clock 等 MMAPI，才知道何時播完。 
*/
final class SoundCatalog {
    private static final String RESOURCE = "/assets/sound-index.bin";
    private SoundCatalog() {}

    static SoundData find(String resourcePath) {
        if (resourcePath == null) return single(resourcePath, -1);
        InputStream raw = Resources.open(RESOURCE);
        if (raw == null) return single(resourcePath, -1);
        DataInputStream in = new DataInputStream(raw);
        try {
            if (in.readUnsignedByte() != 'S' || in.readUnsignedByte() != 'N'
                    || in.readUnsignedByte() != 'D' || in.readUnsignedByte() != '2') {
                return single(resourcePath, -1);
            }
            int key = fnv1a(resourcePath);
            int count = in.readUnsignedShort();
            for (int i = 0; i < count; i++) {
                int hash = in.readInt();
                int segmentCount = in.readUnsignedByte();
                int loopSegmentIndex = in.readUnsignedByte() - 1;
                String[] paths = new String[segmentCount];
                int[] durations = new int[segmentCount];
                for (int segment = 0; segment < segmentCount; segment++) {
                    paths[segment] = in.readUTF();
                    durations[segment] = in.readInt();
                }
                if (hash == key) return new SoundData(paths, durations, loopSegmentIndex);
            }
        } catch (IOException ignored) {
        } finally {
            try { in.close(); } catch (IOException ignored) {}
        }
        return single(resourcePath, -1);
    }

    private static SoundData single(String resourcePath, int durationMillis) {
        return new SoundData(new String[] {resourcePath}, new int[] {durationMillis}, -1);
    }

    private static int fnv1a(String value) {
        int result = 0x811c9dc5;
        for (int i = 0; i < value.length(); i++) {
            result ^= value.charAt(i) & 0xff;
            result *= 0x01000193;
        }
        return result;
    }
}
