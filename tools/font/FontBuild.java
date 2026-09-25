package doja.tools.font;

import doja.tools.io.FileIO;

import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.TreeSet;

/** 根據使用字形的多少，建立『渲染字形』與『Shift-JIS 解碼器』資源。 */
public final class FontBuild {
    private static final int PIXEL_SIZE = 12;

    private FontBuild() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: FontBuild <font-file> <out-dir> [source-dir ...]");
        }

        File fontInput = new File(args[0]);
        File outDir = new File(args[1]);
        File[] sourceDirs = new File[Math.max(0, args.length - 2)];
        for (int i = 0; i < sourceDirs.length; i++) sourceDirs[i] = new File(args[i + 2]);

        File fontFile = FontFiles.find(fontInput);
        if (fontFile == null) throw new IOException("No .ttf or .otf font found at " + fontInput.getPath());
        FileIO.ensureDirectory(outDir);

        Font font = FontFiles.load(fontFile).deriveFont((float)PIXEL_SIZE);
        TreeSet<Integer> glyphs;
        TreeSet<Integer> shiftJisCodes;
        try {
            FontUsageCollector.Result usage = FontUsageCollector.collect(sourceDirs);
            glyphs = usage.glyphs;
            shiftJisCodes = usage.shiftJisCodes;
        } catch (IOException noManifest) {
            if (noManifest.getMessage() == null || noManifest.getMessage().indexOf("no font-usage.bin") < 0) {
                throw noManifest;
            }
            glyphs = FontSourceScanner.collect(font, Collections.<Integer,Integer>emptyMap(), sourceDirs);
            shiftJisCodes = new TreeSet<Integer>();
            System.out.println("FontBuild: no font-usage.bin; scanned source resources directly");
        }
        addDefaultEnglish(glyphs, shiftJisCodes);
        int sjisCount = ShiftJisTable.write(shiftJisCodes, new File(outDir, "sjis_map.bin"), glyphs);
        int glyphCount = BitmapFontWriter.write(font, glyphs, new File(outDir, "glyphs.bin"));
        System.out.println("FontBuild: " + fontFile.getName() + ", glyphs=" + glyphCount + ", sjis=" + sjisCount);
    }

    private static void addDefaultEnglish(TreeSet<Integer> glyphs, TreeSet<Integer> shiftJisCodes) {
        for (int i = 0; i < 26; i++) {
            glyphs.add(Integer.valueOf('A' + i));
            glyphs.add(Integer.valueOf('a' + i));
            glyphs.add(Integer.valueOf(0xFF21 + i));
            glyphs.add(Integer.valueOf(0xFF41 + i));
            shiftJisCodes.add(Integer.valueOf(0x8260 + i));
            shiftJisCodes.add(Integer.valueOf(0x8281 + i));
        }
    }
}
