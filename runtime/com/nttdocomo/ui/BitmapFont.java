package com.nttdocomo.ui;

import doja.Resources;
import doja.Sjis;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

final class BitmapFont {
    private static final String RESOURCE = "/font/glyphs.bin";
    private static boolean attempted;
    private static boolean loaded;
    private static int height;
    private static int ascent;
    private static int descent;
    private static char[] codes;
    private static byte[] advances;
    private static byte[] glyphData;
    private static final int BYTES_PER_GLYPH = 24; /* 12 列，每列為一個 uint16 bit mask。 */
    private static final int MAX_WIDTH = 16;
    private static final int[] scratch = new int[MAX_WIDTH * 16];
    private static byte[] asciiAdvancePlus;

    private BitmapFont() {}

    static boolean isLoaded() {
        ensureLoaded();
        return loaded;
    }

    static int getHeight() {
        ensureLoaded();
        return loaded ? height : 12;
    }

    static int getAscent() {
        ensureLoaded();
        return loaded ? ascent : 11;
    }

    static int getDescent() {
        ensureLoaded();
        return loaded ? descent : 1;
    }

    static boolean canDraw(String s) {
        int i;
        ensureLoaded();
        if (!loaded || s == null) return false;
        s = normalizeText(s);
        for (i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') continue;
            if (find(c) < 0) return false;
        }
        return true;
    }

    static int stringWidth(String s) {
        int i;
        int w = 0;
        ensureLoaded();
        if (!loaded || s == null || s.length() == 0) return 0;
        s = normalizeText(s);
        for (i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r') break;
            if (c == '\t') {
                w += 12;
            } else {
                w += charWidth(c);
            }
        }
        return w;
    }

    static int charWidth(char c) {
        int idx;
        ensureLoaded();
        if (!loaded) return 0;
        if (c < 256 && asciiAdvancePlus != null && asciiAdvancePlus[c] != 0) {
            return (asciiAdvancePlus[c] & 0xff) - 1;
        }
        idx = find(c);
        if (idx < 0) idx = find('?');
        if (idx < 0) return 6;
        return advances[idx] & 0xff;
    }

    static boolean drawString(Graphics g, String s, int x, int baseline, int argb, Font f) {
        int i;
        int cx;
        int top;
        int a;
        ensureLoaded();
        if (!loaded || g == null || s == null) return false;
        s = normalizeText(s);
        cx = x;
        a = f == null ? ascent : f.getAscent();
        top = baseline - a;
        for (i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r') break;
            if (c == '\t') {
                cx += 12;
            } else {
                cx += drawChar(g, c, cx, top, argb);
            }
        }
        return true;
    }


    static boolean drawRaw(javax.microedition.lcdui.Graphics mg, String s, int x, int baseline, int argb) {
        int i;
        int cx;
        int top;
        ensureLoaded();
        if (!loaded || mg == null || s == null) return false;
        s = normalizeText(s);
        cx = x;
        top = baseline - ascent;
        mg.setColor(argb & 0x00FFFFFF);
        for (i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r') break;
            if (c == '\t') {
                cx += 12;
            } else {
                cx += drawCharRaw(mg, c, cx, top);
            }
        }
        return true;
    }

    private static int drawCharRaw(javax.microedition.lcdui.Graphics mg, char c, int x, int y) {
        int idx = find(c);
        int width;
        int row;
        int col;
        int mask;
        int glyphOff;
        if (idx < 0) idx = find('?');
        if (idx < 0) return 6;
        width = advances[idx] & 0xff;
        if (width <= 0) return 0;
        if (width > MAX_WIDTH) width = MAX_WIDTH;
        glyphOff = idx * BYTES_PER_GLYPH;
        for (row = 0; row < height; row++) {
            mask = ((glyphData[glyphOff + row * 2] & 0xff) << 8) | (glyphData[glyphOff + row * 2 + 1] & 0xff);
            col = 0;
            while (col < width) {
                while (col < width && (mask & (1 << (15 - col))) == 0) col++;
                if (col < width) {
                    int run = col;
                    while (col < width && (mask & (1 << (15 - col))) != 0) col++;
                    mg.drawLine(x + run, y + row, x + col - 1, y + row);
                }
            }
        }
        return width;
    }

