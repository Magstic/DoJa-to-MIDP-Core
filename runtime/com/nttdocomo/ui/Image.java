package com.nttdocomo.ui;

import doja.Graphics2Impl;
import doja.ImageResource;
import doja.ImageRuntime;

public class Image {
    private javax.microedition.lcdui.Image midpImage;
    private javax.microedition.lcdui.Image originalImage;
    private ImageResource resource;
    private int alpha = 255;
    private Graphics graphics;
    private int transparentColor;
    private boolean transparentEnabled;

    /*
    * DoJa 5.x 支援 0~255 階的全圖半透明，但 MIDP 裝置僅支援『完全透明/不透明』。
    * 因此針對不可變 (Immutable) 圖片，我們會在用到時延遲建立 (Lazy) 一份原生可畫的半透明副本並快取。
    * 如此一來，最頻繁執行的繪圖主迴圈就能直接呼叫 LCDUI 原生的 drawImage/drawRegion，
    * 這解決了回讀 Framebuffer、每格建立 ARGB 緩衝區，自實作軟體混合的不必要開銷。
    *
    * 系統『只會快取一種』非不透明的透明度數值，因為實際的 DoJa 遊戲通常
    * 只是在切換特效時套用某個固定值 (RO 用 128)，做完特效再切回 255（例如《仙境傳說：紫羅蘭》）。
    * 這樣既能降低 RAM 開銷，又能確保切回 255 後，算好的半透明副本能繼續重用。
    */
    private javax.microedition.lcdui.Image alphaRenderImage;
    private javax.microedition.lcdui.Image alphaRenderSource;
    private int alphaRenderValue = -1;
    private javax.microedition.lcdui.Image alphaRenderHistorySource;
    private int alphaRenderHistoryValue = -1;
    private boolean alphaRenderAlphaChanged;

    private static final int[] BAYER_8X8 = {
         0, 48, 12, 60,  3, 51, 15, 63,
        32, 16, 44, 28, 35, 19, 47, 31,
         8, 56,  4, 52, 11, 59,  7, 55,
        40, 24, 36, 20, 43, 27, 39, 23,
         2, 50, 14, 62,  1, 49, 13, 61,
        34, 18, 46, 30, 33, 17, 45, 29,
        10, 58,  6, 54,  9, 57,  5, 53,
        42, 26, 38, 22, 41, 25, 37, 21
    };

    public Image(javax.microedition.lcdui.Image img) {
        midpImage = img;
        originalImage = img;
    }

    private Image(ImageResource value) { resource = value; }

    static Image fromResource(ImageResource value) { return value == null ? null : new Image(value); }

    public static Image createImage(int width, int height) {
        return new Image(javax.microedition.lcdui.Image.createImage(width, height));
    }

    public static Image createImage(int width, int height, int[] data, int off) {
        int count = width * height;
        int[] rgb;
        if (data != null && off == 0 && data.length >= count) rgb = data;
        else {
            rgb = new int[count];
            if (data != null) System.arraycopy(data, off, rgb, 0, count);
        }
        return new Image(javax.microedition.lcdui.Image.createRGBImage(rgb, width, height, true));
    }

    public static Image createImage(byte[] data) {
        ImageResource provided = ImageRuntime.openEncoded(data);
        if (provided != null) return new Image(provided);
        return new Image(javax.microedition.lcdui.Image.createImage(data, 0, data.length));
    }

    public javax.microedition.lcdui.Image getMIDPImage() {
        return resource == null ? midpImage : resource.getImage();
    }

    boolean isProviderBacked() { return resource != null; }
    boolean isTransparentEnabled() { return transparentEnabled; }

    protected void setMIDPImage(javax.microedition.lcdui.Image img) {
        detachGraphics();
        releaseResource();
        invalidateAlphaRenderImage();
        midpImage = img;
        originalImage = img;
    }

    public Graphics getGraphics() {
        if (resource != null) return null;
        if (graphics == null && midpImage != null) {
            graphics = new Graphics2Impl();
            graphics.init(midpImage);
        }
        return graphics;
    }

    public void setTransparentColor(int color) {
        transparentColor = color;
        if (resource == null) applyTransparency();
    }
    public int getTransparentColor() { return transparentColor; }
    public void setTransparentEnabled(boolean enabled) {
        transparentEnabled = enabled;
        if (resource == null) applyTransparency();
    }
    public void setAlpha(int value) {
        if (value < 0 || value > 255) throw new IllegalArgumentException("alpha out of range");
        alpha = value;
    }
    public int getAlpha() { return alpha; }

