package com.nttdocomo.ui;

public class Display {
    public static final int KEY_0 = 0;
    public static final int KEY_1 = 1;
    public static final int KEY_2 = 2;
    public static final int KEY_3 = 3;
    public static final int KEY_4 = 4;
    public static final int KEY_5 = 5;
    public static final int KEY_6 = 6;
    public static final int KEY_7 = 7;
    public static final int KEY_8 = 8;
    public static final int KEY_9 = 9;
    public static final int KEY_ASTERISK = 10;
    public static final int KEY_POUND = 11;
    public static final int KEY_LEFT = 16;
    public static final int KEY_UP = 17;
    public static final int KEY_RIGHT = 18;
    public static final int KEY_DOWN = 19;
    public static final int KEY_SELECT = 20;
    public static final int KEY_SOFT1 = 21;
    public static final int KEY_SOFT2 = 22;

    public static final int KEY_PRESSED_EVENT = 0;
    public static final int KEY_RELEASED_EVENT = 1;
    public static final int TIMER_EXPIRED_EVENT = 7;
    public static final int MEDIA_EVENT = 8;

    private static Frame currentFrame;
    private static int midpAlphaLevels;

    public static void setCurrent(Frame frame) {
        if (frame == null) throw new NullPointerException("frame");
        javax.microedition.midlet.MIDlet midlet = getMidlet();
        javax.microedition.lcdui.Display display = midlet == null ? null : javax.microedition.lcdui.Display.getDisplay(midlet);
        if (display != null) {
            currentFrame = frame;
            display.setCurrent(frame.__midpDisplayable());
        }
    }

    public static Frame getCurrent() { return currentFrame; }

    public static int getWidth() {
        Frame current = getCurrent();
        return current == null ? 240 : current.getWidth();
    }

    public static int getHeight() {
        Frame current = getCurrent();
        return current == null ? 240 : current.getHeight();
    }

    public static boolean isColor() {
        return true;
    }

    public static int numColors() {
        return 65536;
    }

    private static javax.microedition.midlet.MIDlet getMidlet() {
        return DisplayMidletHolder.midlet;
    }

    /**
     * DoJa 保證圖片支援 256 階半透明（8bit alpha），但 MIDP 可能只支援 完全透明/不透明。
     * 筆記：請保持 package 的 private，不可令其暴露出放進 DoJa 的公開 API 裡。
     */
    static int __midpNumAlphaLevels() {
        int cached = midpAlphaLevels;
        if (cached != 0) return cached;
        javax.microedition.midlet.MIDlet midlet = getMidlet();
        if (midlet == null) return 2;
        try {
            int levels = javax.microedition.lcdui.Display.getDisplay(midlet).numAlphaLevels();
            cached = levels < 2 ? 2 : levels;
        } catch (Throwable ignored) {
            cached = 2;
        }
        midpAlphaLevels = cached;
        return cached;
    }

    /** MIDP 振動橋接；裝置不支援時回傳 false。 */
    static boolean __midpVibrate(int duration) {
        javax.microedition.midlet.MIDlet midlet = getMidlet();
        if (midlet == null) return false;
        try {
            return javax.microedition.lcdui.Display.getDisplay(midlet).vibrate(duration);
        } catch (RuntimeException ignored) {
            return false;
        }
    }


    static void setMidlet(javax.microedition.midlet.MIDlet midlet) {
        DisplayMidletHolder.midlet = midlet;
    }

    private static final class DisplayMidletHolder {
        static javax.microedition.midlet.MIDlet midlet;
    }
}
