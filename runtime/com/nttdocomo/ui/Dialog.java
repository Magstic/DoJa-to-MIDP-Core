package com.nttdocomo.ui;

/** DoJa Dialog 的基本實作，先涵蓋遊戲常用的訊息與確認按鈕。 */
public class Dialog {
    public static final int DIALOG_INFO = 0;
    public static final int DIALOG_WARNING = 1;
    public static final int DIALOG_ERROR = 2;

    public Dialog(int type, String title) {}

    public void setText(String text) {}

    public int show() { return 0; }
}
