package com.nttdocomo.ui;

import java.io.IOException;

/**
 * 將背景與文字內容快取為單一影像，當文字內容未變更時，每幀僅需執行幾次 drawImage() ；
 * 這可避免頻繁進行 1-pixel 切片拼接與調用 drawLine() 重繪文字筆畫，從而節約 CPU 開銷。
 */
final class SoftKeys {
    private static final String RESOURCE = "/softkey_bg.png";
    private static javax.microedition.lcdui.Image bg;
    private static int[] bgPixels;
    private static boolean attempted;
    private static final int DEFAULT_W = 44;
    private static final int DEFAULT_H = 20;
    private static final int PAD_X = 5;

    private static String leftText;
    private static String rightText;
    private static javax.microedition.lcdui.Image leftImage;
    private static javax.microedition.lcdui.Image rightImage;

    private SoftKeys() {}

    static void paint(javax.microedition.lcdui.Graphics g, Frame frame, int screenW, int screenH) {
        if (g == null || frame == null || !frame.isSoftLabelVisible()) return;
        String left = frame.getSoftLabel(Frame.SOFT_KEY_1);
        String right = frame.getSoftLabel(Frame.SOFT_KEY_2);
        boolean hasLeft = left != null && left.length() > 0;
        boolean hasRight = right != null && right.length() > 0;
        if (!hasLeft && !hasRight) return;
        ensureBg();

        if (hasLeft) {
            if (!same(left, leftText) || leftImage == null) {
                leftText = left;
                leftImage = buildLabel(left);
            }
            drawCached(g, leftImage, 0, screenH, false);
        } else {
            leftText = null;
            leftImage = null;
        }
        if (hasRight) {
            if (!same(right, rightText) || rightImage == null) {
                rightText = right;
                rightImage = buildLabel(right);
            }
            drawCached(g, rightImage, screenW, screenH, true);
        } else {
            rightText = null;
            rightImage = null;
        }
    }

    private static boolean same(String a, String b) {
        return a == b || (a != null && a.equals(b));
    }

    private static void drawCached(javax.microedition.lcdui.Graphics g,
            javax.microedition.lcdui.Image image, int edgeX, int screenH, boolean rightAligned) {
        if (image == null) return;
        int x = rightAligned ? edgeX - image.getWidth() : edgeX;
        int y = screenH - image.getHeight();
        if (y < 0) y = 0;
        g.drawImage(image, x, y,
            javax.microedition.lcdui.Graphics.TOP | javax.microedition.lcdui.Graphics.LEFT);
    }

    private static javax.microedition.lcdui.Image buildLabel(String text) {
        int bgW = bg == null ? DEFAULT_W : bg.getWidth();
        int bgH = bg == null ? DEFAULT_H : bg.getHeight();
        int textW = BitmapFont.stringWidth(text);
        int boxW = textW + PAD_X * 2;
        if (boxW < bgW) boxW = bgW;
        int[] pixels = new int[boxW * bgH];

        if (bg != null && bgPixels != null) {
            stretchBackground(pixels, boxW, bgH, bgW);
        } else {
            buildFallbackBackground(pixels, boxW, bgH);
        }
        int tx = (boxW - textW) >> 1;
        int baseline = ((bgH - BitmapFont.getHeight()) >> 1) + BitmapFont.getAscent();
        BitmapFont.drawIntoArgb(pixels, boxW, bgH, text, tx, baseline, 0xffffffff);
        return javax.microedition.lcdui.Image.createRGBImage(pixels, boxW, bgH, true);
    }

    private static void stretchBackground(int[] out, int w, int h, int bw) {
        if (w == bw) {
            System.arraycopy(bgPixels, 0, out, 0, w * h);
            return;
        }
        int cap = bw >> 1;
        if (cap < 1) cap = 1;
        if (cap > w >> 1) cap = w >> 1;
        int centre = bw >> 1;
        for (int y = 0; y < h; y++) {
            int src = y * bw;
            int dst = y * w;
            System.arraycopy(bgPixels, src, out, dst, cap);
            int middleEnd = w - cap;
            for (int x = cap; x < middleEnd; x++) out[dst + x] = bgPixels[src + centre];
            System.arraycopy(bgPixels, src + bw - cap, out, dst + w - cap, cap);
        }
    }

    private static void buildFallbackBackground(int[] out, int w, int h) {
        int blue = 0xff173a8b;
        int white = 0xffffffff;
        for (int i = 0; i < out.length; i++) out[i] = blue;
        if (w < 2 || h < 2) return;
        for (int x = 0; x < w; x++) {
            out[x] = white;
            out[(h - 1) * w + x] = white;
        }
        for (int y = 0; y < h; y++) {
            out[y * w] = white;
            out[y * w + w - 1] = white;
        }
    }

    private static void ensureBg() {
        if (attempted) return;
        attempted = true;
        try {
            bg = javax.microedition.lcdui.Image.createImage(RESOURCE);
            int w = bg.getWidth();
            int h = bg.getHeight();
            bgPixels = new int[w * h];
            bg.getRGB(bgPixels, 0, w, 0, 0, w, h);
        } catch (IOException e) {
            bg = null;
            bgPixels = null;
        } catch (Throwable t) {
            bg = null;
            bgPixels = null;
        }
    }
}
