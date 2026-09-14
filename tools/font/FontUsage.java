package doja.tools.font;

import doja.tools.io.FileIO;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

/**
 * Build-time text usage manifest.
 * Render code points and Shift-JIS decode codes are intentionally separate:
 * translated raw strings need render glyphs for the translation, while only
 * untranslated raw strings need Shift-JIS decoder entries.
 */
public final class FontUsage {
    private static final int MAGIC = 0x46555331; // FUS1

    private final TreeSet<Integer> renderCodePoints = new TreeSet<Integer>();
    private final TreeSet<Integer> shiftJisCodes = new TreeSet<Integer>();

    public FontUsage() {}

    public void addRenderText(String text) throws IOException {
        if (text == null) throw new NullPointerException("text");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c) || Character.isLowSurrogate(c)) {
                throw new IOException("bitmap font supports BMP text only: U+" + Integer.toHexString(c).toUpperCase());
            }
            if (c >= 0x20 && c != 0x7F) renderCodePoints.add(Integer.valueOf(c));
        }
    }

    /** Adds only the double-byte codes that the Runtime decoder must resolve. */
    public void addShiftJisBytes(byte[] data) throws IOException {
        if (data == null) throw new NullPointerException("data");
        addShiftJisBytes(data, 0, data.length);
    }

    public void addShiftJisBytes(byte[] data, int offset, int length) throws IOException {
        if (data == null) throw new NullPointerException("data");
        if (offset < 0 || length < 0 || offset > data.length || length > data.length - offset) {
            throw new IndexOutOfBoundsException();
        }
        int end = offset + length;
        int p = offset;
        while (p < end) {
            int b = data[p] & 0xFF;
            if (b <= 0x7F || (b >= 0xA1 && b <= 0xDF)) {
                p++;
                continue;
            }
            if (!isLead(b) || p + 1 >= end) {
                throw new IOException("invalid Shift-JIS byte 0x" + hexByte(b) + " at +" + (p - offset));
            }
            int trail = data[p + 1] & 0xFF;
            if (!isTrail(trail)) {
                throw new IOException("invalid Shift-JIS trail 0x" + hexByte(trail) + " at +" + (p + 1 - offset));
            }
            shiftJisCodes.add(Integer.valueOf((b << 8) | trail));
            p += 2;
        }
    }

    public void merge(FontUsage other) {
        if (other == null) throw new NullPointerException("other");
        renderCodePoints.addAll(other.renderCodePoints);
        shiftJisCodes.addAll(other.shiftJisCodes);
    }

    public Set<Integer> renderCodePoints() {
        return Collections.unmodifiableSet(renderCodePoints);
    }

    public Set<Integer> shiftJisCodes() {
        return Collections.unmodifiableSet(shiftJisCodes);
    }

    public void write(File file) throws IOException {
        if (renderCodePoints.size() > 65535 || shiftJisCodes.size() > 65535) {
            throw new IOException("font usage manifest overflow");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(8
                + renderCodePoints.size() * 2 + shiftJisCodes.size() * 2);
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(MAGIC);
        out.writeShort(renderCodePoints.size());
        for (Iterator<Integer> it = renderCodePoints.iterator(); it.hasNext();) out.writeShort(it.next().intValue());
        out.writeShort(shiftJisCodes.size());
        for (Iterator<Integer> it = shiftJisCodes.iterator(); it.hasNext();) out.writeShort(it.next().intValue());
        out.close();
        FileIO.write(file, bytes.toByteArray());
    }

    public static FontUsage read(File file) throws IOException {
        byte[] data = FileIO.read(file);
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        FontUsage usage = new FontUsage();
        if (in.readInt() != MAGIC) throw new IOException(file + ": invalid font usage magic");
        int renderCount = in.readUnsignedShort();
        int previous = -1;
        for (int i = 0; i < renderCount; i++) {
            int value = in.readUnsignedShort();
            if (value <= previous) throw new IOException(file + ": render code points are not strictly sorted");
            usage.renderCodePoints.add(Integer.valueOf(value));
            previous = value;
        }
        int decodeCount = in.readUnsignedShort();
        previous = -1;
        for (int i = 0; i < decodeCount; i++) {
            int value = in.readUnsignedShort();
            if (value <= previous || !isLead(value >>> 8) || !isTrail(value & 0xFF)) {
                throw new IOException(file + ": invalid Shift-JIS code 0x" + Integer.toHexString(value).toUpperCase());
            }
            usage.shiftJisCodes.add(Integer.valueOf(value));
            previous = value;
        }
        if (in.read() != -1) throw new IOException(file + ": trailing font usage data");
        return usage;
    }

    private static boolean isLead(int value) {
        return (value >= 0x81 && value <= 0x9F) || (value >= 0xE0 && value <= 0xFC);
    }

    private static boolean isTrail(int value) {
        return value >= 0x40 && value <= 0xFC && value != 0x7F;
    }

    private static String hexByte(int value) {
        String text = Integer.toHexString(value & 0xFF).toUpperCase();
        return text.length() == 1 ? "0" + text : text;
    }
}
