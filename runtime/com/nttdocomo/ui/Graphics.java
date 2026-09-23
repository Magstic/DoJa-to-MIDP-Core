package com.nttdocomo.ui;

public class Graphics {
    public static final int BLACK = 0;
    public static final int BLUE = 1;
    public static final int LIME = 2;
    public static final int AQUA = 3;
    public static final int RED = 4;
    public static final int FUCHSIA = 5;
    public static final int YELLOW = 6;
    public static final int WHITE = 7;
    public static final int GRAY = 8;
    public static final int NAVY = 9;
    public static final int GREEN = 10;
    public static final int TEAL = 11;
    public static final int MAROON = 12;
    public static final int PURPLE = 13;
    public static final int OLIVE = 14;
    public static final int SILVER = 15;

    public static final int FLIP_NONE = 0;
    public static final int FLIP_HORIZONTAL = 1;
    public static final int FLIP_VERTICAL = 2;
    public static final int FLIP_ROTATE = 3;
    public static final int FLIP_ROTATE_LEFT = 4;
    public static final int FLIP_ROTATE_RIGHT = 5;
    public static final int FLIP_ROTATE_RIGHT_HORIZONTAL = 6;
    public static final int FLIP_ROTATE_RIGHT_VERTICAL = 7;

    protected javax.microedition.lcdui.Graphics midpGraphics;
    protected javax.microedition.lcdui.Image backBuffer;
    protected Canvas parentCanvas;
    protected int screenWidth;
    protected int screenHeight;
    private int currentARGB = 0xFF000000;
    private int originX;
    private int originY;
    private int flipMode;
    private Font currentFont;
    protected int renderMode = 0;
    protected int srcRatio = 255;
    protected int dstRatio = 255;
    private boolean nativeRenderFastPath = true;
    private boolean sourceOverAlphaPath;
    private int lockCount;
    private boolean presenting;
    private int[] fillScratch;
    private int[] pixelScratch;
    private int[] blendScratch;
    private int[] polygonScratch;
    private int[] scaleMapX;
    private int[] scaleMapY;
    private int[] solidCompositeLut;
    private int solidLutSource;
    private int solidLutMode;
    private int solidLutSrcRatio;
    private int solidLutDstRatio;
    private boolean solidLutValid;
    private int[] sourceRatioLut;
    private int[] sourceScaleLut;
    private int[] destinationRatioLut;
    private int ratioLutSource = -1;
    private int ratioLutDestination = -1;
    /*
    * DoJa 5.x 支援全圖 256 階半透明。非 Source-over 的 raster mode
    * 仍需讀回 framebuffer，這兩個 LUT 會先算好當前透明度的顏色比重，
    * 供每格畫面重複使用；互補 OP_ADD 則在 source-only 路徑中交給 MIDP drawRGB。
    */
    private int[] imageAlphaSourceLut;
    private int[] imageAlphaDestinationLut;
    private int imageAlphaLutValue = -1;
    private javax.microedition.lcdui.Image textMaskImage;
    private javax.microedition.lcdui.Graphics textMaskGraphics;
    private int textMaskWidth;
    private int textMaskHeight;
    private int[] textMaskPixels;
    private static final int COMPOSITE_PIXELS = 4096;
    private static final int SOLID_COMPOSITE_PIXELS = 16384;

    // 預先計算 0 到 90 度的 Sine 定點數表（Q30 格式）。
    // 其餘象限的角度，可以利用正弦函數的對稱性補齊：Cosine 直接加 90 度就能複用該表。
    // 這可以避免昂貴的浮點運算。
    private static final int[] SIN_Q30_0_90 = {
        0, 18739379, 37473049, 56195305, 74900443, 93582766, 112236583, 130856211,
        149435979, 167970228, 186453311, 204879599, 223243478, 241539355, 259761657, 277904834,
        295963357, 313931728, 331804471, 349576144, 367241333, 384794656, 402230767, 419544355,
        436730145, 453782903, 470697435, 487468587, 504091252, 520560366, 536870912, 553017922,
        568996477, 584801711, 600428808, 615873009, 631129609, 646193961, 661061475, 675727625,
        690187940, 704438018, 718473518, 732290163, 745883746, 759250125, 772385229, 785285058,
        797945680, 810363241, 822533958, 834454122, 846120104, 857528349, 868675383, 879557810,
        890172315, 900515665, 910584710, 920376381, 929887697, 939115760, 948057759, 956710970,
        965072759, 973140576, 980911966, 988384560, 995556083, 1002424350, 1008987269, 1015242840,
        1021189159, 1026824413, 1032146887, 1037154959, 1041847103, 1046221891, 1050277989, 1054014162,
        1057429273, 1060522280, 1063292242, 1065738315, 1067859754, 1069655912, 1071126243, 1072270298,
        1073087729, 1073578288, 1073741824
    };

    private static int sinQ30(int angle) {
        if (angle <= 90) return SIN_Q30_0_90[angle];
        if (angle <= 180) return SIN_Q30_0_90[180 - angle];
        if (angle <= 270) return -SIN_Q30_0_90[angle - 180];
        return -SIN_Q30_0_90[360 - angle];
    }

    private static int cosQ30(int angle) {
        int shifted = angle + 90;
        if (shifted >= 360) shifted -= 360;
        return sinQ30(shifted);
    }

    public Graphics() {
        currentFont = Font.getDefaultFont();
    }

    public void init(int width, int height) {
        if (width <= 0) width = 240;
        if (height <= 0) height = 240;
        screenWidth = width;
        screenHeight = height;
        backBuffer = javax.microedition.lcdui.Image.createImage(width, height);
        midpGraphics = backBuffer.getGraphics();
        originX = 0;
        originY = 0;
        lockCount = 0;
        presenting = false;
        setColor(currentARGB);
        if (currentFont != null) setFont(currentFont);
    }

    void init(javax.microedition.lcdui.Image mutableImage) {
        backBuffer = mutableImage;
        midpGraphics = mutableImage.getGraphics();
        screenWidth = mutableImage.getWidth();
        screenHeight = mutableImage.getHeight();
        originX = 0;
        originY = 0;
        lockCount = 0;
        presenting = false;
        setColor(currentARGB);
        if (currentFont != null) setFont(currentFont);
    }

    protected void ensureSurface() {
        if (midpGraphics != null) return;
        int width = screenWidth;
        int height = screenHeight;
        if (parentCanvas != null) {
            int canvasWidth = parentCanvas.getWidth();
            int canvasHeight = parentCanvas.getHeight();
            if (canvasWidth > 0) width = canvasWidth;
            if (canvasHeight > 0) height = canvasHeight;
        }
        if (width <= 0) width = 240;
        if (height <= 0) height = 240;
        init(width, height);
    }

    public javax.microedition.lcdui.Graphics getMIDPGraphics() {
        ensureSurface();
        return midpGraphics;
    }

    public javax.microedition.lcdui.Image getBackBuffer() {
        ensureSurface();
        return backBuffer;
    }

    void paintDisplay(javax.microedition.lcdui.Graphics g) {
        ensureSurface();
        synchronized (this) {
            while (lockCount != 0) {
                try { wait(); } catch (InterruptedException ignored) {}
            }
            int physicalWidth = parentCanvas == null ? backBuffer.getWidth() : parentCanvas.__midpPhysicalWidth();
            int physicalHeight = parentCanvas == null ? backBuffer.getHeight() : parentCanvas.__midpPhysicalHeight();
            int x = (physicalWidth - backBuffer.getWidth()) / 2;
            int y = (physicalHeight - backBuffer.getHeight()) / 2;
            if (x != 0 || y != 0) {
                int background = parentCanvas == null ? 0 : parentCanvas.getBackground();
                g.setColor(background & 0x00FFFFFF);
                g.fillRect(0, 0, physicalWidth, physicalHeight);
            }
            g.drawImage(backBuffer, x, y,
                javax.microedition.lcdui.Graphics.TOP | javax.microedition.lcdui.Graphics.LEFT);
        }
    }

    public void lock() {
        ensureSurface();
        synchronized (this) {
            while (presenting) {
                try { wait(); } catch (InterruptedException ignored) {}
            }
            lockCount++;
        }
    }

    public void unlock(boolean forced) {
        ensureSurface();
        boolean present = false;
        synchronized (this) {
            if (lockCount == 0) return;
            if (forced) {
                lockCount = 0;
                present = parentCanvas != null && !parentCanvas.__midpIsPaintCallback();
            } else {
                lockCount--;
                present = lockCount == 0 && parentCanvas != null && !parentCanvas.__midpIsPaintCallback();
            }
            if (present) presenting = true;
            notifyAll();
        }
        if (!present) return;
        try {
            parentCanvas.__midpPresent();
        } finally {
            synchronized (this) {
                presenting = false;
                notifyAll();
            }
        }
    }

