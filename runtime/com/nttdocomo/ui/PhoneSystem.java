package com.nttdocomo.ui;

public final class PhoneSystem {
    private PhoneSystem() {
    }

    public static void setAttribute(int attribute, int value) {
        /* MIDP 對背光、震動等屬性的支援良莠不一，直接忽略吧，沒必要浪費時間適配各種手機。 */
    }
}
