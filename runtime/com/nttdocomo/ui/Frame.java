package com.nttdocomo.ui;

import doja.MidpFrameHost;

/**
 * DoJa Frame 的外觀。
 * 實際顯示與按鍵事件交給內部的 MIDP Canvas host。
 * */
public abstract class Frame {
    public static final int SOFT_KEY_1 = 0;
    public static final int SOFT_KEY_2 = 1;

    private final String[] softLabels = new String[2];
    private int backgroundColor = Graphics.getColorOfName(Graphics.BLACK);
    private boolean softLabelVisible = true;
    private final MidpFrameHost host;

    public Frame() { host = new MidpFrameHost(this); }

    public int getWidth() { return host.getWidth(); }
    public int getHeight() { return host.getHeight(); }
    public void repaint() { host.repaint(); }
    public void repaint(int x, int y, int width, int height) { host.repaint(x, y, width, height); }
    public void setFullScreenMode(boolean full) { host.setFullScreenMode(full); }
    public boolean isShown() { return host.isShown(); }

    public void setBackground(int c) { backgroundColor = c; }
    public int getBackground() { return backgroundColor; }

    public void setSoftLabel(int index, String label) {
        if (index >= 0 && index < softLabels.length) { softLabels[index] = label; repaint(); }
    }
    public String getSoftLabel(int index) { return index < 0 || index >= softLabels.length ? null : softLabels[index]; }
    public void setSoftLabelVisible(boolean visible) { softLabelVisible = visible; repaint(); }
    public boolean isSoftLabelVisible() { return softLabelVisible; }

    public final javax.microedition.lcdui.Displayable __midpDisplayable() { return host; }
    public final int __midpGameAction(int keyCode) {
        try { return host.getGameAction(keyCode); } catch (Exception ignored) { return 0; }
    }
    public final void __midpPresent() {
        PhoneSystem.__midpRefreshVibration();
        host.present();
    }
    public final int __midpPhysicalWidth() { return host.getWidth(); }
    public final int __midpPhysicalHeight() { return host.getHeight(); }

    /** MIDP host 從這兩個入口把按鍵事件送回遊戲 Frame。 */
    public void __midpKeyPressed(int keyCode) {}
    public void __midpKeyReleased(int keyCode) {}
    public void __midpPaint(javax.microedition.lcdui.Graphics g) {
        SoftKeys.paint(g, this, getWidth(), getHeight());
    }
}
