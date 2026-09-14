# 架構

Runtime 將 DoJa 執行期 API 與行為映射到 MIDP 2.0 / CLDC 1.1。

> **Class**：原版遊戲類
> **com.nttdocomo.***：提供 DoJa to MIDP API
> **doja.**：和 MIDP 對接的後端
> **MIDP 2.0 / CLDC 1.1**：最終執行環境

## 分層

針對 DoJa 遊戲的運作需求，轉譯層定義了以下類：

```text
ApplicationDescriptor  JAM metadata
MidpFrameHost          Canvas、輸入與畫面提交
Resources              JAR 資源存取
Sjis                   Shift-JIS 解碼
SoundPolicy            遊戲聲音分類
ImageProvider          遊戲圖片來源
```

遊戲專屬實作由 Wrapper 的 `comp/runtime` 接入這些介面。


## 圖形與畫面提交

### 繪圖

1. 一般繪製直接調用 MIDP 原生繪圖 API；
2. ADD、SUB 或 Alpha 混色等需要讀取目標像素（Destination Pixel）的操作，切換至軟體光柵處理；
3. 熱路徑採用矩形批次回讀（Batch Readback）、單色查找表（Solid-color LUT）以及整數運算（Not Float）。


### 畫面提交

使用單一可變 Framebuffer：

1. 遊戲修改 Framebuffer
2. 呼叫 `unlock` / `syncUnlock`
3. 觸發 `repaint` + `serviceRepaints`
4. 透過 `paint` 將完整內容提交至 Display

系統在當前幀完全提交後，才會開始處理下一個畫面幀，以在維持效能的同時，避免畫面撕裂。


### 特殊支援

1. Graphics2 的仿射變換實作 drawImage 遵循 N 系列機型 DoJa 規格；
2. GIF 支援調色盤、透明索引、交錯掃描（Interlace）與清理方式（Disposal）。


## Scratchpad

Scratchpad 是一個可隨意讀寫的資料塊：

1. JAR 檔內的唯讀基底；
2. MIDlet 的 RMS 寫入覆蓋層。

系統利用 `SPBM` Metadata 來追蹤並記錄實際寫入的 4096-byte 資料區塊。

若 Wrapper 抽取 SP 資產，可直接省略相應的資料區塊，其餘未抽離部分繼續維持原有的位址存取語義。

## 聲音

播放狀態分為 logical clock 與 MMAPI Player。

靜音時，只維持 logical clock，減輕真機的 CPU 消耗。

遊戲需要 BGM / SFX 分類時，可在 Wrapper 實作 `SoundPolicy` 並交給 `AudioPresenter`。