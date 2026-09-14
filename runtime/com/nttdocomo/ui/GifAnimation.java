package com.nttdocomo.ui;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Vector;

/* 專門處理 drawNthImage() 所需的 GIF89a 合成運算，解碼完成後即可直接擷取指定的畫面幀。 */
final class GifAnimation {
    private final int width;
    private final int height;
    private final int background;
    private final Frame[] frames;
    private final int[] canvas;
    private int[] restore;
    private int current = -1;
    private Frame displayed;

    GifAnimation(byte[] data) throws IOException {
        Parser parser = new Parser(data);
        parser.parse();
        width = parser.width;
        height = parser.height;
        background = parser.background;
        frames = parser.frames;
        if (frames.length == 0) throw new IOException("GIF has no image frames");
        canvas = new int[width * height];
        reset();
    }

    int getFrameCount() { return frames.length; }

    Image getFrame(int index) {
        if (index < 0 || index >= frames.length) throw new IllegalArgumentException("GIF frame out of range");
        try {
            if (index <= current) reset();
            while (current < index) advance(frames[current + 1]);
            int[] snapshot = new int[canvas.length];
            System.arraycopy(canvas, 0, snapshot, 0, canvas.length);
            return Image.createImage(width, height, snapshot, 0);
        } catch (IOException e) {
            throw new IllegalArgumentException("bad animated GIF");
        }
    }

    private void reset() {
        for (int i = 0; i < canvas.length; i++) canvas[i] = background;
        current = -1;
        displayed = null;
        restore = null;
    }

    private void advance(Frame next) throws IOException {
        disposeDisplayed();
        if (next.disposal == 3) {
            if (restore == null || restore.length != canvas.length) restore = new int[canvas.length];
            System.arraycopy(canvas, 0, restore, 0, canvas.length);
        }
        draw(next);
        displayed = next;
        current++;
    }

    private void disposeDisplayed() {
        if (displayed == null) return;
        if (displayed.disposal == 2) {
            int x0 = displayed.left < 0 ? 0 : displayed.left;
            int y0 = displayed.top < 0 ? 0 : displayed.top;
            int x1 = displayed.left + displayed.width;
            int y1 = displayed.top + displayed.height;
            if (x1 > width) x1 = width;
            if (y1 > height) y1 = height;
            for (int y = y0; y < y1; y++) {
                int p = y * width + x0;
                for (int x = x0; x < x1; x++) canvas[p++] = background;
            }
        } else if (displayed.disposal == 3 && restore != null) {
            System.arraycopy(restore, 0, canvas, 0, canvas.length);
        }
    }

    private void draw(Frame frame) throws IOException {
        int[] pixels = PalettedImage.decodeLzw(frame.compressed, frame.lzwMinCodeSize,
                frame.width * frame.height);
        int[] rowOrder = frame.interlaced ? interlacedRows(frame.height) : null;
        for (int sourceRow = 0; sourceRow < frame.height; sourceRow++) {
            int localY = frame.interlaced ? rowOrder[sourceRow] : sourceRow;
            int targetY = frame.top + localY;
            if (targetY < 0 || targetY >= height) continue;
            int src = sourceRow * frame.width;
            for (int localX = 0; localX < frame.width; localX++) {
                int index = pixels[src + localX];
                if (index == frame.transparentIndex) continue;
                int targetX = frame.left + localX;
                if (targetX < 0 || targetX >= width) continue;
                if (frame.palette != null && index >= 0 && index < frame.palette.length) {
                    canvas[targetY * width + targetX] = frame.palette[index];
                }
            }
        }
    }

    private static int[] interlacedRows(int height) {
        int[] rows = new int[height];
        int n = 0;
        for (int y = 0; y < height; y += 8) rows[n++] = y;
        for (int y = 4; y < height; y += 8) rows[n++] = y;
        for (int y = 2; y < height; y += 4) rows[n++] = y;
        for (int y = 1; y < height; y += 2) rows[n++] = y;
        return rows;
    }

