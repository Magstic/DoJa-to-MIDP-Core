package doja.tools.font;

import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.TreeSet;

/** Loads explicit font-usage manifests; build-time guesses about source text are deliberately forbidden. */
final class FontUsageCollector {
    static final String USAGE_FILE = "font-usage.bin";

    private FontUsageCollector() {}

    static Result collect(Font font, File[] roots) throws IOException {
        Result result = new Result();
        for (int cp = 0x20; cp <= 0x7E; cp++) add(result.glyphs, font, cp, "ASCII baseline");
        for (int i = 0; i < roots.length; i++) scan(result, font, roots[i]);
        if (result.manifests == 0) throw new IOException("no " + USAGE_FILE + " found in font source roots");
        return result;
    }

    private static void scan(Result result, Font font, File file) throws IOException {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) return;
            Arrays.sort(children, new Comparator<File>() {
                public int compare(File a, File b) { return a.getName().compareTo(b.getName()); }
            });
            for (int i = 0; i < children.length; i++) scan(result, font, children[i]);
            return;
        }
        if (!USAGE_FILE.equals(file.getName())) return;
        FontUsage usage = FontUsage.read(file);
        for (Integer cp : usage.renderCodePoints()) add(result.glyphs, font, cp.intValue(), file.getPath());
        result.shiftJisCodes.addAll(usage.shiftJisCodes());
        result.manifests++;
    }

    private static void add(TreeSet<Integer> glyphs, Font font, int cp, String source) throws IOException {
        if (cp <= 0 || cp > 0xFFFF) throw new IOException(source + ": unsupported glyph U+" + hex4(cp));
        char c = (char)cp;
        if (!font.canDisplay(c)) throw new IOException(source + ": font cannot display U+" + hex4(cp));
        glyphs.add(Integer.valueOf(cp));
    }

    private static String hex4(int value) {
        String text = Integer.toHexString(value).toUpperCase();
        while (text.length() < 4) text = "0" + text;
        return text;
    }

    static final class Result {
        final TreeSet<Integer> glyphs = new TreeSet<Integer>();
        final TreeSet<Integer> shiftJisCodes = new TreeSet<Integer>();
        int manifests;
    }
}
