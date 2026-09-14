package com.nttdocomo.util;

public interface TimeKeeper {
    int getResolution();
    int getMinTimeInterval();
    void start();
    void stop();
    void dispose();
}
