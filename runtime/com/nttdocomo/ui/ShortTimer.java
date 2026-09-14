package com.nttdocomo.ui;

import java.util.Vector;
import com.nttdocomo.util.TimeKeeper;

public final class ShortTimer implements TimeKeeper, Runnable {
    public static final int EVENT_TIMER = 1;
    private static final int RESOLUTION_MS = 1;
    private static final int MIN_INTERVAL_MS = 1;
    private static final Vector timers = new Vector();

    private final Canvas canvas;
    private final int id;
    private final int interval;
    private final boolean repeat;
    private boolean running;
    private boolean disposed;
    private Thread thread;

    protected ShortTimer() {
        canvas = null;
        id = 0;
        interval = MIN_INTERVAL_MS;
        repeat = false;
        disposed = true;
    }

    private ShortTimer(Canvas canvas, int id, int interval, boolean repeat) {
        this.canvas = canvas;
        this.id = id;
        this.interval = interval;
        this.repeat = repeat;
    }

    public static ShortTimer getShortTimer(Canvas canvas, int id, int time, boolean repeat) {
        if (canvas == null) throw new NullPointerException("canvas");
        if (time < 0) throw new IllegalArgumentException("negative timer interval");
        int interval = normalizeInterval(time);
        synchronized (timers) {
            for (int i = 0; i < timers.size(); i++) {
                ShortTimer timer = (ShortTimer)timers.elementAt(i);
                if (!timer.disposed && timer.canvas == canvas && timer.id == id) {
                    throw new UIException(UIException.BUSY_RESOURCE, "timer id already in use");
                }
            }
            ShortTimer timer = new ShortTimer(canvas, id, interval, repeat);
            timers.addElement(timer);
            return timer;
        }
    }

    public int getResolution() {
        ensureUsable();
        return RESOLUTION_MS;
    }

    public int getMinTimeInterval() {
        ensureUsable();
        return MIN_INTERVAL_MS;
    }

    public synchronized void start() {
        ensureUsable();
        if (running) throw new UIException(UIException.ILLEGAL_STATE, "timer already running");
        running = true;
        thread = new Thread(this);
        thread.start();
    }

    public synchronized void stop() {
        ensureUsable();
        running = false;
    }

    public void dispose() {
        synchronized (this) {
            if (disposed) return;
            running = false;
            disposed = true;
            thread = null;
        }
        synchronized (timers) {
            timers.removeElement(this);
        }
    }

    public void run() {
        for (;;) {
            try {
                Thread.sleep(interval);
            } catch (InterruptedException ignored) {
            }

            synchronized (this) {
                if (!running || disposed) return;
            }

            canvas.processEvent(Display.TIMER_EXPIRED_EVENT, id);

            synchronized (this) {
                if (!repeat) {
                    running = false;
                    return;
                }
            }
        }
    }

    private void ensureUsable() {
        if (disposed) throw new UIException(UIException.ILLEGAL_STATE, "timer disposed");
    }

    private static int normalizeInterval(int time) {
        if (time <= MIN_INTERVAL_MS) return MIN_INTERVAL_MS;
        int remainder = (time - MIN_INTERVAL_MS) % RESOLUTION_MS;
        return remainder == 0 ? time : time + (RESOLUTION_MS - remainder);
    }
}
