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

    static int durationMillis(String resourcePath) {
        if (resourcePath == null) return -1;
        InputStream raw = Resources.open(RESOURCE);
        if (raw == null) return -1;
        DataInputStream in = new DataInputStream(raw);
        try {
            if (in.readUnsignedByte() != 'S' || in.readUnsignedByte() != 'N'
                    || in.readUnsignedByte() != 'D' || in.readUnsignedByte() != 'I') return -1;
            int key = fnv1a(resourcePath);
            int count = in.readUnsignedShort();
            for (int i = 0; i < count; i++) {
                int hash = in.readInt();
                int duration = in.readInt();
                if (hash == key) return duration;
            }
        } catch (IOException ignored) {
        } finally {
            try { in.close(); } catch (IOException ignored) {}
        }
        return -1;
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
