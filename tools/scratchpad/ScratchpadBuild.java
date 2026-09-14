package doja.tools.scratchpad;

import doja.tools.io.FileIO;
import doja.tools.jam.Jam;

import java.io.File;
import java.io.IOException;

/** 讀取擷取的 .sp、套用可選 Schema，再把整理結果交給 Runtime packager。 */
public final class ScratchpadBuild {
    private ScratchpadBuild() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3 && args.length != 4) {
            throw new IllegalArgumentException("Usage: ScratchpadBuild <app.jam> <app.sp> <generated-dir> [schema-class]");
        }
        String schemaClass = args.length == 4 && args[3].length() != 0 ? args[3] : null;
        build(new File(args[0]), new File(args[1]), new File(args[2]), schemaClass);
    }

    public static void build(File jamFile, File spFile, File generatedDir, String schemaClass) throws Exception {
        int logicalSize = Jam.read(jamFile).requireInt("SPsize");
        byte[] logical = normalize(FileIO.read(spFile), logicalSize);
        Scratchpad scratchpad = new Scratchpad(logical, generatedDir);

        if (schemaClass != null) {
            Scratchpad.Schema schema = loadSchema(schemaClass);
            schema.apply(scratchpad);
            System.out.println("ScratchpadBuild: applied " + schemaClass);
        }

        scratchpad.writeLogical(new File(generatedDir.getParentFile(), "scratchpad.bin"));
        scratchpad.build();
    }

    private static Scratchpad.Schema loadSchema(String className) throws Exception {
        Class<?> type = Class.forName(className);
        Object value = type.getDeclaredConstructor().newInstance();
        if (!(value instanceof Scratchpad.Schema)) throw new IOException(className + " must implement Scratchpad.Schema");
        return (Scratchpad.Schema)value;
    }

    static byte[] normalize(byte[] raw, int logicalSize) throws IOException {
        if (raw.length < logicalSize) throw new IOException("SP is smaller than JAM SPsize: " + raw.length + " < " + logicalSize);
        int prefix = raw.length - logicalSize;
        if (prefix != 0 && prefix != 64) {
            throw new IOException("Unsupported SP container prefix: " + prefix + " bytes (expected 0 or 64)");
        }
        byte[] logical = new byte[logicalSize];
        System.arraycopy(raw, prefix, logical, 0, logicalSize);
        System.out.println("ScratchpadBuild: logical=" + logicalSize + ", container-prefix=" + prefix);
        return logical;
    }
}
