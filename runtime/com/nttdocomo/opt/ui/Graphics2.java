package com.nttdocomo.opt.ui;

import com.nttdocomo.opt.ui.j3d.AffineTrans;
import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.Image;
import com.nttdocomo.ui.MediaImage;
import com.nttdocomo.ui.MediaManager;

public class Graphics2 extends Graphics {
    public static final int CM_NORMAL = 0;
    public static final int CM_ZOOM = 256;

    public static final int OP_REPL = 0;
    public static final int OP_ADD = 1;
    public static final int OP_SUB = 2;

    private int coordinateMode = CM_NORMAL;
    private int[] coordScratchX;
    private int[] coordScratchY;
    private int[] affineSource;
    private int[] affineRow;

    public Graphics2() { super(); }

    public void setRenderMode(int operator, int srcRatio, int dstRatio) {
        setRenderModeState(operator, srcRatio, dstRatio);
    }

    public void setCoordinateMode(int mode) {
        if (mode != CM_NORMAL && mode != CM_ZOOM) throw new IllegalArgumentException("invalid coordinate mode");
        coordinateMode = mode;
    }

    public static int getIntermediateColor(int color1, int color2, int ratio) {
        if (ratio < 0 || ratio > 255) throw new IllegalArgumentException("ratio out of range");
        int inv = 255 - ratio;
        int r = (multiplyU8((color1 >>> 16) & 255, inv) + multiplyU8((color2 >>> 16) & 255, ratio)) / 255;
        int g = (multiplyU8((color1 >>> 8) & 255, inv) + multiplyU8((color2 >>> 8) & 255, ratio)) / 255;
        int b = (multiplyU8(color1 & 255, inv) + multiplyU8(color2 & 255, ratio)) / 255;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public Image getImage(int x, int y, int width, int height) {
        ensureSurface();
        /* getImage() 抓的是 framebuffer 原始像素，縮放座標只套在繪圖動作上。 */
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("invalid capture size");
        int px = x + getOriginX();
        int py = y + getOriginY();
        if (px < 0 || py < 0 || px + width > screenWidth || py + height > screenHeight) {
            throw new IllegalArgumentException("capture outside graphics surface");
        }
        javax.microedition.lcdui.Image captured = javax.microedition.lcdui.Image.createImage(
                backBuffer, px, py, width, height, 0);
        return new Image(captured);
    }

    public void drawNumber(int x, int y, int value, int digit) {
        if (digit <= 0) throw new IllegalArgumentException("digit must be positive");
        String valueText = String.valueOf(value);
        StringBuffer out = new StringBuffer(digit);
        int pad = digit - valueText.length();
        while (pad-- > 0) out.append(' ');
        if (valueText.length() <= digit) out.append(valueText);
        else out.append(valueText.substring(valueText.length() - digit));
        super.drawString(out.toString(), c(x), c(y));
    }

    public void drawNthImage(MediaImage image, int k, int x, int y) {
        if (image == null) throw new NullPointerException("image");
        super.drawImage(MediaManager.getImageFrame(image, k), c(x), c(y));
    }

    /** MIDP 查不到可靠的垂直同步週期，因此用 0 表示由平台自行安排。 */
    public int getSyncUnlockInterval() { return 0; }

    /** 把這一幀送出去；0 代表下一幀仍沒有可供排程的 vsync 間隔。 */
    public int syncUnlock(int interval) {
        if (interval < 0) throw new IllegalArgumentException("negative interval");
        unlock(true);
        return 0;
    }

    /**
     * N 系列 DoJa 把 affine drawImage 視為獨立路徑，不套 setRenderMode()。
     * 照這個規則處理可避免旋轉圖片意外吃到前一次的 ADD/SUB 狀態。
     */
    public void drawImage(Image image, AffineTrans at) {
        if (image == null) return;
        drawAffine(image, at, 0, 0, image.getWidth(), image.getHeight());
    }

    public void drawImage(Image image, AffineTrans at, int sx, int sy, int width, int height) {
        drawAffine(image, at, sx, sy, width, height);
    }

    private void drawAffine(Image image, AffineTrans at, int sx, int sy, int sw, int sh) {
        ensureSurface();
        if (image == null || at == null) throw new NullPointerException();
        if (sw <= 0 || sh <= 0) return;
        int iw = image.getWidth(), ih = image.getHeight();
        if (sx < 0) { sw += sx; sx = 0; }
        if (sy < 0) { sh += sy; sy = 0; }
        if (sx + sw > iw) sw = iw - sx;
        if (sy + sh > ih) sh = ih - sy;
        if (sw <= 0 || sh <= 0) return;

        long det = (long)at.m00 * at.m11 - (long)at.m01 * at.m10;
        if (det == 0) return;

        int x0 = tx(at, 0, 0), y0 = ty(at, 0, 0);
        int x1 = tx(at, sw, 0), y1 = ty(at, sw, 0);
        int x2 = tx(at, 0, sh), y2 = ty(at, 0, sh);
        int x3 = tx(at, sw, sh), y3 = ty(at, sw, sh);
        int minX = min4(x0,x1,x2,x3) - 1;
        int maxX = max4(x0,x1,x2,x3) + 1;
        int minY = min4(y0,y1,y2,y3) - 1;
        int maxY = max4(y0,y1,y2,y3) + 1;

        int clipX = midpGraphics.getClipX();
        int clipY = midpGraphics.getClipY();
        int clipR = clipX + midpGraphics.getClipWidth() - 1;
        int clipB = clipY + midpGraphics.getClipHeight() - 1;
        if (minX < clipX) minX = clipX;
        if (minY < clipY) minY = clipY;
        if (maxX > clipR) maxX = clipR;
        if (maxY > clipB) maxY = clipB;
        if (minX > maxX || minY > maxY) return;

        long sourceCount = (long)sw * sh;
        if (sourceCount > Integer.MAX_VALUE) throw new IllegalArgumentException("image region too large");
        int count = (int)sourceCount;
        if (affineSource == null || affineSource.length < count) affineSource = new int[count];
        javax.microedition.lcdui.Image src = image.getMIDPImage();
        if (src == null) return;
        src.getRGB(affineSource, 0, sw, sx, sy, sw, sh);
        for (int i = 0; i < count; i++) affineSource[i] = prepareImagePixel(image, affineSource[i]);

        int rowWidth = maxX - minX + 1;
        if (affineRow == null || affineRow.length < rowWidth) affineRow = new int[rowWidth];
        for (int dy = minY; dy <= maxY; dy++) {
            for (int dx = minX; dx <= maxX; dx++) {
                long qx = dx - (long)at.m03;
                long qy = dy - (long)at.m13;
                long nu = 4096L * ((long)at.m11 * qx - (long)at.m01 * qy);
                long nv = 4096L * (-(long)at.m10 * qx + (long)at.m00 * qy);
                int u = floorDiv(nu, det);
                int v = floorDiv(nv, det);
                affineRow[dx - minX] = (u >= 0 && v >= 0 && u < sw && v < sh)
                        ? affineSource[v * sw + u] : 0;
            }
            drawRGBReplacement(affineRow, 0, rowWidth, minX, dy, rowWidth, 1, true);
        }
    }

    private static int tx(AffineTrans a, int x, int y) {
        return (int)(((long)a.m00 * x + (long)a.m01 * y + 2048L) >> 12) + a.m03;
    }
    private static int ty(AffineTrans a, int x, int y) {
        return (int)(((long)a.m10 * x + (long)a.m11 * y + 2048L) >> 12) + a.m13;
    }
    private static int floorDiv(long n, long d) {
        if (d < 0) { n = -n; d = -d; }
        if (n >= 0) return (int)(n / d);
        return (int)(-((-n + d - 1) / d));
    }
    private static int min4(int a,int b,int c,int d) { int m=a<b?a:b; if(c<m)m=c; if(d<m)m=d; return m; }
    private static int max4(int a,int b,int c,int d) { int m=a>b?a:b; if(c>m)m=c; if(d>m)m=d; return m; }

    public void drawImage(Image img, int x, int y) { super.drawImage(img, c(x), c(y)); }
    public void drawImage(Image img, int dx, int dy, int sx, int sy, int width, int height) {
        super.drawImage(img, c(dx), c(dy), c(sx), c(sy), c(width), c(height));
    }
    public void drawScaledImage(Image img, int dx, int dy, int dw, int dh, int sx, int sy, int sw, int sh) {
        super.drawScaledImage(img, c(dx), c(dy), c(dw), c(dh), c(sx), c(sy), c(sw), c(sh));
    }
    public void drawString(String str, int x, int y) { super.drawString(str, c(x), c(y)); }
    public void drawChars(char[] data, int x, int y, int off, int len) { super.drawChars(data, c(x), c(y), off, len); }
    public void drawLine(int x1, int y1, int x2, int y2) { super.drawLine(c(x1), c(y1), c(x2), c(y2)); }
    public void drawRect(int x, int y, int w, int h) { super.drawRect(c(x), c(y), c(w), c(h)); }
    public void fillRect(int x, int y, int w, int h) { super.fillRect(c(x), c(y), c(w), c(h)); }
    public void clearRect(int x, int y, int w, int h) { super.clearRect(c(x), c(y), c(w), c(h)); }
    public void drawArc(int x, int y, int w, int h, int start, int arc) { super.drawArc(c(x), c(y), c(w), c(h), start, arc); }
    public void fillArc(int x, int y, int w, int h, int start, int arc) { super.fillArc(c(x), c(y), c(w), c(h), start, arc); }
    public void copyArea(int sx, int sy, int w, int h, int dx, int dy) { super.copyArea(c(sx), c(sy), c(w), c(h), c(dx), c(dy)); }
    public void setPixel(int x, int y) { super.setPixel(c(x), c(y)); }
    public void setPixel(int x, int y, int color) { super.setPixel(c(x), c(y), color); }
    public void setRGBPixel(int x, int y, int pixel) { super.setRGBPixel(c(x), c(y), pixel); }
    public int getPixel(int x, int y) { return super.getPixel(c(x), c(y)); }
    public int getRGBPixel(int x, int y) { return super.getRGBPixel(c(x), c(y)); }

    public void drawPolyline(int[] xs, int[] ys, int count) { drawPolyline(xs, ys, 0, count); }
    public void drawPolyline(int[] xs, int[] ys, int off, int count) {
        if (coordinateMode == CM_NORMAL) { super.drawPolyline(xs, ys, off, count); return; }
        scalePoints(xs, ys, off, count);
        super.drawPolyline(coordScratchX, coordScratchY, 0, count);
    }

    public void fillPolygon(int[] xs, int[] ys, int count) { fillPolygon(xs, ys, 0, count); }
    public void fillPolygon(int[] xs, int[] ys, int off, int count) {
        if (coordinateMode == CM_NORMAL) { super.fillPolygon(xs, ys, off, count); return; }
        scalePoints(xs, ys, off, count);
        super.fillPolygon(coordScratchX, coordScratchY, 0, count);
    }

    private void scalePoints(int[] xs, int[] ys, int off, int count) {
        if (xs == null || ys == null) throw new NullPointerException();
        if (off < 0 || count < 0 || off + count > xs.length || off + count > ys.length) {
            throw new ArrayIndexOutOfBoundsException();
        }
        if (coordScratchX == null || coordScratchX.length < count) coordScratchX = new int[count];
        if (coordScratchY == null || coordScratchY.length < count) coordScratchY = new int[count];
        for (int i = 0; i < count; i++) {
            coordScratchX[i] = xs[off + i] >> 8;
            coordScratchY[i] = ys[off + i] >> 8;
        }
    }

    private int c(int value) { return coordinateMode == CM_ZOOM ? value >> 8 : value; }
}
