package doja.tools.font;

import doja.tools.classfile.ClassFile;
import doja.tools.io.FileIO;

import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
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
        else if (name.endsWith(".txt")) scanPlainText(glyphs, font, decodeText(file));
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

    private static String decodeText(File file) throws IOException {
        byte[] data = FileIO.read(file);
        String text;
        if (startsWith(data, 0xEF, 0xBB, 0xBF)) {
            text = decodePlainText(data, 3, Charset.forName("UTF-8"));
            if (text != null) return text;
            throw invalidTextEncoding(file);
        }
        if (startsWith(data, 0xFF, 0xFE)) {
            text = decodePlainText(data, 2, Charset.forName("UTF-16LE"));
            if (text != null) return text;
            throw invalidTextEncoding(file);
        }
        if (startsWith(data, 0xFE, 0xFF)) {
            text = decodePlainText(data, 2, Charset.forName("UTF-16BE"));
            if (text != null) return text;
            throw invalidTextEncoding(file);
        }

        String utf16 = detectBomlessUtf16(data);
        if (utf16 != null) {
            text = decodePlainText(data, 0, Charset.forName(utf16));
            if (text != null) return text;
        }
        text = decodePlainText(data, 0, Charset.forName("UTF-8"));
        if (text != null) return text;
        text = decodePlainText(data, 0, ShiftJisTable.charset());
        if (text != null) return text;
        throw invalidTextEncoding(file);
    }

    /** 無 BOM 時只在位元組排列具有明確特徵時判定為 UTF-16。 */
    private static String detectBomlessUtf16(byte[] data) {
        if (data.length >= 4 && (data.length & 1) == 0) {
            int evenZero = 0;
            int oddZero = 0;
            boolean[] evenValues = new boolean[256];
            boolean[] oddValues = new boolean[256];
            int evenDistinct = 0;
            int oddDistinct = 0;
            for (int i = 0; i < data.length; i += 2) {
                int even = data[i] & 255;
                int odd = data[i + 1] & 255;
                if (even == 0) evenZero++;
                if (odd == 0) oddZero++;
                if (!evenValues[even]) { evenValues[even] = true; evenDistinct++; }
                if (!oddValues[odd]) { oddValues[odd] = true; oddDistinct++; }
            }
            if (oddZero >= 2 && evenZero * 4 <= oddZero) return "UTF-16LE";
            if (evenZero >= 2 && oddZero * 4 <= evenZero) return "UTF-16BE";
            if (oddDistinct * 2 <= evenDistinct) return "UTF-16LE";
            if (evenDistinct * 2 <= oddDistinct) return "UTF-16BE";
        }
        return null;
    }

    private static String decodeStrict(byte[] data, int offset, Charset charset)
            throws CharacterCodingException {
        CharsetDecoder decoder = charset.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPORT);
        decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
        return decoder.decode(ByteBuffer.wrap(data, offset, data.length - offset)).toString();
    }

    private static String decodePlainText(byte[] data, int offset, Charset charset) {
        try {
            String text = decodeStrict(data, offset, charset);
            return isPlainText(text) ? text : null;
        } catch (CharacterCodingException invalid) {
            return null;
        }
    }

    private static boolean isPlainText(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '\n' && c != '\r' && c != '\t' && Character.isISOControl(c)) return false;
        }
        return true;
    }

    private static IOException invalidTextEncoding(File file) {
        return new IOException("cannot determine TXT encoding (UTF-8, UTF-16 or Shift-JIS): " + file);
    }

    private static boolean startsWith(byte[] data, int... prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if ((data[i] & 255) != prefix[i]) return false;
        }
        return true;
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