    public static int getColorOfRGB(int r, int g, int b) {
        return 0xFF000000 | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public static int getColorOfRGB(int r, int g, int b, int a) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public static int getColorOfName(int name) {
        switch (name) {
            case BLACK: return getColorOfRGB(0x00, 0x00, 0x00);
            case BLUE: return getColorOfRGB(0x00, 0x00, 0xFF);
            case LIME: return getColorOfRGB(0x00, 0xFF, 0x00);
            case AQUA: return getColorOfRGB(0x00, 0xFF, 0xFF);
            case RED: return getColorOfRGB(0xFF, 0x00, 0x00);
            case FUCHSIA: return getColorOfRGB(0xFF, 0x00, 0xFF);
            case YELLOW: return getColorOfRGB(0xFF, 0xFF, 0x00);
            case WHITE: return getColorOfRGB(0xFF, 0xFF, 0xFF);
            case GRAY: return getColorOfRGB(0x80, 0x80, 0x80);
            case NAVY: return getColorOfRGB(0x00, 0x00, 0x80);
            case GREEN: return getColorOfRGB(0x00, 0x80, 0x00);
            case TEAL: return getColorOfRGB(0x00, 0x80, 0x80);
            case MAROON: return getColorOfRGB(0x80, 0x00, 0x00);
            case PURPLE: return getColorOfRGB(0x80, 0x00, 0x80);
            case OLIVE: return getColorOfRGB(0x80, 0x80, 0x00);
            case SILVER: return getColorOfRGB(0xC0, 0xC0, 0xC0);
            default: return getColorOfRGB(0, 0, 0);
        }
    }

    public void setColor(int argb) {
        ensureSurface();
        /* 
         * 處理 DoJa 顏色格式：當傳入的 32bit ARGB 未指定 Alpha 時，預設視為不透明色。
         * 特定的渲染模式中，透明度則留待繪製階段再另外計算 & 使用。
         */
        if ((argb & 0xFF000000) == 0) {
            argb = 0xFF000000 | (argb & 0x00FFFFFF);
        }
        currentARGB = argb;
        midpGraphics.setColor(argb & 0x00FFFFFF);
    }

    protected void setRenderModeState(int operator, int sourceRatio, int destinationRatio) {
        if (operator < 0 || operator > 2) throw new IllegalArgumentException("invalid raster operator");
        if (sourceRatio < 0 || sourceRatio > 255 || destinationRatio < 0 || destinationRatio > 255) {
            throw new IllegalArgumentException("raster ratio out of range");
        }
        renderMode = operator;
        srcRatio = sourceRatio;
        dstRatio = destinationRatio;
        nativeRenderFastPath = operator == 0 && sourceRatio == 255;
        solidLutValid = false;
        ratioLutSource = -1;
        ratioLutDestination = -1;
    }

    protected final int getRenderModeState() { return renderMode; }
    protected final int getSourceRatioState() { return srcRatio; }
    protected final int getDestinationRatioState() { return dstRatio; }
    protected final int getFlipModeState() { return flipMode; }
    protected final boolean isNativeRenderFastPath() { return nativeRenderFastPath; }
    protected final boolean hasActiveLock() { synchronized (this) { return lockCount != 0; } }
    protected final boolean canPresentLockedSurface() {
        return parentCanvas != null && Display.getCurrent() == parentCanvas && parentCanvas.isShown();
    }

    protected int getEffectiveAlpha(int objectAlpha) {
        int a = objectAlpha < 0 ? 0 : (objectAlpha > 255 ? 255 : objectAlpha);
        int colorAlpha = (currentARGB >>> 24) & 0xFF;
        return colorAlpha < a ? colorAlpha : a;
    }

    protected int getOriginX() { return originX; }
    protected int getOriginY() { return originY; }

    public void setFont(Font font) {
        ensureSurface();
        currentFont = font;
        if (font != null) {
            midpGraphics.setFont(font.getMIDPFont());
        }
    }

    public void setClip(int x, int y, int width, int height) {
        ensureSurface();
        midpGraphics.setClip(x, y, width, height);
    }

    public void clearClip() {
        ensureSurface();
        midpGraphics.setClip(-originX, -originY, screenWidth, screenHeight);
    }

    public void clipRect(int x, int y, int width, int height) {
        ensureSurface();
        midpGraphics.clipRect(x, y, width, height);
    }

    public void setOrigin(int x, int y) {
        ensureSurface();
        midpGraphics.translate(x - originX, y - originY);
        originX = x;
        originY = y;
    }

    public void setFlipMode(int mode) {
        flipMode = mode;
    }

    public void drawImage(Image img, int[] matrix) {
        if (matrix == null || matrix.length < 6 || img == null) return;
        drawImage(img, matrix[4], matrix[5], 0, 0, img.getWidth(), img.getHeight());
    }

    public void drawImage(Image img, int[] matrix, int sx, int sy, int width, int height) {
        if (matrix == null || matrix.length < 6) return;
        drawImage(img, matrix[4], matrix[5], sx, sy, width, height);
    }

    public void drawImage(Image img, int x, int y) {
        if (img == null) return;
        drawImage(img, x, y, 0, 0, img.getWidth(), img.getHeight());
    }

    public void drawImage(Image img, int dx, int dy, int sx, int sy, int width, int height) {
        drawUnscaledSubImage(img, dx, dy, sx, sy, width, height);
    }

    public void drawScaledImage(Image img, int dx, int dy, int dw, int dh, int sx, int sy, int sw, int sh) {
        drawSubImage(img, dx, dy, sx, sy, sw, sh, dw, dh);
    }

    /** 繪製互補 OP_ADD 圖片，且不改變呼叫端的光柵狀態。 */
    protected final void drawSourceOverImage(Image image, int alpha,
            int dx, int dy, int sx, int sy, int width, int height) {
        if (image == null) return;
        int savedAlpha = image.getAlpha();
        boolean savedPath = sourceOverAlphaPath;
        try {
            sourceOverAlphaPath = true;
            image.setAlpha(alpha);
            drawUnscaledSubImage(image, dx, dy, sx, sy, width, height);
        } finally {
            sourceOverAlphaPath = savedPath;
            image.setAlpha(savedAlpha);
        }
    }

    /** 繪製互補 OP_ADD 縮放圖片，不讀回目的畫面。 */
    protected final void drawSourceOverImage(Image image, int alpha,
            int dx, int dy, int dw, int dh, int sx, int sy, int sw, int sh) {
        if (image == null) return;
        int savedAlpha = image.getAlpha();
        boolean savedPath = sourceOverAlphaPath;
        try {
            sourceOverAlphaPath = true;
            image.setAlpha(alpha);
            drawSubImage(image, dx, dy, sx, sy, sw, sh, dw, dh);
        } finally {
            sourceOverAlphaPath = savedPath;
            image.setAlpha(savedAlpha);
        }
    }

    /** 使用可重用的 ARGB 暫存區填滿互補 OP_ADD 矩形。 */
    protected final void drawSourceOverRect(int x, int y, int width, int height,
            int rgb, int alpha) {
        ensureSurface();
        if (width <= 0 || height <= 0 || alpha <= 0) return;

        int clipX = midpGraphics.getClipX();
        int clipY = midpGraphics.getClipY();
        int clipR = clipX + midpGraphics.getClipWidth();
        int clipB = clipY + midpGraphics.getClipHeight();
        int left = x > clipX ? x : clipX;
        int top = y > clipY ? y : clipY;
        int right = x + width < clipR ? x + width : clipR;
        int bottom = y + height < clipB ? y + height : clipB;
        int logicalLeft = -originX;
        int logicalTop = -originY;
        int logicalRight = screenWidth - originX;
        int logicalBottom = screenHeight - originY;
        if (left < logicalLeft) left = logicalLeft;
        if (top < logicalTop) top = logicalTop;
        if (right > logicalRight) right = logicalRight;
        if (bottom > logicalBottom) bottom = logicalBottom;
        if (left >= right || top >= bottom) return;

        int blockWidth = right - left;
        if (blockWidth > COMPOSITE_PIXELS) blockWidth = COMPOSITE_PIXELS;
        int rowsPerBlock = COMPOSITE_PIXELS / blockWidth;
        if (rowsPerBlock < 1) rowsPerBlock = 1;
        ensureScratch(blockWidth * rowsPerBlock);
        boolean dither = alpha < 255 && Image.shouldDitherAlpha();
        int opaquePixel = 0xFF000000 | (rgb & 0x00FFFFFF);
        int alphaPixel = (alpha << 24) | (rgb & 0x00FFFFFF);

        for (int by = top; by < bottom; by += rowsPerBlock) {
            int bh = bottom - by;
            if (bh > rowsPerBlock) bh = rowsPerBlock;
            for (int bx = left; bx < right; bx += blockWidth) {
                int bw = right - bx;
                if (bw > blockWidth) bw = blockWidth;
                for (int row = 0; row < bh; row++) {
                    int localY = by + row - y;
                    int rowOffset = row * bw;
                    for (int col = 0; col < bw; col++) {
                        fillScratch[rowOffset + col] = dither
                                ? Image.ditherAlphaPixel(alphaPixel, bx + col - x, localY)
                                : (alpha == 255 ? opaquePixel : alphaPixel);
                    }
                }
                midpGraphics.drawRGB(fillScratch, 0, bw, bx, by, bw, bh, true);
            }
        }
    }

    /**
     * 處理無縮放的子圖繪製（DoJa 語義）
     * DoJa 的 drawImage() 採用 1:1 像素映射。當指定的來源矩形超出圖片實際邊界時，
     * 僅繪製相交的『有效區域』，絕不將剩餘像素強制拉伸填滿請求尺寸，避免貼邊 Sprite 發生變形。
     */
    private void drawUnscaledSubImage(Image img, int dx, int dy, int sx, int sy, int width, int height) {
        if (img == null || width <= 0 || height <= 0) return;
        int imgW = img.getWidth();
        int imgH = img.getHeight();

        long requestedRight = (long)sx + (long)width;
        long requestedBottom = (long)sy + (long)height;
        int clippedX = sx < 0 ? 0 : sx;
        int clippedY = sy < 0 ? 0 : sy;
        int clippedRight = requestedRight > imgW ? imgW : (int)requestedRight;
        int clippedBottom = requestedBottom > imgH ? imgH : (int)requestedBottom;
        if (clippedX >= clippedRight || clippedY >= clippedBottom) return;

        int clippedW = clippedRight - clippedX;
        int clippedH = clippedBottom - clippedY;
        int relX = clippedX - sx;
        int relY = clippedY - sy;
        int outX = dx;
        int outY = dy;

        /**
         * 當來源圖片經過裁切後，目的座標依然先依「未裁切的完整尺寸」進行計算，之後再將繪製範圍對齊至剩餘的實際交集區域，並施加矩陣變換。
         * 這樣可以確保 Sprite 在貼邊翻轉時，畫面邊界與錨點依然正確，不會發生位置跳躍。
         */
        switch (flipMode) {
            case FLIP_HORIZONTAL:
                outX += width - relX - clippedW;
                outY += relY;
                break;
            case FLIP_VERTICAL:
                outX += relX;
                outY += height - relY - clippedH;
                break;
            case FLIP_ROTATE:
                outX += width - relX - clippedW;
                outY += height - relY - clippedH;
                break;
            case FLIP_ROTATE_LEFT:
                outX += relY;
                outY += width - relX - clippedW;
                break;
            case FLIP_ROTATE_RIGHT:
                outX += height - relY - clippedH;
                outY += relX;
                break;
            case FLIP_ROTATE_RIGHT_HORIZONTAL:
                outX += relY;
                outY += relX;
                break;
            case FLIP_ROTATE_RIGHT_VERTICAL:
                outX += height - relY - clippedH;
                outY += width - relX - clippedW;
                break;
            default:
                outX += relX;
                outY += relY;
                break;
        }

        drawSubImage(img, outX, outY, clippedX, clippedY,
            clippedW, clippedH, clippedW, clippedH);
    }

    private void drawSubImage(Image img, int dx, int dy, int sx, int sy, int sw, int sh, int dw, int dh) {
        ensureSurface();
        if (img == null) return;
        if (sw <= 0 || sh <= 0 || dw <= 0 || dh <= 0) return;
        int imageAlpha = img.getAlpha();
        if (imageAlpha <= 0) return;
        int imgW = img.getWidth();
        int imgH = img.getHeight();
        if (sx < 0) { sw += sx; sx = 0; }
        if (sy < 0) { sh += sy; sy = 0; }
        if (sx + sw > imgW) sw = imgW - sx;
        if (sy + sh > imgH) sh = imgH - sy;
        if (sw <= 0 || sh <= 0) return;

        javax.microedition.lcdui.Image src = img.getMIDPImage();
        if (src == null) return;

        boolean sourceOver = sourceOverAlphaPath;
        boolean alphaCacheAllowed = true;
        if (sourceOver) {
            alphaCacheAllowed = img.useSourceOverAlphaCache(src, imageAlpha,
                    (long)src.getWidth() * (long)src.getHeight() <= COMPOSITE_PIXELS);
        }
        /*
        * 可化約成 Source-over 的路徑優先使用 MIDP 原生繪製：小型穩定透明度
        * 圖片使用 Image 的單一 lazy cache，動態透明度則使用下方固定大小的
        * source-only scratch path。其他 raster mode 仍保留 framebuffer 軟體合成。
        * - 只支援開/關透明的手機：cache 與 scratch 都使用同一套 Bayer 抖動。
        * - 支援真實半透明的手機：直接交給 MIDP drawImage/drawRGB 處理 ARGB。
        */
        if ((nativeRenderFastPath || sourceOver) && sw == dw && sh == dh) {
            javax.microedition.lcdui.Image nativeSrc = src;
            if (imageAlpha < 255) {
                nativeSrc = alphaCacheAllowed ? img.getMIDPAlphaRenderImage(src) : null;
            }
            if (nativeSrc != null) {
                if (flipMode == FLIP_NONE) {
                    int cx = midpGraphics.getClipX();
                    int cy = midpGraphics.getClipY();
                    int cw = midpGraphics.getClipWidth();
                    int ch = midpGraphics.getClipHeight();
                    midpGraphics.clipRect(dx, dy, dw, dh);
                    midpGraphics.drawImage(nativeSrc, dx - sx, dy - sy,
                        javax.microedition.lcdui.Graphics.TOP | javax.microedition.lcdui.Graphics.LEFT);
                    midpGraphics.setClip(cx, cy, cw, ch);
                    return;
                }
                try {
                    midpGraphics.drawRegion(nativeSrc, sx, sy, sw, sh, toMIDPTransform(flipMode), dx, dy,
                        javax.microedition.lcdui.Graphics.TOP | javax.microedition.lcdui.Graphics.LEFT);
                    return;
                } catch (Throwable ignored) {
                }
            }
        }

        /* 可變動 (Mutable) 或經過縮放 (Scaled) 的圖片，不能直接使用之前快取的『不可變圖片副本』！ */
        if (sourceOver && imageAlpha < 255) {
            drawNativeStreaming(src, img, dx, dy, sx, sy, sw, sh, dw, dh);
            return;
        }

        if (nativeRenderFastPath && imageAlpha < 255 && sw == dw && sh == dh
                && flipMode == FLIP_NONE) {
            drawUnscaledAlphaImage(src, img, dx, dy, sx, sy, sw, sh, imageAlpha);
            return;
        }

        drawNativeStreaming(src, img, dx, dy, sx, sy, sw, sh, dw, dh);
    }

    private void drawNativeStreaming(javax.microedition.lcdui.Image src, Image image,
            int dx, int dy, int sx, int sy, int sw, int sh, int dw, int dh) {
        boolean rotated = isRotated(flipMode);
        int outW = rotated ? dh : dw;
        int outH = rotated ? dw : dh;
        if (outW <= 0 || outH <= 0 || sw <= 0 || sh <= 0) return;
        int imageAlpha = image.getAlpha();
        boolean colourKey = image.isTransparentEnabled();
        int transparent = colourKey ? (image.getTransparentColor() & 0x00ffffff) : 0;
        boolean dither = sourceOverAlphaPath && imageAlpha < 255 && Image.shouldDitherAlpha();
        if (sw != dw || sh != dh || outW > COMPOSITE_PIXELS) {
            prepareScaleMaps(dw, dh, sw, sh);
        }

        /* 
         * drawImage() 是系統常見的效能熱點，因此改為一次搬移一整條切片，同時將暫存緩衝區控制在 4096 像素內。
         * 在無縮放的情形下，每個切片僅需三次跨越 Java 與 MIDP 的介面呼叫，效率遠高於逐列進行 getRGB/drawRGB 操作。
         */
        if (sw == dw && sh == dh && outW <= COMPOSITE_PIXELS) {
            drawUnscaledBuffered(src, dx, dy, sx, sy, sw, sh,
                    imageAlpha, colourKey, transparent, rotated);
            return;
        }

        long sourcePixels = (long)sw * (long)sh;
        if (sourcePixels <= COMPOSITE_PIXELS && outW <= COMPOSITE_PIXELS) {
            drawScaledBuffered(src, dx, dy, sx, sy, sw, sh, dw, dh,
                    imageAlpha, colourKey, transparent, rotated);
            return;
        }

        /* 大型縮放塞不進固定工作區時改成逐列串流，使用固定寬度 tile。 */
        ensureScratch(COMPOSITE_PIXELS);
        ensurePixelScratch(sw > sh ? sw : sh);
        for (int outY = 0; outY < outH; outY++) {
            if (!rotated) {
                int preY = (flipMode == FLIP_VERTICAL || flipMode == FLIP_ROTATE)
                    ? dh - 1 - outY : outY;
                int sourceY = sy + scaleMapY[preY];
                src.getRGB(pixelScratch, 0, sw, sx, sourceY, sw, 1);
                for (int outX = 0; outX < outW; outX += COMPOSITE_PIXELS) {
                    int bw = outW - outX;
                    if (bw > COMPOSITE_PIXELS) bw = COMPOSITE_PIXELS;
                    for (int col = 0; col < bw; col++) {
                        int drawX = outX + col;
                        int preX = (flipMode == FLIP_HORIZONTAL || flipMode == FLIP_ROTATE)
                            ? dw - 1 - drawX : drawX;
                        int sourceIndex = scaleMapX[preX];
                        int sourceX = sx + sourceIndex;
                        int pixel = pixelScratch[sourceIndex];
                        fillScratch[col] = prepareImagePixel(pixel, imageAlpha, colourKey,
                                transparent, dither, sourceX, sourceY);
                    }
                    drawPreparedImagePixels(fillScratch, 0, bw, dx + outX, dy + outY, bw, 1,
                            nativeRenderFastPath && imageAlpha < 255);
                }
            } else {
                int preX;
                switch (flipMode) {
                    case FLIP_ROTATE_LEFT:
                    case FLIP_ROTATE_RIGHT_VERTICAL:
                        preX = dw - 1 - outY; break;
                    default:
                        preX = outY; break;
                }
                int sourceX = sx + scaleMapX[preX];
                src.getRGB(pixelScratch, 0, 1, sourceX, sy, 1, sh);
                for (int outX = 0; outX < outW; outX += COMPOSITE_PIXELS) {
                    int bw = outW - outX;
                    if (bw > COMPOSITE_PIXELS) bw = COMPOSITE_PIXELS;
                    for (int col = 0; col < bw; col++) {
                        int drawX = outX + col;
                        int preY;
                        switch (flipMode) {
                            case FLIP_ROTATE_RIGHT:
                            case FLIP_ROTATE_RIGHT_VERTICAL:
                                preY = dh - 1 - drawX; break;
                            default:
                                preY = drawX; break;
                        }
                        int sourceY = sy + scaleMapY[preY];
                        int pixel = pixelScratch[scaleMapY[preY]];
                        fillScratch[col] = prepareImagePixel(pixel, imageAlpha, colourKey,
                                transparent, dither, sourceX, sourceY);
                    }
                    drawPreparedImagePixels(fillScratch, 0, bw, dx + outX, dy + outY, bw, 1,
                            nativeRenderFastPath && imageAlpha < 255);
                }
            }
        }
    }

    private void drawUnscaledBuffered(javax.microedition.lcdui.Image src,
            int dx, int dy, int sx, int sy, int sw, int sh,
            int imageAlpha, boolean colourKey, int transparent, boolean rotated) {
        int outW = rotated ? sh : sw;
        int outH = rotated ? sw : sh;
        boolean dither = sourceOverAlphaPath && imageAlpha < 255 && Image.shouldDitherAlpha();
        int rowsPerBlock = COMPOSITE_PIXELS / outW;
        if (rowsPerBlock < 1) rowsPerBlock = 1;

        for (int outY = 0; outY < outH; outY += rowsPerBlock) {
            int bh = outH - outY;
            if (bh > rowsPerBlock) bh = rowsPerBlock;
            int count = outW * bh;
            ensureScratch(count);
            ensurePixelScratch(count);

            if (!rotated) {
                boolean reverseY = flipMode == FLIP_VERTICAL || flipMode == FLIP_ROTATE;
                boolean reverseX = flipMode == FLIP_HORIZONTAL || flipMode == FLIP_ROTATE;
                int readY = reverseY ? sy + sh - outY - bh : sy + outY;
                src.getRGB(pixelScratch, 0, sw, sx, readY, sw, bh);
                for (int ry = 0; ry < bh; ry++) {
                    int sourceRow = (reverseY ? bh - 1 - ry : ry) * sw;
                    int outRow = ry * outW;
                    int sourceY = reverseY ? sy + sh - 1 - (outY + ry) : sy + outY + ry;
                    for (int ox = 0; ox < outW; ox++) {
                        int sourceX = reverseX ? sw - 1 - ox : ox;
                        int pixel = pixelScratch[sourceRow + sourceX];
                        fillScratch[outRow + ox] = prepareImagePixel(
                                pixel, imageAlpha, colourKey, transparent, dither,
                                sx + sourceX, sourceY);
                    }
                }
            } else {
                boolean reverseSourceX = flipMode == FLIP_ROTATE_LEFT
                        || flipMode == FLIP_ROTATE_RIGHT_VERTICAL;
                boolean reverseSourceY = flipMode == FLIP_ROTATE_RIGHT
                        || flipMode == FLIP_ROTATE_RIGHT_VERTICAL;
                int readX = reverseSourceX ? sx + sw - outY - bh : sx + outY;
                src.getRGB(pixelScratch, 0, bh, readX, sy, bh, sh);
                for (int ry = 0; ry < bh; ry++) {
                    int sourceColumn = reverseSourceX ? bh - 1 - ry : ry;
                    int outRow = ry * outW;
                    int sourceX = reverseSourceX ? sx + sw - 1 - (outY + ry) : sx + outY + ry;
                    for (int ox = 0; ox < outW; ox++) {
                        int sourceY = reverseSourceY ? sh - 1 - ox : ox;
                        int pixel = pixelScratch[sourceY * bh + sourceColumn];
                        fillScratch[outRow + ox] = prepareImagePixel(
                                pixel, imageAlpha, colourKey, transparent, dither,
                                sourceX, sy + sourceY);
                    }
                }
            }
            drawPreparedImagePixels(fillScratch, 0, outW, dx, dy + outY, outW, bh,
                    nativeRenderFastPath && imageAlpha < 255);
        }
    }

    private void drawScaledBuffered(javax.microedition.lcdui.Image src,
            int dx, int dy, int sx, int sy, int sw, int sh, int dw, int dh,
            int imageAlpha, boolean colourKey, int transparent, boolean rotated) {
        int sourceCount = sw * sh;
        ensurePixelScratch(sourceCount);
        src.getRGB(pixelScratch, 0, sw, sx, sy, sw, sh);
        boolean dither = sourceOverAlphaPath && imageAlpha < 255 && Image.shouldDitherAlpha();

        int outW = rotated ? dh : dw;
        int outH = rotated ? dw : dh;
        int rowsPerBlock = COMPOSITE_PIXELS / outW;
        if (rowsPerBlock < 1) rowsPerBlock = 1;

        for (int outY = 0; outY < outH; outY += rowsPerBlock) {
            int bh = outH - outY;
            if (bh > rowsPerBlock) bh = rowsPerBlock;
            int count = outW * bh;
            ensureScratch(count);
            for (int ry = 0; ry < bh; ry++) {
                int gy = outY + ry;
                int outRow = ry * outW;
                if (!rotated) {
                    int preY = (flipMode == FLIP_VERTICAL || flipMode == FLIP_ROTATE)
                            ? dh - 1 - gy : gy;
                    int sourceY = scaleMapY[preY];
                    for (int ox = 0; ox < outW; ox++) {
                        int preX = (flipMode == FLIP_HORIZONTAL || flipMode == FLIP_ROTATE)
                                ? dw - 1 - ox : ox;
                        int sourceX = scaleMapX[preX];
                        int pixel = pixelScratch[sourceY * sw + sourceX];
                        fillScratch[outRow + ox] = prepareImagePixel(
                                pixel, imageAlpha, colourKey, transparent, dither,
                                sx + sourceX, sy + sourceY);
                    }
                } else {
                    int preX;
                    switch (flipMode) {
                        case FLIP_ROTATE_LEFT:
                        case FLIP_ROTATE_RIGHT_VERTICAL:
                            preX = dw - 1 - gy; break;
                        default:
                            preX = gy; break;
                    }
                    int sourceX = scaleMapX[preX];
                    for (int ox = 0; ox < outW; ox++) {
                        int preY;
                        switch (flipMode) {
                            case FLIP_ROTATE_RIGHT:
                            case FLIP_ROTATE_RIGHT_VERTICAL:
                                preY = dh - 1 - ox; break;
                            default:
                                preY = ox; break;
                        }
                        int sourceY = scaleMapY[preY];
                        int pixel = pixelScratch[sourceY * sw + sourceX];
                        fillScratch[outRow + ox] = prepareImagePixel(
                                pixel, imageAlpha, colourKey, transparent, dither,
                                sx + sourceX, sy + sourceY);
                    }
                }
            }
            drawPreparedImagePixels(fillScratch, 0, outW, dx, dy + outY, outW, bh,
                    nativeRenderFastPath && imageAlpha < 255);
        }
    }

    /**
     * 一般繪圖的 framebuffer 半透明合成器。互補 OP_ADD 不會進入此方法；
     * 它使用 drawNativeStreaming 的 source-only 路徑。
     */
    private void drawUnscaledAlphaImage(javax.microedition.lcdui.Image src, Image image,
            int dx, int dy, int sx, int sy, int width, int height, int imageAlpha) {
        int clipX = midpGraphics.getClipX();
        int clipY = midpGraphics.getClipY();
        int clipR = clipX + midpGraphics.getClipWidth();
        int clipB = clipY + midpGraphics.getClipHeight();
        int left = dx > clipX ? dx : clipX;
        int top = dy > clipY ? dy : clipY;
        int right = dx + width < clipR ? dx + width : clipR;
        int bottom = dy + height < clipB ? dy + height : clipB;
        int physL = left + originX;
        int physT = top + originY;
        if (physL < 0) { left -= physL; physL = 0; }
        if (physT < 0) { top -= physT; physT = 0; }
        if (right + originX > screenWidth) right = screenWidth - originX;
        if (bottom + originY > screenHeight) bottom = screenHeight - originY;
        if (left >= right || top >= bottom) return;

        boolean colourKey = image.isTransparentEnabled();
        int transparent = colourKey ? (image.getTransparentColor() & 0x00FFFFFF) : 0;
        prepareImageAlphaLuts(imageAlpha);

        int blockW = right - left;
        if (blockW > COMPOSITE_PIXELS) blockW = COMPOSITE_PIXELS;
        int rowsPerBlock = COMPOSITE_PIXELS / blockW;
        if (rowsPerBlock < 1) rowsPerBlock = 1;
        int maxRows = bottom - top;
        if (maxRows > rowsPerBlock) maxRows = rowsPerBlock;
        int maxCount = blockW * maxRows;
        ensurePixelScratch(maxCount);
        ensureBlendScratch(maxCount);

        for (int by = top; by < bottom; by += rowsPerBlock) {
            int bh = bottom - by;
            if (bh > rowsPerBlock) bh = rowsPerBlock;
            for (int bx = left; bx < right; bx += blockW) {
                int bw = right - bx;
                if (bw > blockW) bw = blockW;
                int srcX = sx + (bx - dx);
                int srcY = sy + (by - dy);
                src.getRGB(pixelScratch, 0, bw, srcX, srcY, bw, bh);
                backBuffer.getRGB(blendScratch, 0, bw, bx + originX, by + originY, bw, bh);
                compositeUniformAlphaTile(pixelScratch, blendScratch, bw * bh,
                        imageAlpha, colourKey, transparent);
                midpGraphics.drawRGB(blendScratch, 0, bw, bx, by, bw, bh, false);
            }
        }
    }

    /**
     * 對已準備好的 ARGB 像素繪製。互補 OP_ADD 直接使用 MIDP 的
     * Source-over drawRGB；其他需要目的像素的模式才進入軟體合成。
     */
    private void drawPreparedImagePixels(int[] rgb, int offset, int scanlength,
            int x, int y, int width, int height, boolean forceSoftwareAlpha) {
        if (sourceOverAlphaPath) {
            midpGraphics.drawRGB(rgb, offset, scanlength, x, y, width, height, true);
            return;
        }
        if (!forceSoftwareAlpha) {
            drawRGBComposite(rgb, offset, scanlength, x, y, width, height, true);
            return;
        }
        drawARGBSourceOverSoftware(rgb, offset, scanlength, x, y, width, height);
    }

    private void drawARGBSourceOverSoftware(int[] source, int offset, int scanlength,
            int x, int y, int width, int height) {
        int clipX = midpGraphics.getClipX();
        int clipY = midpGraphics.getClipY();
        int clipR = clipX + midpGraphics.getClipWidth();
        int clipB = clipY + midpGraphics.getClipHeight();
        int left = x > clipX ? x : clipX;
        int top = y > clipY ? y : clipY;
        int right = x + width < clipR ? x + width : clipR;
        int bottom = y + height < clipB ? y + height : clipB;
        int physL = left + originX;
        int physT = top + originY;
        if (physL < 0) { left -= physL; physL = 0; }
        if (physT < 0) { top -= physT; physT = 0; }
        if (right + originX > screenWidth) right = screenWidth - originX;
        if (bottom + originY > screenHeight) bottom = screenHeight - originY;
        if (left >= right || top >= bottom) return;

        int blockW = right - left;
        if (blockW > COMPOSITE_PIXELS) blockW = COMPOSITE_PIXELS;
        int rowsPerBlock = COMPOSITE_PIXELS / blockW;
        if (rowsPerBlock < 1) rowsPerBlock = 1;
        int maxRows = bottom - top;
        if (maxRows > rowsPerBlock) maxRows = rowsPerBlock;
        ensureBlendScratch(blockW * maxRows);

        for (int by = top; by < bottom; by += rowsPerBlock) {
            int bh = bottom - by;
            if (bh > rowsPerBlock) bh = rowsPerBlock;
            for (int bx = left; bx < right; bx += blockW) {
                int bw = right - bx;
                if (bw > blockW) bw = blockW;
                backBuffer.getRGB(blendScratch, 0, bw, bx + originX, by + originY, bw, bh);
                compositeARGBTile(source, offset, scanlength, x, y, bx, by, bw, bh);
                midpGraphics.drawRGB(blendScratch, 0, bw, bx, by, bw, bh, false);
            }
        }
    }

    private void compositeUniformAlphaTile(int[] source, int[] destination, int count,
            int imageAlpha, boolean colourKey, int transparent) {
        int[] srcLut = imageAlphaSourceLut;
        int[] dstLut = imageAlphaDestinationLut;
        for (int i = 0; i < count; i++) {
            int src = source[i];
            int srcAlpha = (src >>> 24) & 0xFF;
            if (srcAlpha == 0 || (colourKey && (src & 0x00FFFFFF) == transparent)) continue;
            int dst = destination[i];
            if (srcAlpha == 255) {
                int r = div255Round(srcLut[(src >>> 16) & 0xFF] + dstLut[(dst >>> 16) & 0xFF]);
                int g = div255Round(srcLut[(src >>> 8) & 0xFF] + dstLut[(dst >>> 8) & 0xFF]);
                int b = div255Round(srcLut[src & 0xFF] + dstLut[dst & 0xFF]);
                destination[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
            } else {
                int alpha = div255Round(multiplyU8(srcAlpha, imageAlpha));
                destination[i] = sourceOverPixel(src, dst, alpha);
            }
        }
    }

    private void compositeARGBTile(int[] source, int offset, int scanlength,
            int drawX, int drawY, int tileX, int tileY, int width, int height) {
        for (int row = 0; row < height; row++) {
            int srcRow = offset + (tileY + row - drawY) * scanlength + (tileX - drawX);
            int dstRow = row * width;
            for (int col = 0; col < width; col++) {
                int src = source[srcRow + col];
                int alpha = (src >>> 24) & 0xFF;
                if (alpha == 0) continue;
                int index = dstRow + col;
                if (alpha == 255) blendScratch[index] = 0xFF000000 | (src & 0x00FFFFFF);
                else blendScratch[index] = sourceOverPixel(src, blendScratch[index], alpha);
            }
        }
    }

    private static int sourceOverPixel(int src, int dst, int alpha) {
        if (alpha <= 0) return dst;
        if (alpha >= 255) return 0xFF000000 | (src & 0x00FFFFFF);
        int inv = 255 - alpha;
        int r = div255Round(multiplyU8((src >>> 16) & 0xFF, alpha)
                + multiplyU8((dst >>> 16) & 0xFF, inv));
        int g = div255Round(multiplyU8((src >>> 8) & 0xFF, alpha)
                + multiplyU8((dst >>> 8) & 0xFF, inv));
        int b = div255Round(multiplyU8(src & 0xFF, alpha)
                + multiplyU8(dst & 0xFF, inv));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void prepareImageAlphaLuts(int alpha) {
        if (imageAlphaLutValue == alpha) return;
        if (imageAlphaSourceLut == null) imageAlphaSourceLut = new int[256];
        if (imageAlphaDestinationLut == null) imageAlphaDestinationLut = new int[256];
        int inv = 255 - alpha;
        for (int i = 0; i < 256; i++) {
            imageAlphaSourceLut[i] = multiplyU8(i, alpha);
            imageAlphaDestinationLut[i] = multiplyU8(i, inv);
        }
        imageAlphaLutValue = alpha;
    }

    private void prepareScaleMaps(int destinationWidth, int destinationHeight,
            int sourceWidth, int sourceHeight) {
        if (scaleMapX == null || scaleMapX.length < destinationWidth) scaleMapX = new int[destinationWidth];
        if (scaleMapY == null || scaleMapY.length < destinationHeight) scaleMapY = new int[destinationHeight];
        fillScaleMap(scaleMapX, destinationWidth, sourceWidth);
        fillScaleMap(scaleMapY, destinationHeight, sourceHeight);
    }

    /** floor(i * source / destination), using only two divisions for the entire axis. */
    private static void fillScaleMap(int[] map, int destination, int source) {
        int step = source / destination;
        int remainderStep = source % destination;
        int sourceIndex = 0;
        int remainder = 0;
        for (int i = 0; i < destination; i++) {
            map[i] = sourceIndex;
            sourceIndex += step;
            remainder += remainderStep;
            if (remainder >= destination) {
                remainder -= destination;
                sourceIndex++;
            }
        }
    }

    protected int prepareImagePixel(Image image, int pixel) {
        if (image == null) return 0;
        return applyImageAlpha(pixel, image.getAlpha(), image.isTransparentEnabled(),
                (image.isTransparentEnabled() ? image.getTransparentColor() : 0) & 0x00FFFFFF);
    }


    private static int applyImageAlpha(int pixel, int imageAlpha,
            boolean colourKey, int transparent) {
        int rgb = pixel & 0x00ffffff;
        int alpha = (pixel >>> 24) & 0xff;
        if (colourKey && rgb == transparent) alpha = 0;
        if (imageAlpha < 255) alpha = div255Round(multiplyU8(alpha, imageAlpha));
        return rgb | (alpha << 24);
    }

    private static int prepareImagePixel(int pixel, int imageAlpha,
            boolean colourKey, int transparent, boolean dither, int sourceX, int sourceY) {
        int prepared = applyImageAlpha(pixel, imageAlpha, colourKey, transparent);
        return dither ? Image.ditherAlphaPixel(prepared, sourceX, sourceY) : prepared;
    }

    private void ensurePixelScratch(int size) {
        if (pixelScratch == null || pixelScratch.length < size) {
            pixelScratch = new int[size];
        }
    }

    private static int toMIDPTransform(int mode) {
        switch (mode) {
            case FLIP_HORIZONTAL: return 2;              // MIDP TRANS_MIRROR
            case FLIP_VERTICAL: return 1;                // MIDP TRANS_MIRROR_ROT180
            case FLIP_ROTATE: return 3;                  // MIDP TRANS_ROT180
            case FLIP_ROTATE_LEFT: return 6;             // MIDP TRANS_ROT270
            case FLIP_ROTATE_RIGHT: return 5;            // MIDP TRANS_ROT90
            case FLIP_ROTATE_RIGHT_HORIZONTAL: return 4; // MIDP TRANS_MIRROR_ROT270
            case FLIP_ROTATE_RIGHT_VERTICAL: return 7;   // MIDP TRANS_MIRROR_ROT90
            default: return 0;                           // MIDP TRANS_NONE
        }
    }

    private static boolean isRotated(int mode) {
        return mode == FLIP_ROTATE_LEFT || mode == FLIP_ROTATE_RIGHT || mode == FLIP_ROTATE_RIGHT_HORIZONTAL || mode == FLIP_ROTATE_RIGHT_VERTICAL;
    }

    public void drawString(String str, int x, int y) {
        ensureSurface();
        if (str == null) str = "";
        if (BitmapFont.isLoaded()) {
            int alpha = getEffectiveAlpha(255);
            int argb = (currentARGB & 0x00FFFFFF) | (alpha << 24);
            if (BitmapFont.drawString(this, str, x, y, argb, currentFont)) {
                return;
            }
        }
        int alpha = getEffectiveAlpha(255);
        if (canUseNativeSolid(currentARGB) && alpha == 255) {
            int yy = y;
            if (currentFont != null) yy += currentFont.getBaselineShift();
            beginNativeSolid(currentARGB);
            midpGraphics.drawString(str, x, yy,
                    javax.microedition.lcdui.Graphics.BASELINE | javax.microedition.lcdui.Graphics.LEFT);
            endNativeSolid();
            return;
        }
        drawNativeTextComposite(str, x, y, alpha);
    }

    /** 先把 MIDP 字型畫成單色 mask，再進行 DoJa 混合；複合模式因此和圖片路徑一致。 */
    private void drawNativeTextComposite(String str, int x, int y, int alpha) {
        if (str.length() == 0) return;
        javax.microedition.lcdui.Font mf = currentFont == null
                ? javax.microedition.lcdui.Font.getDefaultFont() : currentFont.getMIDPFont();
        int w = mf.stringWidth(str);
        int h = mf.getHeight();
        if (w <= 0 || h <= 0) return;
        ensureTextMask(w, h);
        textMaskGraphics.setColor(0x000000);
        textMaskGraphics.fillRect(0, 0, textMaskWidth, textMaskHeight);
        textMaskGraphics.setFont(mf);
        textMaskGraphics.setColor(0xFFFFFF);
        int baseline = mf.getBaselinePosition();
        textMaskGraphics.drawString(str, 0, baseline,
                javax.microedition.lcdui.Graphics.BASELINE | javax.microedition.lcdui.Graphics.LEFT);
        int count = w * h;
        if (textMaskPixels == null || textMaskPixels.length < count) textMaskPixels = new int[count];
        textMaskImage.getRGB(textMaskPixels, 0, w, 0, 0, w, h);
        int rgb = currentARGB & 0x00FFFFFF;
        for (int i = 0; i < count; i++) {
            int p = textMaskPixels[i];
            int coverage = (((p >>> 16) & 255) + ((p >>> 8) & 255) + (p & 255)) / 3;
            textMaskPixels[i] = rgb | (((multiplyU8(coverage, alpha) + 127) / 255) << 24);
        }
        int baselineShift = currentFont == null ? 0 : currentFont.getBaselineShift();
        drawRGBComposite(textMaskPixels, 0, w, x, y + baselineShift - baseline, w, h, true);
    }

    private void ensureTextMask(int w, int h) {
        if (textMaskImage != null && textMaskWidth >= w && textMaskHeight >= h) return;
        textMaskWidth = w;
        textMaskHeight = h;
        textMaskImage = javax.microedition.lcdui.Image.createImage(w, h);
        textMaskGraphics = textMaskImage.getGraphics();
    }

    public void drawChars(char[] data, int x, int y, int off, int len) {
        if (data == null || len <= 0) return;
        drawString(new String(data, off, len), x, y);
    }

    public void setPictoColorEnabled(boolean b) {
        /* DoJa 裝置間的 pictogram 顏色不同，MIDP 沿用目前文字色即可。 */
    }

    public void drawLine(int x1, int y1, int x2, int y2) {
        ensureSurface();
        if (canUseNativeSolid(currentARGB)) {
            beginNativeSolid(currentARGB);
            midpGraphics.drawLine(x1, y1, x2, y2);
            endNativeSolid();
            return;
        }
        rasterLine(x1, y1, x2, y2, currentARGB);
    }

    public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints) {
        drawPolyline(xPoints, yPoints, 0, nPoints);
    }

    public void drawPolyline(int[] xPoints, int[] yPoints, int offset, int count) {
        ensureSurface();
        if (xPoints == null || yPoints == null || count < 2) return;
        for (int i = offset; i < offset + count - 1; i++) {
            drawLine(xPoints[i], yPoints[i], xPoints[i + 1], yPoints[i + 1]);
        }
    }

    public void drawRect(int x, int y, int w, int h) {
        ensureSurface();
        if (w < 0 || h < 0) return;
        if (canUseNativeSolid(currentARGB)) {
            beginNativeSolid(currentARGB);
            midpGraphics.drawRect(x, y, w, h);
            endNativeSolid();
            return;
        }
        drawLine(x, y, x + w, y);
        if (h != 0) drawLine(x, y + h, x + w, y + h);
        if (h > 1) {
            drawLine(x, y + 1, x, y + h - 1);
            if (w != 0) drawLine(x + w, y + 1, x + w, y + h - 1);
        }
    }

    public void fillRect(int x, int y, int w, int h) {
        ensureSurface();
        if (w <= 0 || h <= 0) return;
        int alpha = (currentARGB >>> 24) & 0xFF;
        if (canUseNativeSolid(currentARGB)) {
            beginNativeSolid(currentARGB);
            midpGraphics.fillRect(x, y, w, h);
            endNativeSolid();
            return;
        }
        fillSolid(x, y, w, h, currentARGB);
    }

    public void clearRect(int x, int y, int w, int h) {
        ensureSurface();
        if (w <= 0 || h <= 0) return;
        int background = parentCanvas == null
                ? getColorOfName(BLACK) : parentCanvas.getBackground();
        if (nativeRenderFastPath) {
            int old = currentARGB;
            setColor(background);
            midpGraphics.fillRect(x, y, w, h);
            setColor(old);
        } else {
            fillSolid(x, y, w, h, background);
        }
    }

    public void copyArea(int sx, int sy, int width, int height, int dx, int dy) {
        ensureSurface();
        if (width <= 0 || height <= 0) return;
        int dstX = sx + dx;
        int dstY = sy + dy;
        if (nativeRenderFastPath) {
            try {
                midpGraphics.copyArea(sx, sy, width, height, dstX, dstY,
                    javax.microedition.lcdui.Graphics.TOP | javax.microedition.lcdui.Graphics.LEFT);
                return;
            } catch (Throwable ignored) {
                // 裝置端轉換失敗時，下面的 software path 會給出同一套結果。
            }
        }
        copyAreaSoftware(sx, sy, width, height, dstX, dstY);
    }

    public void setPixel(int x, int y) {
        ensureSurface();
        rasterPixel(x, y, currentARGB);
    }

    public void setPixel(int x, int y, int color) {
        int old = currentARGB;
        setColor(color);
        setPixel(x, y);
        setColor(old);
    }

    public void setRGBPixel(int x, int y, int pixel) {
        ensureSurface();
        rasterPixel(x, y, 0xFF000000 | (pixel & 0x00FFFFFF));
    }

    public int getPixel(int x, int y) {
        ensureSurface();
        if (pixelScratch == null) pixelScratch = new int[1];
        if (backBuffer == null) return 0;
        backBuffer.getRGB(pixelScratch, 0, 1, x + originX, y + originY, 1, 1);
        return pixelScratch[0];
    }

    public int getRGBPixel(int x, int y) {
        return getPixel(x, y) & 0x00FFFFFF;
    }

    public int[] getPixels(int x, int y, int width, int height, int[] pixels, int off) {
        ensureSurface();
        if (pixels == null) {
            pixels = new int[off + width * height];
        }
        if (backBuffer == null) return pixels;
        backBuffer.getRGB(pixels, off, width, x + originX, y + originY, width, height);
        return pixels;
    }

    public int[] getRGBPixels(int x, int y, int width, int height, int[] pixels, int off) {
        pixels = getPixels(x, y, width, height, pixels, off);
        int i;
        int count = width * height;
        for (i = 0; i < count; i++) {
            pixels[off + i] &= 0x00FFFFFF;
        }
        return pixels;
    }

    public void setPixels(int x, int y, int width, int height, int[] pixels, int off) {
        ensureSurface();
        if (pixels == null || width <= 0 || height <= 0) return;
        drawRGBComposite(pixels, off, width, x, y, width, height, true);
    }

    public void setRGBPixels(int x, int y, int width, int height, int[] pixels, int off) {
        ensureSurface();
        if (pixels == null || width <= 0 || height <= 0) return;
        drawRGBComposite(pixels, off, width, x, y, width, height, false);
    }

    public void drawRGB(int[] rgb, int offset, int scanlength, int x, int y, int width, int height, boolean processAlpha) {
        ensureSurface();
        drawRGBComposite(rgb, offset, scanlength, x, y, width, height, processAlpha);
    }

    protected void drawRGBComposite(int[] rgb, int offset, int scanlength, int x, int y, int width, int height, boolean processAlpha) {
        ensureSurface();
        if (rgb == null || width <= 0 || height <= 0) return;
        if (nativeRenderFastPath) {
            midpGraphics.drawRGB(rgb, offset, scanlength, x, y, width, height, processAlpha);
            return;
        }
        if (renderMode == 0) {
            drawRGBReplaceScaled(rgb, offset, scanlength, x, y, width, height, processAlpha);
            return;
        }
        prepareRatioLuts();
        int clipX = midpGraphics.getClipX();
        int clipY = midpGraphics.getClipY();
        int clipR = clipX + midpGraphics.getClipWidth();
        int clipB = clipY + midpGraphics.getClipHeight();
        int left = x > clipX ? x : clipX;
        int top = y > clipY ? y : clipY;
        int right = x + width < clipR ? x + width : clipR;
        int bottom = y + height < clipB ? y + height : clipB;
        int physL = left + originX;
        int physT = top + originY;
        if (physL < 0) { left -= physL; physL = 0; }
        if (physT < 0) { top -= physT; physT = 0; }
        if (right + originX > screenWidth) right = screenWidth - originX;
        if (bottom + originY > screenHeight) bottom = screenHeight - originY;
        if (left >= right || top >= bottom) return;


        /* 採用矩形 Tile 批次讀寫目標畫布，大幅降低逐列頻繁掃描 Java/MIDP 邊界的成本；
         * 單一 Tile 的尺寸上限維持在 4096 PX，避免增加 JVM 開銷。 */
        int blockW = right - left;
        if (blockW > COMPOSITE_PIXELS) blockW = COMPOSITE_PIXELS;
        int rowsPerBlock = COMPOSITE_PIXELS / blockW;
        if (rowsPerBlock < 1) rowsPerBlock = 1;
        int maxRows = bottom - top;
        if (maxRows > rowsPerBlock) maxRows = rowsPerBlock;
        ensureBlendScratch(blockW * maxRows);

        for (int by = top; by < bottom; by += rowsPerBlock) {
            int bh = bottom - by;
            if (bh > rowsPerBlock) bh = rowsPerBlock;
            for (int bx = left; bx < right; bx += blockW) {
                int bw = right - bx;
                if (bw > blockW) bw = blockW;
                int px = bx + originX;
                backBuffer.getRGB(blendScratch, 0, bw, px, by + originY, bw, bh);
                if (renderMode == 1) {
                    compositeAddTile(rgb, offset, scanlength, x, y, bx, by, bw, bh, processAlpha);
                } else {
                    compositeSubTile(rgb, offset, scanlength, x, y, bx, by, bw, bh, processAlpha);
                }
                midpGraphics.drawRGB(blendScratch, 0, bw, bx, by, bw, bh, false);
            }
        }
    }

    private void compositeAddTile(int[] source, int offset, int scanlength,
            int drawX, int drawY, int tileX, int tileY, int width, int height, boolean processAlpha) {
        for (int row = 0; row < height; row++) {
            int srcRow = offset + (tileY + row - drawY) * scanlength + (tileX - drawX);
            int dstRow = row * width;
            for (int col = 0; col < width; col++) {
                int src = source[srcRow + col];
                int alpha = processAlpha ? (src >>> 24) & 0xFF : 255;
                if (alpha == 0) continue;
                int index = dstRow + col;
                int dst = blendScratch[index];
                int dr = (dst >>> 16) & 0xFF;
                int dg = (dst >>> 8) & 0xFF;
                int db = dst & 0xFF;
                int n = sourceRatioLut[(src >>> 16) & 0xFF] + destinationRatioLut[dr];
                int rr = n >= 65025 ? 255 : div255Floor(n);
                n = sourceRatioLut[(src >>> 8) & 0xFF] + destinationRatioLut[dg];
                int rg = n >= 65025 ? 255 : div255Floor(n);
                n = sourceRatioLut[src & 0xFF] + destinationRatioLut[db];
                int rb = n >= 65025 ? 255 : div255Floor(n);
                if (alpha < 255) {
                    int inv = 255 - alpha;
                    rr = div255Round(multiplyU8(rr, alpha) + multiplyU8(dr, inv));
                    rg = div255Round(multiplyU8(rg, alpha) + multiplyU8(dg, inv));
                    rb = div255Round(multiplyU8(rb, alpha) + multiplyU8(db, inv));
                }
                blendScratch[index] = 0xFF000000 | (rr << 16) | (rg << 8) | rb;
            }
        }
    }

    private void compositeSubTile(int[] source, int offset, int scanlength,
            int drawX, int drawY, int tileX, int tileY, int width, int height, boolean processAlpha) {
        for (int row = 0; row < height; row++) {
            int srcRow = offset + (tileY + row - drawY) * scanlength + (tileX - drawX);
            int dstRow = row * width;
            for (int col = 0; col < width; col++) {
                int src = source[srcRow + col];
                int alpha = processAlpha ? (src >>> 24) & 0xFF : 255;
                if (alpha == 0) continue;
                int index = dstRow + col;
                int dst = blendScratch[index];
                int dr = (dst >>> 16) & 0xFF;
                int dg = (dst >>> 8) & 0xFF;
                int db = dst & 0xFF;
                int n = destinationRatioLut[dr] - sourceRatioLut[(src >>> 16) & 0xFF];
                int rr = n <= 0 ? 0 : div255Floor(n);
                n = destinationRatioLut[dg] - sourceRatioLut[(src >>> 8) & 0xFF];
                int rg = n <= 0 ? 0 : div255Floor(n);
                n = destinationRatioLut[db] - sourceRatioLut[src & 0xFF];
                int rb = n <= 0 ? 0 : div255Floor(n);
                if (alpha < 255) {
                    int inv = 255 - alpha;
                    rr = div255Round(multiplyU8(rr, alpha) + multiplyU8(dr, inv));
                    rg = div255Round(multiplyU8(rg, alpha) + multiplyU8(dg, inv));
                    rb = div255Round(multiplyU8(rb, alpha) + multiplyU8(db, inv));
                }
                blendScratch[index] = 0xFF000000 | (rr << 16) | (rg << 8) | rb;
            }
        }
    }

    /**
     * OP_REPL does not depend on the destination.  For a non-255 source ratio, scale the source
     * into the fixed scratch buffer and let MIDP perform the ordinary source-alpha write.  This
     * avoids the expensive framebuffer read required by ADD/SUB.
     */
    private void drawRGBReplaceScaled(int[] rgb, int offset, int scanlength,
            int x, int y, int width, int height, boolean processAlpha) {
        prepareRatioLuts();
        int maxW = width < COMPOSITE_PIXELS ? width : COMPOSITE_PIXELS;
        ensureScratch(maxW);
        for (int row = 0; row < height; row++) {
            int srcRow = offset + row * scanlength;
            for (int bx = 0; bx < width; bx += maxW) {
                int bw = width - bx;
                if (bw > maxW) bw = maxW;
                for (int i = 0; i < bw; i++) {
                    int p = rgb[srcRow + bx + i];
                    int a = processAlpha ? (p >>> 24) & 0xFF : 255;
                    if (a == 0) {
                        fillScratch[i] = 0;
                    } else {
                        int r = sourceScaleLut[(p >>> 16) & 0xFF];
                        int g = sourceScaleLut[(p >>> 8) & 0xFF];
                        int b = sourceScaleLut[p & 0xFF];
                        fillScratch[i] = (a << 24) | (r << 16) | (g << 8) | b;
                    }
                }
                midpGraphics.drawRGB(fillScratch, 0, bw, x + bx, y + row, bw, 1, true);
            }
        }
    }

    private int rasterPixelValue(int src, int dst) {
        if (renderMode != 0 || srcRatio != 255) prepareRatioLuts();
        return rasterPixelValuePrepared(src, dst);
    }

    private int rasterPixelValuePrepared(int src, int dst) {
        int a = (src >>> 24) & 0xFF;
        if (a == 0) return dst;

        int sr = (src >>> 16) & 0xFF;
        int sg = (src >>> 8) & 0xFF;
        int sb = src & 0xFF;

        if (renderMode == 0) {
            if (srcRatio != 255) {
                sr = sourceScaleLut[sr];
                sg = sourceScaleLut[sg];
                sb = sourceScaleLut[sb];
            }
            if (a >= 255) return 0xFF000000 | (sr << 16) | (sg << 8) | sb;
            int inv = 255 - a;
            int r = div255Round(multiplyU8(sr, a) + multiplyU8((dst >>> 16) & 0xFF, inv));
            int g = div255Round(multiplyU8(sg, a) + multiplyU8((dst >>> 8) & 0xFF, inv));
            int b = div255Round(multiplyU8(sb, a) + multiplyU8(dst & 0xFF, inv));
            return 0xFF000000 | (r << 16) | (g << 8) | b;
        }

        int dr = (dst >>> 16) & 0xFF;
        int dg = (dst >>> 8) & 0xFF;
        int db = dst & 0xFF;
        int rr = rasterChannel(sr, dr);
        int rg = rasterChannel(sg, dg);
        int rb = rasterChannel(sb, db);
        if (a < 255) {
            int inv = 255 - a;
            rr = div255Round(multiplyU8(rr, a) + multiplyU8(dr, inv));
            rg = div255Round(multiplyU8(rg, a) + multiplyU8(dg, inv));
            rb = div255Round(multiplyU8(rb, a) + multiplyU8(db, inv));
        }
        return 0xFF000000 | (rr << 16) | (rg << 8) | rb;
    }

    private int rasterChannel(int source, int destination) {
        int n;
        if (renderMode == 1) {
            n = sourceRatioLut[source] + destinationRatioLut[destination];
            if (n >= 65025) return 255;
            return div255Floor(n);
        }
        n = destinationRatioLut[destination] - sourceRatioLut[source];
        if (n <= 0) return 0;
        return div255Floor(n);
    }

    private void prepareRatioLuts() {
        if (sourceRatioLut == null) sourceRatioLut = new int[256];
        if (sourceScaleLut == null) sourceScaleLut = new int[256];
        if (destinationRatioLut == null) destinationRatioLut = new int[256];
        if (ratioLutSource != srcRatio) {
            for (int i = 0; i < 256; i++) {
                int product = multiplyU8(i, srcRatio);
                sourceRatioLut[i] = product;
                sourceScaleLut[i] = div255Floor(product);
            }
            ratioLutSource = srcRatio;
        }
        if (ratioLutDestination != dstRatio) {
            for (int i = 0; i < 256; i++) destinationRatioLut[i] = multiplyU8(i, dstRatio);
            ratioLutDestination = dstRatio;
        }
    }

    /** 8-bit channel/ratio product; maximum 65025 is comfortably within Java int. */
    protected static int multiplyU8(int value, int factor) {
        return (value & 0xFF) * (factor & 0xFF);
    }

    /** Exact floor(value / 255) for 0..65025 without integer division. */
    protected static int div255Floor(int value) {
        int t = value + 1;
        return (t + (t >> 8)) >> 8;
    }

    /** Round(value / 255) to nearest for 0..65025 without integer division. */
    private static int div255Round(int value) {
        return div255Floor(value + 127);
    }

    protected static int scaleU8(int value, int ratio) {
        return div255Floor(multiplyU8(value, ratio));
    }

    private void rasterPixel(int x, int y, int source) {
        int clipX = midpGraphics.getClipX();
        int clipY = midpGraphics.getClipY();
        if (x < clipX || y < clipY || x >= clipX + midpGraphics.getClipWidth() || y >= clipY + midpGraphics.getClipHeight()) return;
        int px = x + originX;
        int py = y + originY;
        if (px < 0 || py < 0 || px >= screenWidth || py >= screenHeight) return;
        if (canUseNativeSolid(source)) {
            ensureScratch(1);
            fillScratch[0] = 0xFF000000 | nativeSolidRGB(source);
            midpGraphics.drawRGB(fillScratch, 0, 1, x, y, 1, 1, false);
            return;
        }
        ensureBlendScratch(1);
        backBuffer.getRGB(blendScratch, 0, 1, px, py, 1, 1);
        blendScratch[0] = rasterPixelValue(source, blendScratch[0]);
        midpGraphics.drawRGB(blendScratch, 0, 1, x, y, 1, 1, false);
    }

    /**
     * 處理單色半透明填滿（常用於 DoJa 遊戲的全螢幕 淡入/淡出 或 色彩遮罩）。
     * 優化策略：
     * 1. 使用 3 x 256 的預算查找表（LUT）處理 RGB 三通道，避免每個像素重複執行 6 次乘法與 Clamp 邊界檢查。
     * 2. 僅在顏色或渲染狀態改變時才重新建構查找表，避免浪費。
     * 3. 採用分塊批次讀寫像素，避免記憶體開銷過大。
     */
    private void fillSolid(int x, int y, int w, int h, int source) {
        if (w <= 0 || h <= 0) return;
        int sourceAlpha = (source >>> 24) & 0xFF;
        if (sourceAlpha == 0) return;
        if (canUseNativeSolid(source)) {
            int restore = currentARGB & 0x00FFFFFF;
            midpGraphics.setColor(nativeSolidRGB(source));
            midpGraphics.fillRect(x, y, w, h);
            midpGraphics.setColor(restore);
            return;
        }

        int clipX = midpGraphics.getClipX();
        int clipY = midpGraphics.getClipY();
        int clipR = clipX + midpGraphics.getClipWidth();
        int clipB = clipY + midpGraphics.getClipHeight();
        int left = x > clipX ? x : clipX;
        int top = y > clipY ? y : clipY;
        int right = x + w < clipR ? x + w : clipR;
        int bottom = y + h < clipB ? y + h : clipB;
        int physL = left + originX;
        int physT = top + originY;
        if (physL < 0) { left -= physL; physL = 0; }
        if (physT < 0) { top -= physT; physT = 0; }
        if (right + originX > screenWidth) right = screenWidth - originX;
        if (bottom + originY > screenHeight) bottom = screenHeight - originY;
        if (left >= right || top >= bottom) return;

        prepareSolidCompositeLut(source);

        int blockW = right - left;
        if (blockW > SOLID_COMPOSITE_PIXELS) blockW = SOLID_COMPOSITE_PIXELS;
        int rowsPerBlock = SOLID_COMPOSITE_PIXELS / blockW;
        if (rowsPerBlock < 1) rowsPerBlock = 1;
        int maxRows = bottom - top;
        if (maxRows > rowsPerBlock) maxRows = rowsPerBlock;
        ensureBlendScratch(blockW * maxRows);

        for (int by = top; by < bottom; by += rowsPerBlock) {
            int bh = bottom - by;
            if (bh > rowsPerBlock) bh = rowsPerBlock;
            for (int bx = left; bx < right; bx += blockW) {
                int bw = right - bx;
                if (bw > blockW) bw = blockW;
                int count = bw * bh;
                backBuffer.getRGB(blendScratch, 0, bw, bx + originX, by + originY, bw, bh);
                for (int i = 0; i < count; i++) {
                    int dst = blendScratch[i];
                    blendScratch[i] = solidCompositeLut[(dst >>> 16) & 0xFF]
                            | solidCompositeLut[256 + ((dst >>> 8) & 0xFF)]
                            | solidCompositeLut[512 + (dst & 0xFF)];
                }
                midpGraphics.drawRGB(blendScratch, 0, bw, bx, by, bw, bh, false);
            }
        }
    }

    private void prepareSolidCompositeLut(int source) {
        if (solidCompositeLut == null) solidCompositeLut = new int[256 * 3];
        if (solidLutValid && solidLutSource == source && solidLutMode == renderMode
                && solidLutSrcRatio == srcRatio && solidLutDstRatio == dstRatio) return;

        int a = (source >>> 24) & 0xFF;
        int sr = (source >>> 16) & 0xFF;
        int sg = (source >>> 8) & 0xFF;
        int sb = source & 0xFF;

        if (renderMode == 0) {
            if (srcRatio != 255) {
                prepareRatioLuts();
                sr = sourceScaleLut[sr];
                sg = sourceScaleLut[sg];
                sb = sourceScaleLut[sb];
            }
            int inv = 255 - a;
            int baseR = multiplyU8(sr, a);
            int baseG = multiplyU8(sg, a);
            int baseB = multiplyU8(sb, a);
            for (int value = 0; value < 256; value++) {
                int dstTerm = multiplyU8(value, inv);
                solidCompositeLut[value] = 0xFF000000 | (div255Round(baseR + dstTerm) << 16);
                solidCompositeLut[256 + value] = div255Round(baseG + dstTerm) << 8;
                solidCompositeLut[512 + value] = div255Round(baseB + dstTerm);
            }
        } else {
            prepareRatioLuts();
            int srcR = sourceRatioLut[sr];
            int srcG = sourceRatioLut[sg];
            int srcB = sourceRatioLut[sb];
            int inv = 255 - a;
            for (int value = 0; value < 256; value++) {
                int dstTerm = destinationRatioLut[value];
                int r, g, b;
                if (renderMode == 1) {
                    int n = srcR + dstTerm; r = n >= 65025 ? 255 : div255Floor(n);
                    n = srcG + dstTerm; g = n >= 65025 ? 255 : div255Floor(n);
                    n = srcB + dstTerm; b = n >= 65025 ? 255 : div255Floor(n);
                } else {
                    int n = dstTerm - srcR; r = n <= 0 ? 0 : div255Floor(n);
                    n = dstTerm - srcG; g = n <= 0 ? 0 : div255Floor(n);
                    n = dstTerm - srcB; b = n <= 0 ? 0 : div255Floor(n);
                }
                if (a < 255) {
                    r = div255Round(multiplyU8(r, a) + multiplyU8(value, inv));
                    g = div255Round(multiplyU8(g, a) + multiplyU8(value, inv));
                    b = div255Round(multiplyU8(b, a) + multiplyU8(value, inv));
                }
                solidCompositeLut[value] = 0xFF000000 | (r << 16);
                solidCompositeLut[256 + value] = g << 8;
                solidCompositeLut[512 + value] = b;
            }
        }

        solidLutSource = source;
        solidLutMode = renderMode;
        solidLutSrcRatio = srcRatio;
        solidLutDstRatio = dstRatio;
        solidLutValid = true;
    }

    private boolean canUseNativeSolid(int source) {
        return renderMode == 0 && ((source >>> 24) & 0xFF) == 255;
    }

    private int nativeSolidRGB(int source) {
        int r = (source >>> 16) & 0xFF;
        int g = (source >>> 8) & 0xFF;
        int b = source & 0xFF;
        if (srcRatio != 255) {
            r = scaleU8(r, srcRatio);
            g = scaleU8(g, srcRatio);
            b = scaleU8(b, srcRatio);
        }
        return (r << 16) | (g << 8) | b;
    }

    private void beginNativeSolid(int source) {
        if (srcRatio != 255) midpGraphics.setColor(nativeSolidRGB(source));
    }

    private void endNativeSolid() {
        if (srcRatio != 255) midpGraphics.setColor(currentARGB & 0x00FFFFFF);
    }

    private void rasterLine(int x1, int y1, int x2, int y2, int source) {
        if (y1 == y2) { fillSolid(x1 < x2 ? x1 : x2, y1, Math.abs(x2 - x1) + 1, 1, source); return; }
        if (x1 == x2) { fillSolid(x1, y1 < y2 ? y1 : y2, 1, Math.abs(y2 - y1) + 1, source); return; }
        int dx = Math.abs(x2 - x1), sx = x1 < x2 ? 1 : -1;
        int dy = -Math.abs(y2 - y1), sy = y1 < y2 ? 1 : -1;
        int err = dx + dy;
        for (;;) {
            rasterPixel(x1, y1, source);
            if (x1 == x2 && y1 == y2) break;
            int e2 = err << 1;
            if (e2 >= dy) { err += dy; x1 += sx; }
            if (e2 <= dx) { err += dx; y1 += sy; }
        }
    }

    private void copyAreaSoftware(int sx, int sy, int width, int height, int dstX, int dstY) {
        int yStart = 0, yEnd = height, yStep = 1;
        if (dstY > sy && dstY < sy + height) { yStart = height - 1; yEnd = -1; yStep = -1; }
        int max = width < COMPOSITE_PIXELS ? width : COMPOSITE_PIXELS;
        ensureScratch(max);
        for (int ry = yStart; ry != yEnd; ry += yStep) {
            boolean reverseX = dstY + ry == sy + ry && dstX > sx && dstX < sx + width;
            if (reverseX) {
                for (int remain = width; remain > 0;) {
                    int bw = remain < max ? remain : max;
                    int bx = remain - bw;
                    backBuffer.getRGB(fillScratch, 0, bw, sx + bx + originX, sy + ry + originY, bw, 1);
                    drawRGBComposite(fillScratch, 0, bw, dstX + bx, dstY + ry, bw, 1, false);
                    remain -= bw;
                }
            } else {
                for (int bx = 0; bx < width; bx += max) {
                    int bw = width - bx; if (bw > max) bw = max;
                    backBuffer.getRGB(fillScratch, 0, bw, sx + bx + originX, sy + ry + originY, bw, 1);
                    drawRGBComposite(fillScratch, 0, bw, dstX + bx, dstY + ry, bw, 1, false);
                }
            }
        }
    }

    private int[] ensurePolygonScratch(int size) {
        if (polygonScratch == null || polygonScratch.length < size) {
            polygonScratch = new int[size];
        }
        return polygonScratch;
    }

    private void ensureBlendScratch(int size) {
        if (size > SOLID_COMPOSITE_PIXELS) throw new IllegalArgumentException("blend scratch exceeds fixed limit");
        if (blendScratch == null || blendScratch.length < size) blendScratch = new int[size];
    }

    private void ensureScratch(int size) {
        if (size > COMPOSITE_PIXELS) throw new IllegalArgumentException("draw scratch exceeds fixed limit");
        if (fillScratch == null || fillScratch.length < size) {
            fillScratch = new int[size];
        }
    }

    public void fillArc(int x, int y, int w, int h, int startAngle, int arcAngle) {
        ensureSurface();
        if (w <= 0 || h <= 0 || arcAngle == 0) return;
        if (canUseNativeSolid(currentARGB)) {
            beginNativeSolid(currentARGB);
            midpGraphics.fillArc(x, y, w, h, startAngle, arcAngle);
            endNativeSolid();
            return;
        }
        rasterArc(x, y, w, h, startAngle, arcAngle, true);
    }

    public void drawArc(int x, int y, int w, int h, int startAngle, int arcAngle) {
        ensureSurface();
        if (w < 0 || h < 0 || arcAngle == 0) return;
        if (canUseNativeSolid(currentARGB)) {
            beginNativeSolid(currentARGB);
            midpGraphics.drawArc(x, y, w, h, startAngle, arcAngle);
            endNativeSolid();
            return;
        }
        rasterArc(x, y, w, h, startAngle, arcAngle, false);
    }

    private void rasterArc(int x, int y, int w, int h, int startAngle, int arcAngle, boolean fill) {
        if (w == 0 || h == 0) { drawLine(x, y, x + w, y + h); return; }
        int full = arcAngle >= 360 || arcAngle <= -360 ? 1 : 0;
        int sweep = 0;
        int startX = 0, startY = 0, endX = 0, endY = 0;
        if (full == 0) {
            int start = startAngle % 360;
            if (start < 0) start += 360;
            if (arcAngle > 0) {
                sweep = arcAngle;
            } else {
                start = (start + arcAngle) % 360;
                if (start < 0) start += 360;
                sweep = -arcAngle;
            }
            int end = (start + sweep) % 360;
            startX = cosQ30(start);
            startY = sinQ30(start);
            endX = cosQ30(end);
            endY = sinQ30(end);
        }
        long ww = (long)w * (long)w;
        long hh = (long)h * (long)h;
        long bound = ww * hh;
        int innerW = w > 2 ? w - 2 : 0;
        int innerH = h > 2 ? h - 2 : 0;
        long iww = (long)innerW * innerW;
        long ihh = (long)innerH * innerH;
        long innerBound = iww * ihh;
        for (int py = y; py <= y + h; py++) {
            int run = -1;
            for (int px = x; px <= x + w; px++) {
                long dx = ((long)(px - x) << 1) - w;
                long dy = ((long)(py - y) << 1) - h;
                boolean inside = dx * dx * hh + dy * dy * ww <= bound;
                boolean hit = inside && (full != 0 || angleInside(dx, -dy, startX, startY, endX, endY, sweep));
                if (!fill && hit && innerW > 0 && innerH > 0) {
                    hit = dx * dx * ihh + dy * dy * iww > innerBound;
                }
                if (hit) {
                    if (run < 0) run = px;
                } else if (run >= 0) {
                    fillSolid(run, py, px - run, 1, currentARGB);
                    run = -1;
                }
            }
            if (run >= 0) fillSolid(run, py, x + w + 1 - run, 1, currentARGB);
        }
    }

    /**
     * 判斷指定點 (dx, dy) 是否落在角度範圍內。
     * 
     * 1. 採用起始邊、結束邊的方向向量做叉積（Cross Product）的整數運算
     * 2. 透過正負號判斷點落在射線的哪一側
     * 3. 根據掃描角（Sweep Angle）是否大於 180 度決定不同邏輯：
     *    - 小於等於 180 度：點必須同時落在 起始線左側 與 結束線右側（交集）。
     *    - 大於 180 度：不是同時落在兩邊外側的盲區（聯集）。
     */
    private static boolean angleInside(long dx, long dy,
                                       int startX, int startY,
                                       int endX, int endY, int sweep)
    {
        if (dx == 0L && dy == 0L) dx = 1L;
        long startToPoint = (long)startX * dy - (long)startY * dx;
        long pointToEnd = dx * (long)endY - dy * (long)endX;
        if (sweep <= 180) {
            return startToPoint >= 0L && pointToEnd >= 0L;
        }
        long endToPoint = (long)endX * dy - (long)endY * dx;
        long pointToStart = dx * (long)startY - dy * (long)startX;
        return !(endToPoint > 0L && pointToStart > 0L);
    }

    public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        fillPolygon(xPoints, yPoints, 0, nPoints);
    }

    public void fillPolygon(int[] xPoints, int[] yPoints, int offset, int count) {
        ensureSurface();
        if (xPoints == null || yPoints == null || count < 3) return;
        if (canUseNativeSolid(currentARGB)) {
            beginNativeSolid(currentARGB);
            for (int i = offset + 1; i < offset + count - 1; i++) {
                midpGraphics.fillTriangle(xPoints[offset], yPoints[offset], xPoints[i], yPoints[i], xPoints[i + 1], yPoints[i + 1]);
            }
            endNativeSolid();
            return;
        }
        int minY = yPoints[offset], maxY = minY;
        for (int i = offset + 1; i < offset + count; i++) {
            if (yPoints[i] < minY) minY = yPoints[i];
            if (yPoints[i] > maxY) maxY = yPoints[i];
        }
        int[] nodes = ensurePolygonScratch(count);
        for (int y = minY; y <= maxY; y++) {
            int n = 0;
            int j = offset + count - 1;
            for (int i = offset; i < offset + count; i++) {
                int yi = yPoints[i], yj = yPoints[j];
                if ((yi < y && yj >= y) || (yj < y && yi >= y)) {
                    nodes[n++] = xPoints[i] + (int)((long)(y - yi) * (xPoints[j] - xPoints[i]) / (yj - yi));
                }
                j = i;
            }
            for (int i = 1; i < n; i++) {
                int v = nodes[i], k = i - 1;
                while (k >= 0 && nodes[k] > v) { nodes[k + 1] = nodes[k]; k--; }
                nodes[k + 1] = v;
            }
            for (int i = 0; i + 1 < n; i += 2) {
                if (nodes[i + 1] >= nodes[i]) fillSolid(nodes[i], y, nodes[i + 1] - nodes[i] + 1, 1, currentARGB);
            }
        }
    }

    protected void copyStateTo(Graphics g) {
        g.currentARGB = currentARGB;
        g.flipMode = flipMode;
        g.currentFont = currentFont;
        g.renderMode = renderMode;
        g.srcRatio = srcRatio;
        g.dstRatio = dstRatio;
        g.nativeRenderFastPath = nativeRenderFastPath;
    }

    public Graphics copy() {
        Graphics g = new Graphics();
        copyStateTo(g);
        return g;
    }

    public void dispose() {
        backBuffer = null;
        midpGraphics = null;
        sourceOverAlphaPath = false;
        fillScratch = null;
        pixelScratch = null;
        blendScratch = null;
        polygonScratch = null;
        scaleMapX = null;
        scaleMapY = null;
        sourceRatioLut = null;
        sourceScaleLut = null;
        destinationRatioLut = null;
        solidCompositeLut = null;
        lockCount = 0;
    }
}
