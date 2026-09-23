package doja;

import com.nttdocomo.ui.Frame;

/**
 * MIDP Canvas 回呼的終點，然後轉給 Frame 處理。
 * 使用組合包住 Canvas，這層 DoJa API 才不會被綁死在 LCDUI 上。
 */
public final class MidpFrameHost extends javax.microedition.lcdui.Canvas {
    private static Thread eventThread;
    private static int eventDepth;

    private final Frame owner;
    private boolean presentOnly;

    public MidpFrameHost(Frame owner) {
        this.owner = owner;
    }

    protected void paint(javax.microedition.lcdui.Graphics g) {
        enterEventCallback();
        try {
            boolean directPresent;
            synchronized (this) {
                directPresent = presentOnly;
                presentOnly = false;
            }
            if (directPresent) owner.__midpPresentPaint(g);
            else owner.__midpPaint(g);
        } finally {
            leaveEventCallback();
        }
    }

    /** 提交完整 framebuffer 並等 paint 跑完，避免畫面撕裂。這不是 Canvas.repaint()，不可再次呼叫遊戲 paint(Graphics)。 */
    public void present() {
        synchronized (this) { presentOnly = true; }
        repaint();
        serviceRepaints();
    }

    protected void keyPressed(int keyCode) {
        enterEventCallback();
        try { owner.__midpKeyPressed(keyCode); }
        finally { leaveEventCallback(); }
    }

    protected void keyReleased(int keyCode) {
        enterEventCallback();
        try { owner.__midpKeyReleased(keyCode); }
        finally { leaveEventCallback(); }
    }

    protected void hideNotify() {
        enterEventCallback();
        try { owner.__midpHidden(); }
        finally { leaveEventCallback(); }
    }

    /** Dialog.show() 不可阻塞目前正在派送 MIDP UI 回呼的執行緒。 */
    public static synchronized boolean isEventThread() {
        return eventThread == Thread.currentThread() && eventDepth > 0;
    }

    private static synchronized void enterEventCallback() {
        Thread current = Thread.currentThread();
        if (eventThread == current) eventDepth++;
        else {
            eventThread = current;
            eventDepth = 1;
        }
    }

    private static synchronized void leaveEventCallback() {
        if (eventThread != Thread.currentThread()) return;
        eventDepth--;
        if (eventDepth == 0) eventThread = null;
    }
}
