package com.nttdocomo.opt.ui;

import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.Image;

/** Graphics2 舊版的可選繪圖元件。 */
public class Sprite {
    private Image image;
    private int sourceX;
    private int sourceY;
    private int width;
    private int height;
    private boolean wholeImage;
    private int x;
    private int y;
    private boolean visible = true;
    private int flipMode = Graphics.FLIP_NONE;
    private int renderMode = Graphics2.OP_REPL;
    private int sourceRatio = 255;
    private int destinationRatio = 255;

    protected Sprite() {}

    public Sprite(Image image) {
        this.image = image;
        wholeImage = true;
        syncWholeImageSize();
    }

    public Sprite(Image image, int x, int y, int width, int height) {
        if (width < 0 || height < 0) throw new IllegalArgumentException("negative sprite size");
        this.image = image;
        sourceX = x;
        sourceY = y;
        this.width = width;
        this.height = height;
    }

    public void setLocation(int x, int y) { this.x = x; this.y = y; }

    public void setImage(Image image) {
        if (image == null) throw new NullPointerException("image");
        this.image = image;
        sourceX = 0;
        sourceY = 0;
        wholeImage = true;
        syncWholeImageSize();
    }

    public void setImage(Image image, int x, int y, int width, int height) {
        if (image == null) throw new NullPointerException("image");
        if (width < 0 || height < 0) throw new IllegalArgumentException("negative sprite size");
        this.image = image;
        sourceX = x;
        sourceY = y;
        this.width = width;
        this.height = height;
        wholeImage = false;
    }

    public void setVisible(boolean value) { visible = value; }
    public boolean isVisible() { return visible; }

    public void setFlipMode(int mode) {
        if (mode != Graphics.FLIP_NONE && mode != Graphics.FLIP_HORIZONTAL
                && mode != Graphics.FLIP_VERTICAL && mode != Graphics.FLIP_ROTATE) {
            throw new IllegalArgumentException("invalid sprite flip mode");
        }
        flipMode = mode;
    }

    public void setRenderMode(int operator, int srcRatio, int dstRatio) {
        if (operator < Graphics2.OP_REPL || operator > Graphics2.OP_SUB) {
            throw new IllegalArgumentException("invalid raster operator");
        }
        if (srcRatio < 0 || srcRatio > 255 || dstRatio < 0 || dstRatio > 255) {
            throw new IllegalArgumentException("raster ratio out of range");
        }
        renderMode = operator;
        sourceRatio = srcRatio;
        destinationRatio = dstRatio;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { if (wholeImage) syncWholeImageSize(); return width; }
    public int getHeight() { if (wholeImage) syncWholeImageSize(); return height; }

    Image image() { return image; }
    int sourceX() { return sourceX; }
    int sourceY() { return sourceY; }
    int flipMode() { return flipMode; }
    int renderMode() { return renderMode; }
    int sourceRatio() { return sourceRatio; }
    int destinationRatio() { return destinationRatio; }

    private void syncWholeImageSize() {
        if (wholeImage && image != null) {
            width = image.getWidth();
            height = image.getHeight();
        } else if (wholeImage) {
            width = height = 0;
        }
    }
}
