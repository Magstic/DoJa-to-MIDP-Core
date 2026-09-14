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

`doja.tools.font` 字型拉取、明確的 FontUsage manifest、位圖字庫與按需 Shift-JIS 對照表。

FontBuild 不再從 TSV、Java source 或完整 Shift-JIS charset 猜測 glyph。`FontUsage` 將
最終 render code point 與仍需 Runtime 解碼的雙 byte Shift-JIS code 分開記錄；專屬 comp
只需把它實際理解的遊戲文字結構轉成這份通用 manifest。


## 公共 API

專屬的 `comp/* Wrapper` 可直接調用：

- Class 檔案：`ClassFile`、`MethodRefPatch`、`StaticFieldReadPatch`
- Scratchpad：`Scratchpad`
- 翻譯機制：`ClassText`、`TranslationTable`、`TextOccurrence`、`TextIndex`、`ResolvedText`、`ResolvedTextTable`、`RawTranslationResource`
- 字型需求：`FontUsage`
- 圖片：`GifImage`
- 共用工具：`TsvCodec`、`FileIO`