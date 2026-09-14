package doja;

import com.nttdocomo.ui.Frame;

/**
 * MIDP Canvas 回呼的終點，然後轉給 Frame 處理。
 * 使用組合包住 Canvas，這層 DoJa API 才不會被綁死在 LCDUI 上。
 */
public final class MidpFrameHost extends javax.microedition.lcdui.Canvas {
    private final Frame owner;

    public MidpFrameHost(Frame owner) {
        this.owner = owner;
    }

    protected void paint(javax.microedition.lcdui.Graphics g) {
        owner.__midpPaint(g);
    }

    /** 提交完整 framebuffer 並等 paint 跑完，避免畫面撕裂 */
    public void present() {
        repaint();
        serviceRepaints();
    }

    protected void keyPressed(int keyCode) { owner.__midpKeyPressed(keyCode); }
    protected void keyReleased(int keyCode) { owner.__midpKeyReleased(keyCode); }
}
