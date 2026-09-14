package com.nttdocomo.opt.ui;

import com.nttdocomo.opt.ui.j3d.AffineTrans;
import com.nttdocomo.ui.Display;
import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.Image;
import com.nttdocomo.ui.MediaImage;
import com.nttdocomo.ui.MediaManager;
import com.nttdocomo.ui.UIException;

/** DoJa 5.x optional extended graphics surface. */
public class Graphics2 extends Graphics {
    public static final int CM_NORMAL = 0;
    public static final int CM_ZOOM = 256;

    public static final int OP_REPL = 0;
    public static final int OP_ADD = 1;
    public static final int OP_SUB = 2;

    private static final int SYNC_INTERVAL_US = 16667;

    private int coordinateMode = CM_NORMAL;
    private int logicalOriginX;
    private int logicalOriginY;
    private int[] coordScratchX;
    private int[] coordScratchY;
    private int[] affineSource;
    private int[] affineRow;
    private boolean syncStarted;
    private long syncTargetMicros;

    protected Graphics2() { super(); }

    public void setRenderMode(int operator, int srcRatio, int dstRatio) {
        setRenderModeState(operator, srcRatio, dstRatio);
    }

    public void setCoordinateMode(int mode) {
        if (mode != CM_NORMAL && mode != CM_ZOOM) throw new IllegalArgumentException("invalid coordinate mode");
        if (coordinateMode == mode) return;
        coordinateMode = mode;
        applyPhysicalOrigin();
    }

    public void setOrigin(int x, int y) {
        logicalOriginX = x;
        logicalOriginY = y;
        applyPhysicalOrigin();
    }

    private void applyPhysicalOrigin() {
        if (coordinateMode == CM_ZOOM) super.setOrigin(logicalOriginX >> 8, logicalOriginY >> 8);
        else super.setOrigin(logicalOriginX, logicalOriginY);
    }

