package com.nttdocomo.ui;

// 繼承與保留 DoJa 標準的 MediaListener 介面架構，使 Presenter 能以原遊戲熟悉的事件機制回報媒體狀態。
public interface MediaListener {
    void mediaAction(MediaPresenter source, int type, int option);
}
