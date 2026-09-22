package doja;

import com.nttdocomo.ui.Image;

/**
 * Graphics2 的通用實作。
 *
 * 當 sourceRatio + destinationRatio == 255 時，DoJa 的 OP_ADD 等同於
 * Source-over Alpha。這條路徑把來源像素直接交給 MIDP drawRGB()，避免
 * 讀回 framebuffer；其他 raster mode 仍由 Graphics 的既有軟體合成處理。
 */
public final class Graphics2Impl extends com.nttdocomo.opt.ui.Graphics2 {
    private int color = 0xFF000000;

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

        drawSourceOverRect(x, y, width, height, color & 0x00FFFFFF, alpha);
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
        int alpha = scaleAlpha(image.getAlpha(), srcRatio);
        if (scaled) {
            drawSourceOverImage(image, alpha, dx, dy, dw, dh, sx, sy, sw, sh);
        } else {
            drawSourceOverImage(image, alpha, dx, dy, sx, sy, sw, sh);
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

    private boolean isInterpolatedAdd() {
        return renderMode == OP_ADD && dstRatio == 255 - srcRatio;
    }

    private static int scaleAlpha(int alpha, int ratio) {
        return (alpha * ratio + 127) / 255;
    }
}
