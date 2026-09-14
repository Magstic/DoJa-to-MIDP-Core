package doja.tools.font;

import doja.tools.io.FileIO;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.TreeSet;

/** 把字型烘成 Runtime 使用的 1-bpp glyph；檔案小，老裝置畫字也只需讀 bit mask。 */
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

    static void write(Font font, TreeSet<Integer> glyphs, File output) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(body);
        int count = 0;
        for (Iterator<Integer> it = glyphs.iterator(); it.hasNext();) {
            int cp = it.next().intValue();
            GlyphBitmap glyph = render(font, cp);
            if (glyph.advance <= 0 && cp != ' ') continue;
            data.writeShort(cp);
            data.writeByte(glyph.advance);
            data.write(glyph.rows);
            count++;
        }
        data.close();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream(9 + body.size());
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeBytes("BMF1");
        out.writeByte(HEIGHT);
        out.writeByte(ASCENT);
        out.writeByte(DESCENT);
        out.writeShort(count);
        body.writeTo(out);
        out.close();
        FileIO.write(output, bytes.toByteArray());
    }

    private static GlyphBitmap render(Font font, int cp) {
        BufferedImage image = new BufferedImage(RENDER_SIZE, RENDER_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        byte[] rows = new byte[BYTES_PER_GLYPH];
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            graphics.setFont(font);
            FontMetrics metrics = graphics.getFontMetrics();

            int renderCp = cp;
            if (cp == 0x2715 && !font.canDisplay((char)cp)) renderCp = font.canDisplay('×') ? '×' : 'X';
            int advance = metrics.charWidth((char)renderCp);
            if (cp == ' ') advance = Math.max(3, advance);
            if (advance <= 0) advance = isWide(cp) ? 12 : 6;
            if (advance > MAX_WIDTH) advance = MAX_WIDTH;

            graphics.setColor(Color.BLACK);
            graphics.fillRect(0, 0, RENDER_SIZE, RENDER_SIZE);
            graphics.setColor(Color.WHITE);
            graphics.drawString(String.valueOf((char)renderCp), RENDER_X, RENDER_BASELINE);

            int minX = RENDER_SIZE, minY = RENDER_SIZE, maxX = -1, maxY = -1;
            for (int y = 0; y < RENDER_SIZE; y++) {
                for (int x = 0; x < RENDER_SIZE; x++) {
                    if ((image.getRGB(x, y) & 0xFFFFFF) != 0) {
                        if (x < minX) minX = x;
                        if (x > maxX) maxX = x;
                        if (y < minY) minY = y;
                        if (y > maxY) maxY = y;
                    }
                }
            }
            if (maxX < minX || maxY < minY) return new GlyphBitmap(advance, rows);

            int minOutY = minY - RENDER_BASELINE + ASCENT;
            int maxOutY = maxY - RENDER_BASELINE + ASCENT;
            int yShift = 0;
            if (minOutY < 0) yShift = -minOutY;
            if (maxOutY + yShift >= HEIGHT) yShift -= maxOutY + yShift - (HEIGHT - 1);
            if (minOutY + yShift < 0) yShift = -minOutY;

            for (int y = minY; y <= maxY; y++) {
                int outY = y - RENDER_BASELINE + ASCENT + yShift;
                if (outY < 0 || outY >= HEIGHT) continue;
                for (int x = minX; x <= maxX; x++) {
                    if ((image.getRGB(x, y) & 0xFFFFFF) == 0) continue;
                    int outX = x - RENDER_X;
                    if (outX < 0) outX += RENDER_X - minX;
                    if (outX >= 0 && outX < advance && outX < MAX_WIDTH) {
                        int offset = outY * 2;
                        int mask = ((rows[offset] & 255) << 8) | (rows[offset + 1] & 255);
                        mask |= 1 << (15 - outX);
                        rows[offset] = (byte)(mask >>> 8);
                        rows[offset + 1] = (byte)mask;
                    }
                }
            }
            return new GlyphBitmap(advance, rows);
        } finally {
            graphics.dispose();
        }
    }

    private static boolean isWide(int cp) {
        return (cp >= 0x3000 && cp <= 0x9FFF) || (cp >= 0xF900 && cp <= 0xFAFF)
                || (cp >= 0xFF00 && cp <= 0xFFEF);
    }

    private static final class GlyphBitmap {
        final int advance;
        final byte[] rows;
        GlyphBitmap(int advance, byte[] rows) { this.advance = advance; this.rows = rows; }
    }
}