    public static int getIntermediateColor(int color1, int color2, int ratio) {
        if (ratio < 0 || ratio > 255) throw new IllegalArgumentException("ratio out of range");
        if (ratio == 0) return color1;
        if (ratio == 255) return color2;
        int inv = 255 - ratio;
        int r = div255Floor(multiplyU8((color1 >>> 16) & 255, inv)
                + multiplyU8((color2 >>> 16) & 255, ratio));
        int g = div255Floor(multiplyU8((color1 >>> 8) & 255, inv)
                + multiplyU8((color2 >>> 8) & 255, ratio));
        int b = div255Floor(multiplyU8(color1 & 255, inv)
                + multiplyU8(color2 & 255, ratio));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public Image getImage(int x, int y, int width, int height) {
        ensureSurface();
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("invalid capture size");
        long requestedRight = (long)x + width;
        long requestedBottom = (long)y + height;
        int left = x < 0 ? 0 : x;
        int top = y < 0 ? 0 : y;
        int right = requestedRight > screenWidth ? screenWidth : (int)requestedRight;
        int bottom = requestedBottom > screenHeight ? screenHeight : (int)requestedBottom;
        if (left >= right || top >= bottom) return null;

        int outW = right - left;
        int outH = bottom - top;
        int[] pixels = new int[outW * outH];
        backBuffer.getRGB(pixels, 0, outW, left, top, outW, outH);
        Image result = Image.createImage(outW, outH);
        result.getGraphics().setRGBPixels(0, 0, outW, outH, pixels, 0);
        return result;
    }

    public void drawNumber(int x, int y, int value, int digit) {
        if (digit <= 0) throw new IllegalArgumentException("digit must be positive");
        String valueText = String.valueOf(value);
        StringBuffer out = new StringBuffer(digit);
        int pad = digit - valueText.length();
        while (pad-- > 0) out.append(' ');
        if (valueText.length() <= digit) out.append(valueText);
        else out.append(valueText.substring(valueText.length() - digit));
        drawString(out.toString(), x, y);
    }

    public void drawNthImage(MediaImage image, int k, int x, int y) {
        if (image == null) throw new NullPointerException("image");
        if (k < 0) throw new IllegalArgumentException("negative image index");
        if (!MediaManager.isImageUsed(image)) throw new UIException(UIException.ILLEGAL_STATE, "unused media image");
        Image frame;
        try {
            frame = MediaManager.getImageFrame(image, k);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new UIException(UIException.ILLEGAL_STATE, "media image unavailable");
        }
        if (frame == null || frame.getMIDPImage() == null) throw new UIException(UIException.ILLEGAL_STATE, "disposed media image");
        drawImage(frame, x, y);
    }

    public void drawSpriteSet(SpriteSet sprites) {
        if (sprites == null) throw new NullPointerException("sprites");
        drawSpriteSet(sprites, 0, sprites.getCount());
    }

    public void drawSpriteSet(SpriteSet sprites, int offset, int count) {
        if (sprites == null) throw new NullPointerException("sprites");
        int total = sprites.getCount();
        if (offset < 0 || count < 0 || offset > total || count > total - offset) {
            throw new ArrayIndexOutOfBoundsException();
        }
        Sprite[] all = sprites.getSprites();
        for (int i = 0; i < total; i++) {
            Sprite sprite = all[i];
            if (sprite == null) throw new NullPointerException("sprite");
            if (sprite.image() == null) throw new NullPointerException("sprite image");
            if (sprite.image().getMIDPImage() == null) throw new UIException(UIException.ILLEGAL_STATE, "disposed sprite image");
        }

        int oldMode = getRenderModeState();
        int oldSrc = getSourceRatioState();
        int oldDst = getDestinationRatioState();
        int oldFlip = getFlipModeState();
        try {
            int end = offset + count;
            for (int i = offset; i < end; i++) {
                Sprite sprite = all[i];
                if (!sprite.isVisible()) continue;
                setRenderModeState(sprite.renderMode(), sprite.sourceRatio(), sprite.destinationRatio());
                super.setFlipMode(sprite.flipMode());
                drawImage(sprite.image(), sprite.getX(), sprite.getY(), sprite.sourceX(), sprite.sourceY(),
                        sprite.getWidth(), sprite.getHeight());
            }
        } finally {
            setRenderModeState(oldMode, oldSrc, oldDst);
            super.setFlipMode(oldFlip);
        }
    }

    public int getSyncUnlockInterval() { return SYNC_INTERVAL_US; }

    public int syncUnlock(int interval) {
        if (interval <= 0) throw new IllegalArgumentException("interval must be positive");
        if (!hasActiveLock()) return 0;
        if (!canPresentLockedSurface()) return 0;

        long now = nowMicros();
        int actual;
        long target;
        if (!syncStarted) {
            actual = 1;
            target = now + SYNC_INTERVAL_US;
        } else {
            long elapsed = now - syncTargetMicros;
            if (elapsed < 0) elapsed = 0;
            if (elapsed < (long)interval * SYNC_INTERVAL_US) {
                actual = interval;
            } else {
                long cycles = elapsed / SYNC_INTERVAL_US + 1L;
                actual = cycles > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)cycles;
            }
            target = syncTargetMicros + (long)actual * SYNC_INTERVAL_US;
        }

        waitUntilMicros(target);
        syncStarted = true;
        syncTargetMicros = target;
        unlock(true);
        return actual;
    }

    public void drawImage(Image image, AffineTrans at) {
        if (image == null || at == null) throw new NullPointerException();
        ensureImageAlive(image);
        drawAffine(image, at, 0, 0, image.getWidth(), image.getHeight());
    }

    public void drawImage(Image image, AffineTrans at, int sx, int sy, int width, int height) {
        if (image == null || at == null) throw new NullPointerException();
        if (width < 0 || height < 0) throw new IllegalArgumentException("negative source size");
        ensureImageAlive(image);
        if (width == 0 || height == 0) return;
        drawAffine(image, at, sx, sy, width, height);
    }

