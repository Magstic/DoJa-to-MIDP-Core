# 架構

Tools 負責 Wrapper 在建置時的各項處理工作。

```text
DoJa MIDP Tools  ←  Wrapper comp/*
        ↓
JAM、class、Scratchpad、font、image、verification
```


## 模組

### Class file

ClassFile 是統一的 Class 檔案解析與寫入器。

該模塊負責處理 Constant Pool、Modified UTF-8、類成員、Code 屬性以及 Bytecode 的檢查與修改。

常用工具：

```text
Utf8Patch             修改 CONSTANT_Utf8
SjisPatch             重導 Shift-JIS decode
MethodRefPatch        重導 method reference
StaticFieldReadPatch  將 GETSTATIC 重導為 INVOKESTATIC
```

translation 下的 `ClassText` 也建立在該解析器上。

### Translation

```text
TsvCodec             TSV escape / unescape
TranslationTable     原文與譯文
TextIndex            出現位置索引
ResolvedTextTable    最終驗證資料
ClassText            CONSTANT_String 抽取、替換與驗證
RawTranslationResource  Runtime raw-byte translation codec
```

SP、控制碼與指標重定位等格式規則，交給專屬 Wrapper 自行實作。


### 圖片

`GifImage` 負責解析內嵌 GIF 檔、邏輯畫布（Logical canvas）、調色盤、透明度以及編碼資料的 Hash。

SP 偏移（Offset）、Sprite 插槽與執行期索引（Runtime index）皆由專屬 Wrapper 自行實作。


### Scratchpad

```text
Scratchpad.java       提供 Logical address-space 的存取 API
ResourceFormats.java  ZIP / GIF / BMP / PNG / MLD 掃描
SoundConverter.java   MLD → MIDI / WAV
MidiToWav.java        MIDI → WAV（特別是 SFX 槽）
SoundIndex.java       聲音長度索引
ScratchpadPackager    baseline / archive / index 封裝
ScratchpadBuild.java  CLI 與流程
```

軟體專屬 Wrapper 需在建置時將自身的 Schema Class 明確傳入 ScratchpadBuild。


### JAM 與字型

`doja.tools.jam` 解析 JAM，產生 Runtime descriptor 與 MIDP metadata

`doja.tools.font` 負責下載字型，並根據使用清單建立位圖字庫與 Shift-JIS 解碼對照表。

Wrapper 以 `FontUsage` 分別記錄『顯示字元』與『待解碼的 Shift-JIS 編碼』，供 FontBuild 打包。
解碼表只收錄清單中的雙位元組編碼，字庫同步收錄其解碼結果，確保解碼後有對應字形。

`GlyphResolver` 統一選用原字形或安全替代字形，保留原字元編碼。
無可用字形時以『口』代替，並在打包日誌列出缺字；字型沒有『口』時直接產生口形位圖。
字庫固定包含『口』，供執行期缺字時使用。

半形假名、動態文字與譯文所需的字形，由 Wrapper 記入 `addRenderText`；已翻譯的文字無須加入原文解碼表。
未列入清單的動態文字，無法在打包時檢查缺字。


## 公共 API

專屬的 `comp/* Wrapper` 可直接調用：

- Class 檔案：`ClassFile`、`MethodRefPatch`、`StaticFieldReadPatch`
- Scratchpad：`Scratchpad`
- 翻譯機制：`ClassText`、`TranslationTable`、`TextOccurrence`、`TextIndex`、`ResolvedText`、`ResolvedTextTable`、`RawTranslationResource`
- 字型需求：`FontUsage`
- 圖片：`GifImage`
- 共用工具：`TsvCodec`、`FileIO`
