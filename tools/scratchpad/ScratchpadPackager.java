package doja.tools.scratchpad;

import doja.tools.io.FileIO;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

/** 將 logical scratchpad 拆成 baseline block、archive 與索引，Runtime 可按需載入。 */
final class ScratchpadPackager {
    private static final int BLOCK_SIZE = 4096;

    private final Scratchpad scratchpad;
    private final byte[] data;
    private final File generatedDir;
    private final File assetsDir;
    private final List<Scratchpad.Resource> resources;
    private final List<Scratchpad.Archive> archives;

    ScratchpadPackager(Scratchpad scratchpad) {
        this.scratchpad = scratchpad;
        this.data = scratchpad.logicalBytes();
        this.generatedDir = scratchpad.generatedDir();
        this.assetsDir = scratchpad.assetsDir();
        this.resources = scratchpad.discoveredResources();
        this.archives = scratchpad.discoveredArchives();
    }

    void build() throws Exception {
        FileIO.ensureDirectory(assetsDir);
        validateBaselineOmissions();
        writeBlocks(new File(assetsDir, "sp"));
        extractArchives();
        writeArchiveIndex(new File(assetsDir, "index.bin"));
        SoundIndex.write(new File(assetsDir, "sound-index.bin"), scratchpad.soundEntries());
        writeReport(new File(generatedDir.getParentFile(), "scratchpad-report.txt"));
        System.out.println("ScratchpadBuild: logical=" + data.length + ", blocks="
                + ((data.length + BLOCK_SIZE - 1) / BLOCK_SIZE) + ", archives=" + archives.size()
                + ", standard-resources=" + resources.size());
    }

    private void extractArchives() throws Exception {
        for (int i = 0; i < archives.size(); i++) {
            Scratchpad.Archive archive = archives.get(i);
            File outputDir = new File(assetsDir, three(archive.id()));
            List<Scratchpad.Entry> entries = archive.entries();
            for (int e = 0; e < entries.size(); e++) {
                Scratchpad.Entry entry = entries.get(e);
                String name = entry.name();
                byte[] bytes = entry.data().bytes();
                if (endsWithIgnoreCase(name, ".mld")) {
                    String stem = name.substring(0, name.length() - 4);
                    SoundConverter.Result converted = SoundConverter.convert(bytes, new File(outputDir, stem), false);
                    scratchpad.soundEntries().add(new SoundIndex.Entry(
                            "/assets/" + three(archive.id()) + "/" + stem + converted.extension,
                            converted.durationMillis));
                    System.out.println("ScratchpadBuild: " + name + " -> " + stem + converted.extension
                            + " (" + converted.durationMillis + " ms)");
                } else {
                    FileIO.write(new File(outputDir, name), bytes);
                }
            }
        }
    }

    private void writeBlocks(File directory) throws IOException {
        FileIO.ensureDirectory(directory);
        int count = (data.length + BLOCK_SIZE - 1) / BLOCK_SIZE;
        byte[] present = new byte[count];
        int retained = 0;
        int omitted = 0;
        for (int i = 0; i < count; i++) {
            int start = i * BLOCK_SIZE;
            int length = Math.min(BLOCK_SIZE, data.length - start);
            if (isFullyOmitted(start, length)) {
                omitted++;
                continue;
            }
            present[i] = 1;
            retained++;
            byte[] block = new byte[length];
            System.arraycopy(data, start, block, 0, length);
            FileIO.write(new File(directory, blockName(i)), block);
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream(16 + present.length);
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeBytes("SPBM");
        out.writeInt(data.length);
        out.writeInt(BLOCK_SIZE);
        out.writeInt(count);
        out.write(present);
        out.close();
        FileIO.write(new File(directory, "meta.bin"), bytes.toByteArray());
        System.out.println("ScratchpadBuild: baseline-blocks=" + retained + " retained, "
                + omitted + " omitted");
    }

    private boolean isFullyOmitted(int offset, int length) {
        int end = offset + length;
        int cursor = offset;
        for (int i = 0; i < archives.size() && cursor < end; i++) {
            Scratchpad.Archive archive = archives.get(i);
            if (archive.baselineRetained()) continue;
            int archiveStart = archive.offset();
            int archiveEnd = archive.offset() + archive.length();
            if (archiveEnd <= cursor) continue;
            if (archiveStart > cursor) return false;
            if (archiveEnd > cursor) cursor = archiveEnd;
        }
        return cursor >= end;
    }

    private void validateBaselineOmissions() throws IOException {
        List<Scratchpad.MutableState> states = scratchpad.mutableStates();
        for (int i = 0; i < states.size(); i++) {
            Scratchpad.MutableState state = states.get(i);
            int stateEnd = state.offset + state.length;
            for (int a = 0; a < archives.size(); a++) {
                Scratchpad.Archive archive = archives.get(a);
                if (archive.baselineRetained()) continue;
                int archiveEnd = archive.offset() + archive.length();
                if (state.offset < archiveEnd && archive.offset() < stateEnd) {
                    throw new IOException("mutable state overlaps omitted archive baseline: "
                            + state.name + " @ " + state.offset + " +" + state.length
                            + " vs ZIP @ " + archive.offset() + " +" + archive.length());
                }
            }
        }
    }

    private void writeArchiveIndex(File output) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(6 + archives.size() * 10);
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeBytes("SPAR");
        out.writeShort(archives.size());
        for (int i = 0; i < archives.size(); i++) {
            Scratchpad.Archive archive = archives.get(i);
            out.writeInt(archive.offset());
            out.writeInt(archive.length());
            out.writeShort(archive.id());
        }
        out.close();
        FileIO.write(output, bytes.toByteArray());
    }

    private void writeReport(File output) throws IOException {
        StringBuilder text = new StringBuilder();
        text.append("Scratchpad size: ").append(data.length).append('\n');
        text.append("\nStandard resources:\n");
        for (int i = 0; i < resources.size(); i++) {
            Scratchpad.Resource resource = resources.get(i);
            text.append("  ").append(resource.kind()).append(" @ ").append(resource.offset())
                    .append(" + ").append(resource.length()).append('\n');
        }
        text.append("\nOmitted archive baselines:\n");
        boolean anyOmitted = false;
        for (int i = 0; i < archives.size(); i++) {
            Scratchpad.Archive archive = archives.get(i);
            if (!archive.baselineRetained()) {
                anyOmitted = true;
                text.append("  ZIP ").append(archive.id()).append(" @ ").append(archive.offset())
                        .append(" + ").append(archive.length()).append('\n');
            }
        }
        if (!anyOmitted) text.append("  (none)\n");

        text.append("\nDeclared mutable state:\n");
        List<Scratchpad.MutableState> states = scratchpad.mutableStates();
        if (states.isEmpty()) text.append("  (none)\n");
        for (int i = 0; i < states.size(); i++) {
            Scratchpad.MutableState state = states.get(i);
            text.append("  ").append(state.name).append(" @ ").append(state.offset)
                    .append(" + ").append(state.length).append('\n');
        }
        FileIO.write(output, text.toString().getBytes("UTF-8"));
    }

    private static String blockName(int value) {
        String text = String.valueOf(value);
        while (text.length() < 4) text = "0" + text;
        return "b" + text + ".bin";
    }

    private static String three(int value) {
        String text = String.valueOf(value);
        while (text.length() < 3) text = "0" + text;
        return text;
    }

    private static boolean endsWithIgnoreCase(String value, String suffix) {
        return value.regionMatches(true, value.length() - suffix.length(), suffix, 0, suffix.length());
    }
}