    private void drawAffine(Image image, AffineTrans at, int sx, int sy, int sw, int sh) {
        ensureSurface();
        int iw = image.getWidth();
        int ih = image.getHeight();
        long sourceRight = (long)sx + sw;
        long sourceBottom = (long)sy + sh;
        int csx = sx < 0 ? 0 : sx;
        int csy = sy < 0 ? 0 : sy;
        int cr = sourceRight > iw ? iw : (int)sourceRight;
        int cb = sourceBottom > ih ? ih : (int)sourceBottom;
        if (csx >= cr || csy >= cb) return;
        int cw = cr - csx;
        int ch = cb - csy;

        long determinant = (long)at.m00 * at.m11 - (long)at.m01 * at.m10;
        if (determinant == 0) return;

        int x0 = transformX(at, csx, csy), y0 = transformY(at, csx, csy);
        int x1 = transformX(at, cr, csy), y1 = transformY(at, cr, csy);
        int x2 = transformX(at, csx, cb), y2 = transformY(at, csx, cb);
        int x3 = transformX(at, cr, cb), y3 = transformY(at, cr, cb);
        int minX = min4(x0, x1, x2, x3) - 1;
        int maxX = max4(x0, x1, x2, x3) + 1;
        int minY = min4(y0, y1, y2, y3) - 1;
        int maxY = max4(y0, y1, y2, y3) + 1;

        int clipX = midpGraphics.getClipX();
        int clipY = midpGraphics.getClipY();
        int clipR = clipX + midpGraphics.getClipWidth() - 1;
        int clipB = clipY + midpGraphics.getClipHeight() - 1;
        if (minX < clipX) minX = clipX;
        if (minY < clipY) minY = clipY;
        if (maxX > clipR) maxX = clipR;
        if (maxY > clipB) maxY = clipB;
        if (minX > maxX || minY > maxY) return;

        int count = checkedPixelCount(cw, ch);
        if (affineSource == null || affineSource.length < count) affineSource = new int[count];
        image.getMIDPImage().getRGB(affineSource, 0, cw, csx, csy, cw, ch);
        for (int i = 0; i < count; i++) affineSource[i] = prepareImagePixel(image, affineSource[i]);

        int rowWidth = maxX - minX + 1;
        if (affineRow == null || affineRow.length < rowWidth) affineRow = new int[rowWidth];

        long den = determinant;
        int determinantSign = 1;
        if (den < 0) { den = -den; determinantSign = -1; }

        long qx = 4096L * minX - at.m02;
        long qy = 4096L * minY - at.m12;
        long startUN = determinantSign * ((long)at.m11 * qx - (long)at.m01 * qy);
        long startVN = determinantSign * (-(long)at.m10 * qx + (long)at.m00 * qy);
        long stepXUN = determinantSign * ((long)at.m11 * 4096L);
        long stepXVN = determinantSign * (-(long)at.m10 * 4096L);
        long stepYUN = determinantSign * (-(long)at.m01 * 4096L);
        long stepYVN = determinantSign * ((long)at.m00 * 4096L);

        long rowU = floorDivPositive(startUN, den);
        long rowUR = startUN - rowU * den;
        long rowV = floorDivPositive(startVN, den);
        long rowVR = startVN - rowV * den;
        long stepXU = floorDivPositive(stepXUN, den);
        long stepXUR = stepXUN - stepXU * den;
        long stepXV = floorDivPositive(stepXVN, den);
        long stepXVR = stepXVN - stepXV * den;
        long stepYU = floorDivPositive(stepYUN, den);
        long stepYUR = stepYUN - stepYU * den;
        long stepYV = floorDivPositive(stepYVN, den);
        long stepYVR = stepYVN - stepYV * den;

        for (int dy = minY; dy <= maxY; dy++) {
            long u = rowU, ur = rowUR;
            long v = rowV, vr = rowVR;
            for (int i = 0; i < rowWidth; i++) {
                if (u >= csx && u < cr && v >= csy && v < cb) {
                    affineRow[i] = affineSource[((int)v - csy) * cw + ((int)u - csx)];
                } else {
                    affineRow[i] = 0;
                }
                u += stepXU;
                ur += stepXUR;
                if (ur >= den) { ur -= den; u++; }
                v += stepXV;
                vr += stepXVR;
                if (vr >= den) { vr -= den; v++; }
            }
            drawRGBComposite(affineRow, 0, rowWidth, minX, dy, rowWidth, 1, true);
            rowU += stepYU;
            rowUR += stepYUR;
            if (rowUR >= den) { rowUR -= den; rowU++; }
            rowV += stepYV;
            rowVR += stepYVR;
            if (rowVR >= den) { rowVR -= den; rowV++; }
        }
    }