    /**
     * 回傳一張已套用好 DoJa 半透明效果的 MIDP 圖。
     * - 唯讀圖片 (Immutable)：快取算好的半透明副本，以便重複使用。
     * - 可變圖片 (Mutable)：直接置 NULL，由 Graphics 的來源 scratch path 接手。
     */
    javax.microedition.lcdui.Image getMIDPAlphaRenderImage(javax.microedition.lcdui.Image source) {
        int value = alpha;
        if (value >= 255) return source;
        if (value <= 0 || source == null) return null;

        /* 第一次透明度渲染後的每幀高頻熱路徑。 */
        if (alphaRenderImage != null && alphaRenderSource == source
                && alphaRenderValue == value) {
            return alphaRenderImage;
        }
        if (source.isMutable()) return null;

        boolean dither = shouldDitherAlpha();

        int w = source.getWidth();
        int h = source.getHeight();
        int count = w * h;
        int[] pixels = new int[count];
        source.getRGB(pixels, 0, w, 0, 0, w, h);

        if (dither) {
            /*
            * 當 MIDP 的 numAlphaLevels() == 2 時，系統會把所有中間的半透明值直接強制變成『全透明』。
            * 因此，這裡改用『棋盤格』。雖然是偽的，但能模擬出 65 階濃淡的半透明視覺效果。
            */
            for (int y = 0, i = 0; y < h; y++) {
                for (int x = 0; x < w; x++, i++) {
                    int pixel = pixels[i];
                    int sourceAlpha = (pixel >>> 24) & 255;
                    if (sourceAlpha == 0) {
                        pixels[i] = 0;
                        continue;
                    }
                    int effective = (sourceAlpha * value + 127) / 255;
                    pixels[i] = ditherAlphaPixel((effective << 24) | (pixel & 0x00FFFFFF), x, y);
                }
            }
        } else {
            for (int i = 0; i < count; i++) {
                int pixel = pixels[i];
                int sourceAlpha = (pixel >>> 24) & 255;
                int effective = (sourceAlpha * value + 127) / 255;
                pixels[i] = (effective << 24) | (pixel & 0x00FFFFFF);
            }
        }

        javax.microedition.lcdui.Image rendered =
                javax.microedition.lcdui.Image.createRGBImage(pixels, w, h, true);
        alphaRenderSource = source;
        alphaRenderValue = value;
        alphaRenderImage = rendered;
        return rendered;
    }

    /**
     * Source-over 圖片路徑只快取穩定使用的透明度；同一來源的有效透明度
     * 改變後，改用 Graphics 的固定暫存區，避免再建立原生圖片。
     */
    boolean useSourceOverAlphaCache(javax.microedition.lcdui.Image source,
            int value, boolean areaFitsScratch) {
        if (source != alphaRenderHistorySource) {
            alphaRenderHistorySource = source;
            alphaRenderHistoryValue = value;
            alphaRenderAlphaChanged = false;
        } else if (alphaRenderHistoryValue != value) {
            alphaRenderHistoryValue = value;
            alphaRenderAlphaChanged = true;
        }
        return areaFitsScratch && !alphaRenderAlphaChanged;
    }

    static boolean shouldDitherAlpha() {
        return Display.__midpNumAlphaLevels() <= 2;
    }

    static int ditherAlphaPixel(int pixel, int x, int y) {
        int alpha = (pixel >>> 24) & 0xFF;
        if (alpha == 0) return 0;
        if (alpha == 255) return 0xFF000000 | (pixel & 0x00FFFFFF);
        int coverage = (alpha * 64 + 127) / 255;
        return BAYER_8X8[((y & 7) << 3) | (x & 7)] >= coverage
                ? 0 : 0xFF000000 | (pixel & 0x00FFFFFF);
    }

    private void invalidateAlphaRenderImage() {
        alphaRenderImage = null;
        alphaRenderSource = null;
        alphaRenderValue = -1;
        alphaRenderHistorySource = null;
        alphaRenderHistoryValue = -1;
        alphaRenderAlphaChanged = false;
    }

    public int getWidth() {
        if (resource != null) return resource.getWidth();
        return midpImage == null ? 0 : midpImage.getWidth();
    }
    public int getHeight() {
        if (resource != null) return resource.getHeight();
        return midpImage == null ? 0 : midpImage.getHeight();
    }

    public void dispose() {
        detachGraphics();
        invalidateAlphaRenderImage();
        midpImage = null;
        originalImage = null;
        releaseResource();
    }

    private void releaseResource() {
        if (resource != null) { resource.dispose(); resource = null; }
    }

    private void detachGraphics() {
        if (graphics != null) { graphics.dispose(); graphics = null; }
    }

    private void applyTransparency() {
        if (originalImage == null) return;
        invalidateAlphaRenderImage();
        if (!transparentEnabled) {
            if (midpImage != originalImage) detachGraphics();
            midpImage = originalImage;
            return;
        }
        detachGraphics();
        int w = originalImage.getWidth();
        int h = originalImage.getHeight();
        int[] rgb = new int[w * h];
        int transparent = transparentColor & 0x00FFFFFF;
        originalImage.getRGB(rgb, 0, w, 0, 0, w, h);
        for (int i = 0; i < rgb.length; i++) if ((rgb[i] & 0x00FFFFFF) == transparent) rgb[i] = 0;
        midpImage = javax.microedition.lcdui.Image.createRGBImage(rgb, w, h, true);
    }
}
