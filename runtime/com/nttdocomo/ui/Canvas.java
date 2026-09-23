package com.nttdocomo.ui;

import com.nttdocomo.opt.ui.Graphics2;
import doja.Graphics2Impl;

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
    private boolean midpPaintCallback;
    private final Object callbackLock = new Object();
    private final Object eventQueueLock = new Object();
    private Event firstEvent;
    private Event lastEvent;
    private Thread eventThread;

    public Canvas() { setFullScreenMode(true); }

    public Graphics getGraphics() {
        if (dojaGraphics == null) {
            dojaGraphics = new Graphics2Impl();
            dojaGraphics.parentCanvas = this;
        }
        return dojaGraphics;
    }

    public void __midpPaint(javax.microedition.lcdui.Graphics g) {
        Graphics2 graphics = (Graphics2)getGraphics();
        synchronized (callbackLock) {
            synchronized (graphics) {
                midpPaintCallback = true;
                try {
                    paint(graphics);
                } finally {
                    midpPaintCallback = false;
                }
                ((Graphics)graphics).paintDisplay(g);
                SoftKeys.paint(g, this, __midpPhysicalWidth(), __midpPhysicalHeight());
            }
        }
    }

    /** 只提交目前的 DoJa framebuffer，供 Graphics.unlock(true) 使用。 */
    public void __midpPresentPaint(javax.microedition.lcdui.Graphics g) {
        Graphics2 graphics = (Graphics2)getGraphics();
        synchronized (graphics) {
            ((Graphics)graphics).paintDisplay(g);
            SoftKeys.paint(g, this, __midpPhysicalWidth(), __midpPhysicalHeight());
        }
    }

    /** 避免在 paint(Graphics) 期間由 Graphics.unlock(true) 遞迴觸發重繪。 */
    public boolean __midpIsPaintCallback() { return midpPaintCallback; }

    public int getKeypadState() { return keyState; }
    public int getKeypadState(int group) { return keyState; }
    public void processEvent(int type, int param) {}
    public void processIMEEvent(int type, String text) {}
    public void imeOn(String text, int displayMode, int inputMode) {
        Display.__midpStartIme(this, text, inputMode, 256);
    }
    public void imeOn(String text, int displayMode, int inputMode, int inputSize) {
        Display.__midpStartIme(this, text, inputMode, inputSize);
    }
    public void paint(Graphics g) {}
    public void run() {}

    public void __midpKeyPressed(int keyCode) {
        int mask = mapKey(keyCode);
        keyState |= mask;
        if (mask != 0) __midpPostEvent(Display.KEY_PRESSED_EVENT, maskToKeyParam(mask));
    }

    public void __midpKeyReleased(int keyCode) {
        int mask = mapKey(keyCode);
        keyState &= ~mask;
        if (mask != 0) __midpPostEvent(Display.KEY_RELEASED_EVENT, maskToKeyParam(mask));
    }

    public void __midpHidden() { keyState = 0; }

    /** 將 DoJa 事件移出 MIDP UI 回呼執行緒，並依序派送。 */
    public void __midpPostEvent(int type, int param) {
        enqueue(new Event(type, param, null, false));
    }

    /** 計時器沿用原本的同步派送節奏，避免回呼尚未完成時堆積重複事件。 */
    public void __midpPostEventAndWait(int type, int param) {
        Event event = new Event(type, param, null, false);
        enqueue(event);
        event.awaitCompletion();
    }

    /** IME 完成通知也必須和按鍵、計時器事件依序派送。 */
    static void __midpPostImeEvent(Canvas owner, int type, String text) {
        owner.enqueue(new Event(type, 0, text, true));
    }

    private void enqueue(Event event) {
        synchronized (eventQueueLock) {
            if (lastEvent == null) firstEvent = event;
            else lastEvent.next = event;
            lastEvent = event;
            if (eventThread == null) startEventThread();
            eventQueueLock.notifyAll();
        }
    }

    private void dispatchEvents() {
        try {
            for (;;) {
                Event event;
                synchronized (eventQueueLock) {
                    while (firstEvent == null) {
                        try { eventQueueLock.wait(30000L); }
                        catch (InterruptedException ignored) {}
                        if (firstEvent == null) return;
                    }
                    event = firstEvent;
                    firstEvent = event.next;
                    if (firstEvent == null) lastEvent = null;
                }
                try {
                    synchronized (callbackLock) {
                        if (event.ime) processIMEEvent(event.type, event.text);
                        else processEvent(event.type, event.param);
                    }
                } finally {
                    event.complete();
                }
            }
        } finally {
            synchronized (eventQueueLock) {
                eventThread = null;
                if (firstEvent != null) startEventThread();
            }
        }
    }

    private void startEventThread() {
        eventThread = new Thread(new Runnable() {
            public void run() { dispatchEvents(); }
        });
        eventThread.start();
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

    private static final class Event {
        final int type;
        final int param;
        final String text;
        final boolean ime;
        Event next;
        private boolean completed;

        Event(int type, int param, String text, boolean ime) {
            this.type = type;
            this.param = param;
            this.text = text;
            this.ime = ime;
        }

        synchronized void awaitCompletion() {
            while (!completed) {
                try { wait(); }
                catch (InterruptedException ignored) {}
            }
        }

        synchronized void complete() {
            completed = true;
            notifyAll();
        }
    }
}
