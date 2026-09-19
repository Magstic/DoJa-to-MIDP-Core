package doja.tools.font;

import doja.tools.io.FileIO;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

/** 將需要的字形製成單色位圖，供遊戲執行時使用。 */
final class BitmapFontWriter {
    private static final int HEIGHT = 12;
    private static final int ASCENT = 11;
    private static final int DESCENT = 1;
    private static final int MAX_WIDTH = 16;
    private static final int BYTES_PER_GLYPH = 24;
    private static final int RENDER_SIZE = 48;
    private static final int RENDER_X = 16;
    private static final int RENDER_BASELINE = 32;

    private BitmapFontWriter() {}

    static int write(Font font, Set<Integer> glyphs, File output) throws IOException {
        if (font == null) throw new NullPointerException("font");
        if (glyphs == null) throw new NullPointerException("glyphs");
        if (glyphs.size() > 65535) throw new IOException("bitmap font glyph count overflow: " + glyphs.size());

        ByteArrayOutputStream bytes = new ByteArrayOutputStream(9 + glyphs.size() * (3 + BYTES_PER_GLYPH));
        DataOutputStream out = new DataOutputStream(bytes);
        Rasterizer rasterizer = new Rasterizer(font);
        TreeSet<Integer> missing = new TreeSet<Integer>();
        try {
            out.writeBytes("BMF1");
            out.writeByte(HEIGHT);
            out.writeByte(ASCENT);
            out.writeByte(DESCENT);
            out.writeShort(glyphs.size());

            for (Iterator<Integer> it = glyphs.iterator(); it.hasNext();) {
                int logicalCodePoint = it.next().intValue();
                int renderCodePoint = GlyphResolver.resolve(font, logicalCodePoint);
                if (renderCodePoint < 0) {
                    throw new IOException("font cannot display " + GlyphResolver.codePoint(logicalCodePoint));
                }
                if (GlyphResolver.usedPlaceholder(font, logicalCodePoint, renderCodePoint)) {
                    missing.add(Integer.valueOf(logicalCodePoint));
                }
                int advance = rasterizer.render(logicalCodePoint, renderCodePoint);
                out.writeShort(logicalCodePoint);
                out.writeByte(advance);
                out.write(rasterizer.rows);
            }
        } finally {
            rasterizer.close();
            out.close();
        }

        FileIO.write(output, bytes.toByteArray());
        for (Iterator<Integer> it = missing.iterator(); it.hasNext();) {
            int logicalCodePoint = it.next().intValue();
            System.out.println("FontBuild: missing glyph " + describe(logicalCodePoint)
                    + "; using " + describe(GlyphResolver.PLACEHOLDER_CODE_POINT));
        }
        return glyphs.size();
    }

    private static String describe(int codePoint) {
        return GlyphResolver.codePoint(codePoint) + " '" + (char)codePoint + "'";
    }

    private static boolean isWide(int cp) {
        return (cp >= 0x3000 && cp <= 0x9FFF) || (cp >= 0xF900 && cp <= 0xFAFF)
                || (cp >= 0xFF00 && cp <= 0xFFEF);
    }

    private static final class Rasterizer {
        private final BufferedImage image = new BufferedImage(RENDER_SIZE, RENDER_SIZE, BufferedImage.TYPE_INT_RGB);
        private final int[] pixels = ((DataBufferInt)image.getRaster().getDataBuffer()).getData();
        private final Graphics2D graphics;
        private final FontMetrics metrics;
        private final boolean hasPlaceholder;
        private final char[] renderChar = new char[1];
        private final byte[] rows = new byte[BYTES_PER_GLYPH];

        Rasterizer(Font font) {
            graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            graphics.setFont(font);
            graphics.setColor(Color.WHITE);
            metrics = graphics.getFontMetrics();
            hasPlaceholder = font.canDisplay((char)GlyphResolver.PLACEHOLDER_CODE_POINT);
        }

        int render(int logicalCodePoint, int renderCodePoint) {
            Arrays.fill(pixels, 0);
            Arrays.fill(rows, (byte)0);
            // 字型沒有『口』時，直接畫出口形，讓缺字仍能顯示。
            if (renderCodePoint == GlyphResolver.PLACEHOLDER_CODE_POINT && !hasPlaceholder) {
                for (int y = 1; y < HEIGHT - 1; y++) {
                    int mask = y == 1 || y == HEIGHT - 2 ? 0x7FE0 : 0x4020;
                    rows[y * 2] = (byte)(mask >>> 8);
                    rows[y * 2 + 1] = (byte)mask;
                }
                return 12;
            }
            renderChar[0] = (char)renderCodePoint;
            graphics.drawChars(renderChar, 0, 1, RENDER_X, RENDER_BASELINE);

            int advance = metrics.charWidth((char)renderCodePoint);
            if (logicalCodePoint == ' ') advance = Math.max(3, advance);
            if (advance <= 0) advance = isWide(logicalCodePoint) ? 12 : 6;
            if (advance > MAX_WIDTH) advance = MAX_WIDTH;

            int minX = RENDER_SIZE;
            int minY = RENDER_SIZE;
            int maxX = -1;
            int maxY = -1;
            for (int y = 0, offset = 0; y < RENDER_SIZE; y++, offset += RENDER_SIZE) {
                for (int x = 0; x < RENDER_SIZE; x++) {
                    if ((pixels[offset + x] & 0xFFFFFF) == 0) continue;
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
            if (maxX < minX) return advance;

            int xShift = Math.max(0, RENDER_X - minX);
            advance = Math.min(MAX_WIDTH, Math.max(advance, maxX - RENDER_X + xShift + 1));

            int minOutY = minY - RENDER_BASELINE + ASCENT;
            int maxOutY = maxY - RENDER_BASELINE + ASCENT;
            int yShift = 0;
            if (minOutY < 0) yShift = -minOutY;
            if (maxOutY + yShift >= HEIGHT) yShift -= maxOutY + yShift - (HEIGHT - 1);
            if (minOutY + yShift < 0) yShift = -minOutY;

            for (int y = minY; y <= maxY; y++) {
                int outY = y - RENDER_BASELINE + ASCENT + yShift;
                if (outY < 0 || outY >= HEIGHT) continue;
                int pixelOffset = y * RENDER_SIZE;
                int rowOffset = outY * 2;
                int mask = 0;
                for (int x = minX; x <= maxX; x++) {
                    if ((pixels[pixelOffset + x] & 0xFFFFFF) == 0) continue;
                    int outX = x - RENDER_X + xShift;
                    if (outX >= 0 && outX < advance && outX < MAX_WIDTH) mask |= 1 << (15 - outX);
                }
                rows[rowOffset] = (byte)(mask >>> 8);
                rows[rowOffset + 1] = (byte)mask;
            }
            return advance;
        }

        void close() {
            graphics.dispose();
        }
    }

}
