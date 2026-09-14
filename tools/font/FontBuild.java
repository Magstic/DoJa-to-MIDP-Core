package doja.tools.font;

import doja.tools.io.FileIO;

import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.util.Map;

/** Builds two independent resources: final render glyphs and required Shift-JIS decoder mappings. */
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
        FontUsageCollector.Result usage = FontUsageCollector.collect(font, sourceDirs);
        Map<Integer,Integer> sjis = ShiftJisTable.build(usage.shiftJisCodes);

        ShiftJisTable.write(sjis, new File(outDir, "sjis_map.bin"));
        BitmapFontWriter.write(font, usage.glyphs, new File(outDir, "glyphs.bin"));
        System.out.println("FontBuild: " + fontFile.getName() + ", glyphs=" + usage.glyphs.size()
                + ", sjis=" + sjis.size());
    }
}
