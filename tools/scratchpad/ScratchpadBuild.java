package doja.tools.scratchpad;

import doja.tools.io.FileIO;
import doja.tools.jam.Jam;

import java.io.File;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

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
        int[] partitionSizes = parsePartitionSizes(Jam.read(jamFile).require("SPsize"));
        int logicalSize = totalSize(partitionSizes);
        byte[] logical = normalize(FileIO.read(spFile), logicalSize);
        Scratchpad scratchpad = new Scratchpad(logical, generatedDir);

        if (schemaClass != null) {
            Scratchpad.Schema schema = loadSchema(schemaClass);
            schema.apply(scratchpad);
            System.out.println("ScratchpadBuild: applied " + schemaClass);
        }

        scratchpad.writeLogical(new File(generatedDir.getParentFile(), "scratchpad.bin"));
        scratchpad.build();
        writePartitions(generatedDir, partitionSizes);
    }

    private static int[] parsePartitionSizes(String value) throws IOException {
        String[] parts = value.split(",");
        if (parts.length == 0 || parts.length > 16) {
            throw new IOException("JAM has invalid SPsize partition count: " + value);
        }
        int[] sizes = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                sizes[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                throw new IOException("JAM has invalid SPsize: " + value);
            }
            if (sizes[i] < 0) throw new IOException("JAM has invalid SPsize: " + value);
        }
        return sizes;
    }

    private static int totalSize(int[] sizes) throws IOException {
        long total = 0;
        for (int i = 0; i < sizes.length; i++) total += sizes[i];
        if (total > Integer.MAX_VALUE) throw new IOException("JAM SPsize is too large");
        return (int)total;
    }

    private static void writePartitions(File generatedDir, int[] sizes) throws IOException {
        File directory = new File(new File(generatedDir, "assets"), "sp");
        FileIO.ensureDirectory(directory);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(6 + sizes.length * 4);
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeBytes("SPPT");
        out.writeShort(sizes.length);
        for (int i = 0; i < sizes.length; i++) out.writeInt(sizes[i]);
        out.close();
        FileIO.write(new File(directory, "partitions.bin"), bytes.toByteArray());
        System.out.println("ScratchpadBuild: partitions=" + sizes.length);
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
