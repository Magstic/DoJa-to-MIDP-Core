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
    public void setAlpha(int value) { alpha = value < 0 ? 0 : value > 255 ? 255 : value; }
    public int getAlpha() { return alpha; }

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
