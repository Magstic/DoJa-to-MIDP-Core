package doja.tools.font;

import java.awt.Font;

/** 為需要顯示的字元選擇可用字形，缺字時使用替代字形或『口』。 */
final class GlyphResolver {
    static final int PLACEHOLDER_CODE_POINT = 0x53E3; // 缺字代用字『口』。

    private GlyphResolver() {}

    static int resolve(Font font, int codePoint) {
        if (font == null) throw new NullPointerException("font");
        if (codePoint <= 0 || codePoint > 0xFFFF) return -1;
        if (font.canDisplay((char)codePoint)) return codePoint;

        switch (codePoint) {
        case 0x2212: // 減號『−』。
            return firstDisplayable(font, 0xFF0D, 0x002D); // 依序嘗試『－』與『-』。
        case 0xFF0D: // 全形減號『－』。
            return firstDisplayable(font, 0x002D);
        case 0x2715: // 叉號『✕』。
            return firstDisplayable(font, 0x00D7, 0x0058); // 依序嘗試乘號『×』與字母『X』。
        default:
            return PLACEHOLDER_CODE_POINT;
        }
    }

    static boolean usedPlaceholder(Font font, int logicalCodePoint, int renderCodePoint) {
        return renderCodePoint == PLACEHOLDER_CODE_POINT && !font.canDisplay((char)logicalCodePoint);
    }

    static String codePoint(int value) {
        String text = Integer.toHexString(value).toUpperCase();
        while (text.length() < 4) text = "0" + text;
        return "U+" + text;
    }

    private static int firstDisplayable(Font font, int first) {
        return font.canDisplay((char)first) ? first : PLACEHOLDER_CODE_POINT;
    }

    private static int firstDisplayable(Font font, int first, int second) {
        if (font.canDisplay((char)first)) return first;
        return firstDisplayable(font, second);
    }
}