    private static final class Frame {
        int left, top, width, height;
        boolean interlaced;
        int[] palette;
        int transparentIndex = -1;
        int disposal;
        int lzwMinCodeSize;
        byte[] compressed;
    }

    private static final class Parser {
        private final byte[] data;
        private int pos;
        int width, height, background;
        int[] globalPalette;
        Frame[] frames;
        private final Vector list = new Vector();
        private int disposal;
        private int transparentIndex = -1;

        Parser(byte[] bytes) { data = bytes == null ? new byte[0] : bytes; }

        void parse() throws IOException {
            if (data.length < 13 || data[0] != 'G' || data[1] != 'I' || data[2] != 'F') {
                throw new IOException("not a GIF");
            }
            pos = 6;
            width = u16();
            height = u16();
            if (width <= 0 || height <= 0) throw new IOException("bad GIF dimensions");
            int packed = u8();
            int backgroundIndex = u8();
            u8(); 
            // 預設將像素顯示比例視為正方形，檔案內讀取的像素長寬比（Pixel Aspect Ratio）僅做讀取，不影響繪製。
            if ((packed & 0x80) != 0) globalPalette = colorTable(1 << ((packed & 7) + 1));
            background = globalPalette != null && backgroundIndex < globalPalette.length
                    ? globalPalette[backgroundIndex] : 0x00000000;
            while (pos < data.length) {
                int block = u8();
                if (block == 0x3B) break;
                if (block == 0x21) {
                    extension();
                } else if (block == 0x2C) {
                    image();
                } else {
                    throw new IOException("bad GIF block");
                }
            }
            frames = new Frame[list.size()];
            for (int i = 0; i < frames.length; i++) frames[i] = (Frame)list.elementAt(i);
        }

        private void extension() throws IOException {
            int label = u8();
            if (label == 0xF9) {
                int size = u8();
                if (size != 4) {
                    skip(size);
                    skipSubBlocks();
                    disposal = 0;
                    transparentIndex = -1;
                    return;
                }
                int packed = u8();
                disposal = (packed >> 2) & 7;
                u16(); // 呼叫端直接指定第幾幀，所以這裡用不到播放延遲。
                int index = u8();
                u8(); // Graphic Control Extension 的結束 byte。
                transparentIndex = (packed & 1) != 0 ? index : -1;
            } else {
                skipSubBlocks();
            }
        }

        private void image() throws IOException {
            Frame frame = new Frame();
            frame.left = u16();
            frame.top = u16();
            frame.width = u16();
            frame.height = u16();
            if (frame.width <= 0 || frame.height <= 0) throw new IOException("bad GIF frame size");
            int packed = u8();
            frame.interlaced = (packed & 0x40) != 0;
            frame.palette = (packed & 0x80) != 0
                    ? colorTable(1 << ((packed & 7) + 1)) : globalPalette;
            frame.disposal = disposal;
            frame.transparentIndex = transparentIndex;
            frame.lzwMinCodeSize = u8();
            frame.compressed = subBlocks();
            list.addElement(frame);
            disposal = 0;
            transparentIndex = -1;
        }

        private int[] colorTable(int count) throws IOException {
            int[] table = new int[count];
            for (int i = 0; i < count; i++) {
                int r = u8(), g = u8(), b = u8();
                table[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
            return table;
        }

        private byte[] subBlocks() throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (;;) {
                int count = u8();
                if (count == 0) return out.toByteArray();
                need(count);
                out.write(data, pos, count);
                pos += count;
            }
        }

        private void skipSubBlocks() throws IOException {
            for (;;) {
                int count = u8();
                if (count == 0) return;
                skip(count);
            }
        }

        private void skip(int count) throws IOException { need(count); pos += count; }
        private int u8() throws IOException { need(1); return data[pos++] & 255; }
        private int u16() throws IOException { int lo = u8(); return lo | (u8() << 8); }
        private void need(int count) throws IOException {
            if (count < 0 || pos + count > data.length) throw new IOException("truncated GIF");
        }
    }
}
