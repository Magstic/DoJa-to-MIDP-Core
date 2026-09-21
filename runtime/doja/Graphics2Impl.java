package doja;

import com.nttdocomo.ui.Image;

/**
 * Graphics2 的通用實作。
 *
 * 【OP_ADD 渲染優化】
 * 當 sourceRatio + destinationRatio == 255 時，DoJa 的 OP_ADD 在數學上等同於標準的 Source-Over Alpha 混合。
 * 針對此特例，本實作直接使用底層原生的半透明渲染，從而避免將畫面讀回 Framebuffer 再逐像素進行 Java 軟體混合的昂貴開銷。
 * 
 * 註：因原生路徑與 Java 軟體路徑在 8-bit 數值取整的順序不同，最終顏色的通道值允許『極微小的差異』。
 * 這是唯一美中不足的地方，但肉眼幾乎不可察。
 * 至於其他比例的 ADD 以及所有的 SUB 操作，仍交由 Graphics 既有的軟體路徑處理。
 *
 * 【透明度與單色快取機制】
 * - 圖片透明度：直接復用 Image 既有的 Lazy Cache。
 * - 單色半透明矩形：採用極簡快取策略，系統只保留『最後一次使用的不透明底圖』，
 *   並同樣交由上述的 Image 透明度快取來產生半透明副本。
 * - 回退策略：若底圖記憶體配置失敗，會直接退回使用 Graphics 既有的 solidCompositeLut 進行軟體混合。
 */
public final class Graphics2Impl extends com.nttdocomo.opt.ui.Graphics2 {
    private int color = 0xFF000000;

    private Image solidLayer;
    private int solidLayerRGB;
    private int solidLayerWidth;
    private int solidLayerHeight;
    private int failedSolidRGB;
    private int failedSolidWidth;
    private int failedSolidHeight;
    private boolean solidLayerFailed;

    public Graphics2Impl() { super(); }

    public void setColor(int argb) {
        if ((argb & 0xFF000000) == 0) argb = 0xFF000000 | (argb & 0x00FFFFFF);
        color = argb;
        super.setColor(argb);
    }

    public void fillRect(int x, int y, int width, int height) {
        if (!isInterpolatedAdd() || width <= 0 || height <= 0) {
            super.fillRect(x, y, width, height);
            return;
        }

        int alpha = scaleAlpha((color >>> 24) & 0xFF, srcRatio);
        if (alpha <= 0) return;

        if (alpha >= 255) {
            drawReplaceRect(x, y, width, height);
            return;
        }

        Image layer = getSolidLayer(color & 0x00FFFFFF, width, height);
        if (layer == null) {
            super.fillRect(x, y, width, height);
            return;
        }

        int savedMode = renderMode;
        int savedSrc = srcRatio;
        int savedDst = dstRatio;
        int savedAlpha = layer.getAlpha();
        try {
            layer.setAlpha(alpha);
            super.setRenderMode(OP_REPL, 255, 0);
            super.drawImage(layer, x, y);
        } finally {
            layer.setAlpha(savedAlpha);
            super.setRenderMode(savedMode, savedSrc, savedDst);
        }
    }

    public void drawImage(Image image, int dx, int dy, int sx, int sy, int width, int height) {
        if (!isInterpolatedAdd() || image == null) {
            super.drawImage(image, dx, dy, sx, sy, width, height);
            return;
        }
        drawInterpolated(image, false, dx, dy, width, height, sx, sy, width, height);
    }

    public void drawScaledImage(Image image, int dx, int dy, int dw, int dh,
            int sx, int sy, int sw, int sh) {
        if (!isInterpolatedAdd() || image == null) {
            super.drawScaledImage(image, dx, dy, dw, dh, sx, sy, sw, sh);
            return;
        }
        drawInterpolated(image, true, dx, dy, dw, dh, sx, sy, sw, sh);
    }

    private void drawInterpolated(Image image, boolean scaled,
            int dx, int dy, int dw, int dh, int sx, int sy, int sw, int sh) {
        int savedMode = renderMode;
        int savedSrc = srcRatio;
        int savedDst = dstRatio;
        int savedAlpha = image.getAlpha();
        int alpha = scaleAlpha(savedAlpha, savedSrc);
        try {
            super.setRenderMode(OP_REPL, 255, 0);
            image.setAlpha(alpha);
            if (scaled) super.drawScaledImage(image, dx, dy, dw, dh, sx, sy, sw, sh);
            else super.drawImage(image, dx, dy, sx, sy, sw, sh);
        } finally {
            image.setAlpha(savedAlpha);
            super.setRenderMode(savedMode, savedSrc, savedDst);
        }
    }

    private void drawReplaceRect(int x, int y, int width, int height) {
        int savedMode = renderMode;
        int savedSrc = srcRatio;
        int savedDst = dstRatio;
        try {
            super.setRenderMode(OP_REPL, 255, 0);
            super.fillRect(x, y, width, height);
        } finally {
            super.setRenderMode(savedMode, savedSrc, savedDst);
        }
    }

    private Image getSolidLayer(int rgb, int width, int height) {
        if (solidLayer != null && solidLayerRGB == rgb
                && solidLayerWidth == width && solidLayerHeight == height) {
            return solidLayer;
        }
        if (solidLayerFailed && failedSolidRGB == rgb
                && failedSolidWidth == width && failedSolidHeight == height) {
            return null;
        }

        if (solidLayer != null) {
            solidLayer.dispose();
            solidLayer = null;
        }

        long countLong = (long)width * (long)height;
        if (countLong <= 0L || countLong > Integer.MAX_VALUE) {
            rememberSolidFailure(rgb, width, height);
            return null;
        }

        try {
            int count = (int)countLong;
            int pixel = 0xFF000000 | rgb;
            int[] pixels = new int[count];
            for (int i = 0; i < count; i++) pixels[i] = pixel;
            solidLayer = Image.createImage(width, height, pixels, 0);
            solidLayerRGB = rgb;
            solidLayerWidth = width;
            solidLayerHeight = height;
            solidLayerFailed = false;
            return solidLayer;
        } catch (OutOfMemoryError failure) {
            rememberSolidFailure(rgb, width, height);
            return null;
        } catch (RuntimeException failure) {
            rememberSolidFailure(rgb, width, height);
            return null;
        }
    }

    private void rememberSolidFailure(int rgb, int width, int height) {
        failedSolidRGB = rgb;
        failedSolidWidth = width;
        failedSolidHeight = height;
        solidLayerFailed = true;
    }

    private boolean isInterpolatedAdd() {
        return renderMode == OP_ADD && dstRatio == 255 - srcRatio;
    }

    private static int scaleAlpha(int alpha, int ratio) {
        return (alpha * ratio + 127) / 255;
    }
}
