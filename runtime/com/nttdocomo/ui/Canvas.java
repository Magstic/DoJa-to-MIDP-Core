package com.nttdocomo.ui;

import com.nttdocomo.opt.ui.Graphics2;

public abstract class Canvas extends Frame implements Runnable {
    public static final int KEY_LEFT = 16;
    public static final int KEY_UP = 17;
    public static final int KEY_RIGHT = 18;
    public static final int KEY_DOWN = 19;
    public static final int KEY_SELECT = 20;
    public static final int KEY_SOFT1 = 21;
    public static final int KEY_SOFT2 = 22;

    private Graphics2 dojaGraphics;
    private volatile int keyState;

    public Canvas() { setFullScreenMode(true); }

    public Graphics getGraphics() {
        if (dojaGraphics == null) {
            dojaGraphics = new Graphics2();
            dojaGraphics.parentCanvas = this;
        }
        return dojaGraphics;
    }

    public void __midpPaint(javax.microedition.lcdui.Graphics g) {
        if (dojaGraphics == null) {
            SoftKeys.paint(g, this, getWidth(), getHeight());
            return;
        }
        synchronized (dojaGraphics) {
            ((Graphics)dojaGraphics).paintDisplay(g);
            SoftKeys.paint(g, this, getWidth(), getHeight());
        }
    }

    public int getKeypadState() { return keyState; }
    public int getKeypadState(int group) { return keyState; }
    public void processEvent(int type, int param) {}
    public void processIMEEvent(int type, String text) {}
    public void imeOn(String text, int displayMode, int inputMode) { processIMEEvent(0, text == null ? "" : text); }
    public void imeOn(String text, int displayMode, int inputMode, int inputSize) { imeOn(text, displayMode, inputMode); }
    public void paint(Graphics g) {}
    public void run() {}

    public void __midpKeyPressed(int keyCode) {
        int mask = mapKey(keyCode);
        keyState |= mask;
        if (mask != 0) processEvent(Display.KEY_PRESSED_EVENT, maskToKeyParam(mask));
    }

    public void __midpKeyReleased(int keyCode) {
        int mask = mapKey(keyCode);
        keyState &= ~mask;
        if (mask != 0) processEvent(Display.KEY_RELEASED_EVENT, maskToKeyParam(mask));
    }

    private int maskToKeyParam(int mask) {
        for (int i = 0; i < 31; i++) if (mask == (1 << i)) return i;
        return 0;
    }

    private int mapKey(int keyCode) {
        int action = __midpGameAction(keyCode);
        switch (action) {
            case javax.microedition.lcdui.Canvas.UP: return 1 << Display.KEY_UP;
            case javax.microedition.lcdui.Canvas.DOWN: return 1 << Display.KEY_DOWN;
            case javax.microedition.lcdui.Canvas.LEFT: return 1 << Display.KEY_LEFT;
            case javax.microedition.lcdui.Canvas.RIGHT: return 1 << Display.KEY_RIGHT;
            case javax.microedition.lcdui.Canvas.FIRE: return 1 << Display.KEY_SELECT;
        }
        if (keyCode == -6 || keyCode == -21) return 1 << Display.KEY_SOFT1;
        if (keyCode == -7 || keyCode == -22) return 1 << Display.KEY_SOFT2;
        if (keyCode >= '0' && keyCode <= '9') return 1 << (keyCode - '0');
        if (keyCode == '*') return 1 << Display.KEY_ASTERISK;
        if (keyCode == '#') return 1 << Display.KEY_POUND;
        return 0;
    }
}
