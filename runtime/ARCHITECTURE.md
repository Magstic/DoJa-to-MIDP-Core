# 架構

Runtime 將 DoJa 執行期 API 與行為映射到 MIDP 2.0 / CLDC 1.1。

> **Class**：原版遊戲類
> **com.nttdocomo.***：提供 DoJa to MIDP API
> **doja.**：和 MIDP 對接的後端
> **MIDP 2.0 / CLDC 1.1**：最終執行環境

## 分層

Runtime 主要由以下後端元件組成：

```text
ApplicationDescriptor  JAM metadata
MidpFrameHost          Canvas、輸入與畫面提交
Resources              JAR 資源存取
Sjis                   Shift-JIS 解碼與翻譯入口
TextTranslations       原始 Shift-JIS byte 對應 Unicode 譯文
SoundPolicy            遊戲聲音分類
ImageProvider          遊戲圖片來源介面
ImageResource          圖片來源的 Runtime 資源介面
ImageRuntime           圖片 Provider 載入與分派
Graphics2Impl          Graphics2 的 Runtime 實體
ConnectorProxy         resource、scratchpad 與 MIDP Connection 對接
SoundPlayer            聲音播放狀態與 MMAPI 控制
```

遊戲專屬實作由 Wrapper 的 `comp/runtime` 接入。


## 圖形與畫面提交

### 繪圖

1. 一般繪製直接調用 MIDP 原生繪圖 API；
2. ADD、SUB 等需要 **目標像素參與運算** 的操作，切換至軟體光柵處理；
3. 熱路徑採用矩形批次回讀（Batch Readback）、單色查找表（Solid-color LUT）以及整數運算（Not Float）。


### 畫面提交

使用單一可變 Framebuffer：

1. 遊戲修改 Framebuffer
2. 呼叫 `unlock` / `syncUnlock`
3. 觸發 `repaint` + `serviceRepaints`
4. 透過 `paint` 將完整內容提交至 Display

系統在當前幀完全提交後，才會開始處理下一個畫面幀，以在維持效能的同時，避免畫面撕裂。


### 特殊支援

1. Graphics2 的仿射變換實作 drawImage 遵循 DoJa 5.1 Optional API；
2. GIF 支援調色盤、透明索引、交錯掃描（Interlace）與清理方式（Disposal）。


## 字型與文字

繪字與量字使用 Unicode；Shift-JIS 文字由 Wrapper 先調用 `Sjis.decode` 解碼。
若原始位元組存於 String，須先還原成 byte 陣列。缺少對照的雙位元組字元以『口』代替，並跳過整個字元。

字庫以 12px 製作，依 Font 的高度做最近鄰縮放，量字與繪字使用相同字寬。
Graphics 建立時取得預設 Font；SoftKeys 使用字庫原始尺寸。

### 支援範圍

- 支援 BMP 字元、單色字形與單一字面及樣式；不支援文字塑形或彩色圖示。
- Tiny/Small/Medium/Large 分別為 12/14/16/20px，也可指定數值高度。
  各 DoJa 世代的字級定義不同，適配時須確認目標 Profile（見 [DOCOMO 開發指南第 81 頁](https://www.docomo.ne.jp/english/binary/pdf/service/developer/make/content/iappli/technical_data/doja/jguidefordoja5_x_en_080527.pdf#page=81)）。

## Scratchpad

Scratchpad 是一個可隨意讀寫的資料塊：

1. JAR 檔內的唯讀基底；
2. MIDlet 的 RMS 寫入覆蓋層。

系統利用 SPBM Metadata 描述 Scratchpad 的唯讀基底分塊，實際寫入內容由 RMS overlay 保存。

若 Wrapper 抽取 SP 資產，可直接省略相應的資料區塊，其餘未抽離部分繼續維持原有的位址存取語義。

## 聲音

播放狀態由 logical clock 維持，實際發聲由 MMAPI Player 負責。

靜音時，只維持 logical clock，減輕真機的 CPU 消耗。

遊戲需要 BGM / SFX 分類時，可在 Wrapper 實作 `SoundPolicy` 並交給 `AudioPresenter`。