    /** 直接把 glyph mask 填進 ARGB buffer，建立文字圖片時，就無須逐字呼叫 LCDUI。 */
    static void drawIntoArgb(int[] dst, int stride, int dstHeight,
            String s, int x, int baseline, int argb) {
        int i;
        int cx;
        int top;
        ensureLoaded();
        if (!loaded || dst == null || stride <= 0 || dstHeight <= 0 || s == null) return;
        s = normalizeText(s);
        cx = x;
        top = baseline - ascent;
        for (i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r') break;
            if (c == '\t') {
                cx += 12;
                continue;
            }
            int idx = find(c);
            if (idx < 0) idx = find('?');
            if (idx < 0) { cx += 6; continue; }
            int width = advances[idx] & 0xff;
            if (width > MAX_WIDTH) width = MAX_WIDTH;
            int glyphOff = idx * BYTES_PER_GLYPH;
            for (int row = 0; row < height; row++) {
                int yy = top + row;
                if (yy < 0 || yy >= dstHeight) continue;
                int mask = ((glyphData[glyphOff + row * 2] & 0xff) << 8)
                    | (glyphData[glyphOff + row * 2 + 1] & 0xff);
                for (int col = 0; col < width; col++) {
                    int xx = cx + col;
                    if (xx >= 0 && xx < stride && (mask & (1 << (15 - col))) != 0) {
                        dst[yy * stride + xx] = argb;
                    }
                }
            }
            cx += advances[idx] & 0xff;
        }
    }

    /* 有些遊戲把 Shift-JIS byte 直接塞進 U+0000..U+00FF。
     * 這會在量字和畫字前統一解碼，正常的 Unicode 字串則不受影響。 */
    static String normalizeText(String s) {
        return Sjis.decodePreserved(s);
    }

    private static int drawChar(Graphics g, char c, int x, int y, int argb) {
        int idx = find(c);
        int width;
        int row;
        int col;
        int mask;
        int glyphOff;
        if (idx < 0) idx = find('?');
        if (idx < 0) return 6;
        width = advances[idx] & 0xff;
        if (width <= 0) return 0;
        if (width > MAX_WIDTH) width = MAX_WIDTH;
        glyphOff = idx * BYTES_PER_GLYPH;
        if (((argb >>> 24) & 0xFF) >= 255 && g.isNativeRenderFastPath()) {
            javax.microedition.lcdui.Graphics mg = g.getMIDPGraphics();
            mg.setColor(argb & 0x00FFFFFF);
            for (row = 0; row < height; row++) {
                mask = ((glyphData[glyphOff + row * 2] & 0xff) << 8) | (glyphData[glyphOff + row * 2 + 1] & 0xff);
                col = 0;
                while (col < width) {
                    while (col < width && (mask & (1 << (15 - col))) == 0) col++;
                    if (col < width) {
                        int run = col;
                        while (col < width && (mask & (1 << (15 - col))) != 0) col++;
                        mg.drawLine(x + run, y + row, x + col - 1, y + row);
                    }
                }
            }
            return width;
        }
        int pos = 0;
        for (row = 0; row < height; row++) {
            mask = ((glyphData[glyphOff + row * 2] & 0xff) << 8) | (glyphData[glyphOff + row * 2 + 1] & 0xff);
            for (col = 0; col < width; col++) {
                scratch[pos++] = ((mask & (1 << (15 - col))) != 0) ? argb : 0x00000000;
            }
        }
        g.drawRGBComposite(scratch, 0, width, x, y, width, height, true);
        return width;
    }

    private static int find(char c) {
        int lo = 0;
        int hi;
        int mid;
        int v;
        if (codes == null) return -1;
        hi = codes.length - 1;
        while (lo <= hi) {
            mid = (lo + hi) >>> 1;
            v = codes[mid] - c;
            if (v == 0) return mid;
            if (v < 0) lo = mid + 1;
            else hi = mid - 1;
        }
        return -1;
    }

    private static synchronized void ensureLoaded() {
        DataInputStream in = null;
        int count;
        int i;
        if (attempted) return;
        attempted = true;
        try {
            InputStream raw = Resources.open(RESOURCE);
            if (raw == null) return;
            in = new DataInputStream(raw);
            if (in.readUnsignedByte() != 'B' || in.readUnsignedByte() != 'M'
                    || in.readUnsignedByte() != 'F' || in.readUnsignedByte() != '1') return;
            height = in.readUnsignedByte();
            ascent = in.readUnsignedByte();
            descent = in.readUnsignedByte();
            count = in.readUnsignedShort();
            if (height <= 0 || height > 16 || count <= 0) return;
            codes = new char[count];
            advances = new byte[count];
            glyphData = new byte[count * BYTES_PER_GLYPH];
            for (i = 0; i < count; i++) {
                codes[i] = (char)in.readUnsignedShort();
                advances[i] = in.readByte();
                in.readFully(glyphData, i * BYTES_PER_GLYPH, BYTES_PER_GLYPH);
            }
            loaded = true;
            buildFastWidths();
        } catch (Throwable ignored) {
            loaded = false;
            codes = null;
            advances = null;
            glyphData = null;
        } finally {
            if (in != null) {
                try { in.close(); } catch (IOException ignored) {}
            }
        }
    }


    private static void buildFastWidths() {
        int i;
        asciiAdvancePlus = new byte[256];
        for (i = 0; i < codes.length; i++) {
            int c = codes[i] & 0xffff;
            if (c < 256) {
                int w = advances[i] & 0xff;
                if (w < 255) asciiAdvancePlus[c] = (byte)(w + 1);
            }
        }
    }


}