    private static int transformX(AffineTrans at, int x, int y) {
        return (int)(((long)at.m00 * x + (long)at.m01 * y + at.m02) >> 12);
    }

    private static int transformY(AffineTrans at, int x, int y) {
        return (int)(((long)at.m10 * x + (long)at.m11 * y + at.m12) >> 12);
    }

    private static long floorDivPositive(long value, long positiveDenominator) {
        if (value >= 0) return value / positiveDenominator;
        return -((-value + positiveDenominator - 1) / positiveDenominator);
    }

    private static int checkedPixelCount(int width, int height) {
        long count = (long)width * height;
        if (count > Integer.MAX_VALUE) throw new IllegalArgumentException("image region too large");
        return (int)count;
    }

    private static int min4(int a, int b, int c, int d) { int m = a < b ? a : b; if (c < m) m = c; if (d < m) m = d; return m; }
    private static int max4(int a, int b, int c, int d) { int m = a > b ? a : b; if (c > m) m = c; if (d > m) m = d; return m; }

    private static void ensureImageAlive(Image image) {
        if (image.getMIDPImage() == null) throw new UIException(UIException.ILLEGAL_STATE, "disposed image");
    }

    private static long nowMicros() { return System.currentTimeMillis() * 1000L; }

    private static void waitUntilMicros(long target) {
        for (;;) {
            long remaining = target - nowMicros();
            if (remaining <= 0) return;
            long millis = remaining / 1000L;
            if (millis <= 0) millis = 1;
            try { Thread.sleep(millis); }
            catch (InterruptedException ignored) {}
        }
    }

    /* The normal coordinate mode deliberately adds only one branch per API call. */
    public void drawImage(Image image, int x, int y) {
        if (coordinateMode == CM_NORMAL) { super.drawImage(image, x, y); return; }
        super.drawImage(image, zoomX(x), zoomY(y));
    }

    public void drawImage(Image image, int dx, int dy, int sx, int sy, int width, int height) {
        if (coordinateMode == CM_NORMAL) { super.drawImage(image, dx, dy, sx, sy, width, height); return; }
        super.drawImage(image, zoomX(dx), zoomY(dy), sx, sy, width, height);
    }

    public void drawScaledImage(Image image, int dx, int dy, int dw, int dh,
            int sx, int sy, int sw, int sh) {
        if (coordinateMode == CM_NORMAL) {
            super.drawScaledImage(image, dx, dy, dw, dh, sx, sy, sw, sh);
            return;
        }
        super.drawScaledImage(image, zoomX(dx), zoomY(dy), zoomSpanX(dx, dw), zoomSpanY(dy, dh), sx, sy, sw, sh);
    }

    public void drawString(String str, int x, int y) {
        if (coordinateMode == CM_NORMAL) { super.drawString(str, x, y); return; }
        super.drawString(str, zoomX(x), zoomY(y));
    }

    public void drawChars(char[] data, int x, int y, int off, int len) {
        if (coordinateMode == CM_NORMAL) { super.drawChars(data, x, y, off, len); return; }
        super.drawChars(data, zoomX(x), zoomY(y), off, len);
    }

    public void drawLine(int x1, int y1, int x2, int y2) {
        if (coordinateMode == CM_NORMAL) { super.drawLine(x1, y1, x2, y2); return; }
        super.drawLine(zoomX(x1), zoomY(y1), zoomX(x2), zoomY(y2));
    }

    public void drawRect(int x, int y, int width, int height) {
        if (coordinateMode == CM_NORMAL) { super.drawRect(x, y, width, height); return; }
        super.drawRect(zoomX(x), zoomY(y), zoomSpanX(x, width), zoomSpanY(y, height));
    }

    public void fillRect(int x, int y, int width, int height) {
        if (coordinateMode == CM_NORMAL) { super.fillRect(x, y, width, height); return; }
        super.fillRect(zoomX(x), zoomY(y), zoomSpanX(x, width), zoomSpanY(y, height));
    }

