package doja.tools.font;

import doja.tools.classfile.ClassFile;
import doja.tools.io.FileIO;

import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

/** 掃描遊戲與生成資源裡會顯示的文字，只把實際需要的 glyph 烘進 Runtime 字型。 */
final class FontSourceScanner {
    private FontSourceScanner() {}

    static TreeSet<Integer> collect(Font font, Map<Integer,Integer> sjis, File[] roots) throws IOException {
        TreeSet<Integer> glyphs = new TreeSet<Integer>();
        for (int cp = 0x20; cp <= 0x7E; cp++) add(glyphs, font, cp);
        for (int cp = 0xA0; cp <= 0xFF; cp++) add(glyphs, font, cp);
        for (int cp = 0xFF61; cp <= 0xFF9F; cp++) add(glyphs, font, cp);
        int[] required = { '?', 0x3000, 0x3001, 0x3002, 0x3007, 0x266A, 0x25CB, 0x00D7, 0x2715 };
        for (int i = 0; i < required.length; i++) add(glyphs, font, required[i]);
        for (Iterator<Integer> it = sjis.values().iterator(); it.hasNext();) add(glyphs, font, it.next().intValue());
        for (int i = 0; i < roots.length; i++) scan(glyphs, font, roots[i]);
        glyphs.add(Integer.valueOf('?'));
        return glyphs;
    }

    private static void scan(TreeSet<Integer> glyphs, Font font, File file) throws IOException {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) return;
            Arrays.sort(children, new Comparator<File>() {
                public int compare(File a, File b) { return a.getName().compareTo(b.getName()); }
            });
            for (int i = 0; i < children.length; i++) scan(glyphs, font, children[i]);
            return;
        }

        String name = file.getName();
        if (name.endsWith(".java")) scanJava(glyphs, font, text(file));
        else if (name.endsWith(".tsv")) scanPlainText(glyphs, font, text(file));
        else if (name.endsWith(".class")) scanClass(glyphs, font, file);
        else if (name.equalsIgnoreCase("data.bin")) scanUtf16Le(glyphs, font, FileIO.read(file));
    }

    private static void scanClass(TreeSet<Integer> glyphs, Font font, File file) throws IOException {
        ClassFile cls = ClassFile.read(file);
        for (String value : cls.utf8Values()) addText(glyphs, font, value);
    }

    private static void scanUtf16Le(TreeSet<Integer> glyphs, Font font, byte[] data) {
        for (int p = 0; p + 1 < data.length; p += 2) {
            int cp = (data[p] & 255) | ((data[p + 1] & 255) << 8);
            if (looksLikeText(cp)) add(glyphs, font, cp);
        }
    }

    private static boolean looksLikeText(int cp) {
        return (cp >= 0x2000 && cp <= 0x30ff) || (cp >= 0x3400 && cp <= 0x9fff)
                || (cp >= 0xf900 && cp <= 0xfaff) || (cp >= 0xff00 && cp <= 0xffef);
    }

    private static void scanPlainText(TreeSet<Integer> glyphs, Font font, String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '\n' && c != '\r' && c != '\t') add(glyphs, font, c);
        }
    }

    private static void scanJava(TreeSet<Integer> glyphs, Font font, String source) {
        int i = 0;
        boolean lineComment = false;
        boolean blockComment = false;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (lineComment) {
                if (c == '\n' || c == '\r') lineComment = false;
                i++;
            } else if (blockComment) {
                if (c == '*' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                    blockComment = false;
                    i += 2;
                } else i++;
            } else if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                lineComment = true;
                i += 2;
            } else if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '*') {
                blockComment = true;
                i += 2;
            } else if (c == '"' || c == '\'') {
                i = scanLiteral(glyphs, font, source, i + 1, c);
            } else i++;
        }
    }

    private static int scanLiteral(TreeSet<Integer> glyphs, Font font, String source, int i, char quote) {
        while (i < source.length()) {
            char c = source.charAt(i++);
            if (c == quote) return i;
            if (c == '\\' && i < source.length()) {
                Escape escape = readEscape(source, i);
                c = escape.value;
                i += escape.length;
            }
            if (c != '\n' && c != '\r' && c != '\t') add(glyphs, font, c);
        }
        return i;
    }

    private static Escape readEscape(String source, int i) {
        char c = source.charAt(i);
        if (c == 'u') {
            int p = i;
            while (p < source.length() && source.charAt(p) == 'u') p++;
            if (p + 4 <= source.length()) {
                int value = 0;
                for (int n = 0; n < 4; n++) {
                    int digit = hex(source.charAt(p + n));
                    if (digit < 0) return new Escape(c, 1);
                    value = (value << 4) | digit;
                }
                return new Escape((char)value, (p - i) + 4);
            }
        }
        if (c >= '0' && c <= '7') {
            int value = c - '0';
            int length = 1;
            while (length < 3 && i + length < source.length()) {
                char next = source.charAt(i + length);
                if (next < '0' || next > '7') break;
                value = (value << 3) | (next - '0');
                length++;
            }
            return new Escape((char)value, length);
        }
        switch (c) {
            case 'b': return new Escape('\b', 1);
            case 't': return new Escape('\t', 1);
            case 'n': return new Escape('\n', 1);
            case 'f': return new Escape('\f', 1);
            case 'r': return new Escape('\r', 1);
            case '"': return new Escape('"', 1);
            case '\'': return new Escape('\'', 1);
            case '\\': return new Escape('\\', 1);
            default: return new Escape(c, 1);
        }
    }

    private static int hex(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    }

    private static String text(File file) throws IOException {
        return new String(FileIO.read(file), "UTF-8");
    }

    private static void addText(TreeSet<Integer> glyphs, Font font, String text) {
        for (int i = 0; i < text.length(); i++) add(glyphs, font, text.charAt(i));
    }

    private static void add(TreeSet<Integer> glyphs, Font font, int cp) {
        if (cp > 0 && cp <= 0xFFFF && (font.canDisplay((char)cp) || cp < 0x100 || cp == 0x2715)) {
            glyphs.add(Integer.valueOf(cp));
        }
    }

    private static final class Escape {
        final char value;
        final int length;
        Escape(char value, int length) { this.value = value; this.length = length; }
    }
}