    public void clearRect(int x, int y, int width, int height) {
        if (coordinateMode == CM_NORMAL) { super.clearRect(x, y, width, height); return; }
        super.clearRect(zoomX(x), zoomY(y), zoomSpanX(x, width), zoomSpanY(y, height));
    }

    public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        if (coordinateMode == CM_NORMAL) { super.drawArc(x, y, width, height, startAngle, arcAngle); return; }
        super.drawArc(zoomX(x), zoomY(y), zoomSpanX(x, width), zoomSpanY(y, height), startAngle, arcAngle);
    }

    public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        if (coordinateMode == CM_NORMAL) { super.fillArc(x, y, width, height, startAngle, arcAngle); return; }
        super.fillArc(zoomX(x), zoomY(y), zoomSpanX(x, width), zoomSpanY(y, height), startAngle, arcAngle);
    }

    public void copyArea(int x, int y, int width, int height, int dx, int dy) {
        if (coordinateMode == CM_NORMAL) { super.copyArea(x, y, width, height, dx, dy); return; }
        int zx = zoomX(x);
        int zy = zoomY(y);
        int zw = zoomSpanX(x, width);
        int zh = zoomSpanY(y, height);
        int zdx = zoomPhysicalX(x + dx) - zoomPhysicalX(x);
        int zdy = zoomPhysicalY(y + dy) - zoomPhysicalY(y);
        super.copyArea(zx, zy, zw, zh, zdx, zdy);
    }

    public void setPixel(int x, int y) {
        if (coordinateMode == CM_NORMAL) { super.setPixel(x, y); return; }
        super.setPixel(zoomX(x), zoomY(y));
    }

    public void setPixel(int x, int y, int color) {
        if (coordinateMode == CM_NORMAL) { super.setPixel(x, y, color); return; }
        super.setPixel(zoomX(x), zoomY(y), color);
    }

    public void setRGBPixel(int x, int y, int pixel) {
        if (coordinateMode == CM_NORMAL) { super.setRGBPixel(x, y, pixel); return; }
        super.setRGBPixel(zoomX(x), zoomY(y), pixel);
    }

    public void drawPolyline(int[] xs, int[] ys, int count) { drawPolyline(xs, ys, 0, count); }

    public void drawPolyline(int[] xs, int[] ys, int offset, int count) {
        if (coordinateMode == CM_NORMAL) { super.drawPolyline(xs, ys, offset, count); return; }
        zoomPoints(xs, ys, offset, count);
        super.drawPolyline(coordScratchX, coordScratchY, 0, count);
    }

    public void fillPolygon(int[] xs, int[] ys, int count) { fillPolygon(xs, ys, 0, count); }

    public void fillPolygon(int[] xs, int[] ys, int offset, int count) {
        if (coordinateMode == CM_NORMAL) { super.fillPolygon(xs, ys, offset, count); return; }
        zoomPoints(xs, ys, offset, count);
        super.fillPolygon(coordScratchX, coordScratchY, 0, count);
    }

    private void zoomPoints(int[] xs, int[] ys, int offset, int count) {
        if (xs == null || ys == null) throw new NullPointerException();
        if (offset < 0 || count < 0 || offset + count > xs.length || offset + count > ys.length) {
            throw new ArrayIndexOutOfBoundsException();
        }
        if (coordScratchX == null || coordScratchX.length < count) coordScratchX = new int[count];
        if (coordScratchY == null || coordScratchY.length < count) coordScratchY = new int[count];
        for (int i = 0; i < count; i++) {
            coordScratchX[i] = zoomX(xs[offset + i]);
            coordScratchY[i] = zoomY(ys[offset + i]);
        }
    }

    private int zoomPhysicalX(int value) { return (int)(((long)value + logicalOriginX) >> 8); }
    private int zoomPhysicalY(int value) { return (int)(((long)value + logicalOriginY) >> 8); }
    private int zoomX(int value) { return zoomPhysicalX(value) - getOriginX(); }
    private int zoomY(int value) { return zoomPhysicalY(value) - getOriginY(); }
    private int zoomSpanX(int start, int length) { return zoomPhysicalX(start + length) - zoomPhysicalX(start); }
    private int zoomSpanY(int start, int length) { return zoomPhysicalY(start + length) - zoomPhysicalY(start); }
}
