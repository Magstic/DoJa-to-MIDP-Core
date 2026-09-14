package doja.tools.scratchpad;

import doja.tools.io.FileIO;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 建置階段整理 Scratchpad 的 API。
 * 核心負責連續 byte address space 與常見內嵌格式。
 */
public final class Scratchpad {
    public enum Kind { ZIP, GIF, BMP, PNG, MLD }

    public interface Schema {
        void apply(Scratchpad sp) throws Exception;
    }

    public static class Blob {
        private final byte[] data;
        private final int base;
        private final int length;
        private final String label;

        Blob(byte[] data, int base, int length, String label) {
            this.data = data;
            this.base = base;
            this.length = length;
            this.label = label == null ? "blob" : label;
        }

        public int length() { return length; }
        public String label() { return label; }

        public byte[] bytes() {
            byte[] out = new byte[length];
            System.arraycopy(data, base, out, 0, length);
            return out;
        }

        public Blob slice(int offset, int len) throws IOException {
            check(offset, len);
            return new Blob(data, base + offset, len, label + "+" + offset);
        }

        public int u8(int offset) throws IOException {
            check(offset, 1);
            return data[base + offset] & 0xff;
        }

        public int u16be(int offset) throws IOException {
            check(offset, 2);
            int p = base + offset;
            return ((data[p] & 0xff) << 8) | (data[p + 1] & 0xff);
        }

        public int u16le(int offset) throws IOException {
            check(offset, 2);
            int p = base + offset;
            return (data[p] & 0xff) | ((data[p + 1] & 0xff) << 8);
        }

        public int i32be(int offset) throws IOException {
            check(offset, 4);
            int p = base + offset;
            return ((data[p] & 0xff) << 24) | ((data[p + 1] & 0xff) << 16)
                    | ((data[p + 2] & 0xff) << 8) | (data[p + 3] & 0xff);
        }

        public int i32le(int offset) throws IOException {
            check(offset, 4);
            int p = base + offset;
            return (data[p] & 0xff) | ((data[p + 1] & 0xff) << 8)
                    | ((data[p + 2] & 0xff) << 16) | ((data[p + 3] & 0xff) << 24);
        }

        /** 讀取常見的 DoJa table：BE u16 筆數後接 BE u32 offset，可直接拿來定位 payload。 */
        public OffsetTable offsetTable16_32BE() throws IOException {
            return offsetTable16_32BE(0);
        }

        public OffsetTable offsetTable16_32BE(int offset) throws IOException {
            int count = u16be(offset);
            if (count < 2 || count > 4096) {
                throw new IOException(label + ": invalid offset-table count " + count);
            }
            if (2L + count * 4L > length - offset) {
                throw new IOException(label + ": truncated offset table");
            }
            int[] offsets = new int[count];
            for (int i = 0; i < count; i++) {
                int value = i32be(offset + 2 + i * 4);
                if (value < 0 || value > length - offset) {
                    throw new IOException(label + ": bad table offset " + value + " at " + i);
                }
                if (i > 0 && value < offsets[i - 1]) {
                    throw new IOException(label + ": unsorted table offset at " + i);
                }
                offsets[i] = value;
            }
            return new OffsetTable(slice(offset, length - offset), offsets);
        }

        public Kind kind() {
            return ResourceFormats.exactKind(data, base, length);
        }

        public Archive zip() throws IOException {
            if (kind() != Kind.ZIP) throw new IOException(label + " is not a ZIP");
            return new Archive(this, -1, -1, -1);
        }

        private void check(int offset, int len) throws IOException {
            if (offset < 0 || len < 0 || offset > length || len > length - offset) {
                throw new IOException(label + ": range outside blob: " + offset + "+" + len
                        + " of " + length);
            }
        }
    }

    public static final class Region extends Blob {
        private final Scratchpad owner;
        private final int absoluteOffset;

        Region(Scratchpad owner, int offset, int length, String label) {
            super(owner.data, offset, length, label);
            this.owner = owner;
            this.absoluteOffset = offset;
        }

        public int offset() { return absoluteOffset; }

        public Region clear() {
            fill(0);
            return this;
        }

        public Region fill(int value) {
            byte b = (byte)value;
            for (int i = 0; i < length(); i++) owner.data[absoluteOffset + i] = b;
            return this;
        }

        public Region set(byte[] bytes) throws IOException {
            if (bytes == null) throw new NullPointerException("bytes");
            if (bytes.length != length()) {
                throw new IOException(label() + ": expected " + length() + " bytes, got " + bytes.length);
            }
            System.arraycopy(bytes, 0, owner.data, absoluteOffset, bytes.length);
            return this;
        }
    }

    public static final class OffsetTable {
        private final Blob source;
        private final int[] offsets;

        OffsetTable(Blob source, int[] offsets) {
            this.source = source;
            this.offsets = offsets;
        }

        public int size() { return offsets.length - 1; }
        public int boundaryCount() { return offsets.length; }
        public int offset(int index) { return offsets[index]; }

        public Blob entry(int index) throws IOException {
            if (index < 0 || index >= size()) throw new IOException("table entry outside range: " + index);
            return source.slice(offsets[index], offsets[index + 1] - offsets[index]);
        }
    }

    public static final class Resource {
        private final Kind kind;
        private final int offset;
        private final int length;

        Resource(Kind kind, int offset, int length) {
            this.kind = kind;
            this.offset = offset;
            this.length = length;
        }

        public Kind kind() { return kind; }
        public int offset() { return offset; }
        public int length() { return length; }
        public String toString() { return kind + " @" + offset + " +" + length; }
    }

    public static final class Archive {
        private final Blob blob;
        private final int offset;
        private final int length;
        private final int id;
        private List<Entry> entries;
        private boolean baselineRetained = true;

        Archive(Blob blob, int offset, int length, int id) {
            this.blob = blob;
            this.offset = offset;
            this.length = length;
            this.id = id;
        }

        public int offset() { return offset; }
        public int length() { return length; }
        public int id() { return id; }

        /** 從 baseline 省掉原始 ZIP block，archive 內容仍可經 SPARC 逐 byte 讀取，能縮小產物。 */
        public Archive omitBaseline() {
            baselineRetained = false;
            return this;
        }

        boolean baselineRetained() { return baselineRetained; }

        public List<Entry> entries() throws IOException {
            ensureEntries();
            return Collections.unmodifiableList(entries);
        }

        public Blob entry(String name) throws IOException {
            ensureEntries();
            for (int i = 0; i < entries.size(); i++) {
                Entry e = entries.get(i);
                if (e.name().equals(name)) return e.data();
            }
            throw new IOException("ZIP entry not found: " + name);
        }

        /** 替換已解出的 ZIP entry；原始 scratchpad 位址不動，其他引用仍保持有效。 */
        public void replace(String name, byte[] bytes) throws IOException {
            if (bytes == null) throw new NullPointerException("bytes");
            ensureEntries();
            for (int i = 0; i < entries.size(); i++) {
                Entry e = entries.get(i);
                if (e.name().equals(name)) {
                    e.data = new Blob(bytes, 0, bytes.length, "ZIP:" + name);
                    return;
                }
            }
            throw new IOException("ZIP entry not found: " + name);
        }

        private void ensureEntries() throws IOException {
            if (entries != null) return;
            entries = new ArrayList<Entry>();
            ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(blob.bytes()));
            try {
                ZipEntry ze;
                while ((ze = in.getNextEntry()) != null) {
                    if (ze.isDirectory()) continue;
                    String name = sanitize(ze.getName());
                    byte[] entryData = FileIO.read(in);
                    entries.add(new Entry(name, new Blob(entryData, 0, entryData.length,
                            "ZIP:" + name)));
                }
            } finally {
                in.close();
            }
        }
    }

    public static final class Entry {
        private final String name;
        private Blob data;
        Entry(String name, Blob data) { this.name = name; this.data = data; }
        public String name() { return name; }
        public Blob data() { return data; }
    }

    private final byte[] data;
    private final File generatedDir;
    private final File assetsDir;
    private final List<Resource> resources = new ArrayList<Resource>();
    private final List<Archive> archives = new ArrayList<Archive>();
    private final List<MutableState> states = new ArrayList<MutableState>();
    private final List<SoundIndex.Entry> soundEntries = new ArrayList<SoundIndex.Entry>();
    private boolean discovered;

    public Scratchpad(byte[] logical, File generatedDir) throws IOException {
        if (logical == null) throw new NullPointerException("logical");
        if (generatedDir == null) throw new NullPointerException("generatedDir");
        this.data = logical;
        this.generatedDir = generatedDir;
        this.assetsDir = new File(generatedDir, "assets");
    }

    public int size() { return data.length; }

    public Region region(int offset, int length) throws IOException {
        checkRange(offset, length);
        return new Region(this, offset, length, "SP[" + offset + "," + length + "]");
    }

    /** 標出一段可變狀態並回傳 Region，Schema 可接著清理初始存檔旗標等內容。 */
    public Region state(int offset, int length) throws IOException {
        return state("state", offset, length);
    }

    public Region state(String name, int offset, int length) throws IOException {
        checkRange(offset, length);
        states.add(new MutableState(name, offset, length));
        return new Region(this, offset, length, name);
    }

    public List<Resource> resources() {
        ensureDiscovered();
        return Collections.unmodifiableList(resources);
    }

    public List<Resource> resources(Kind kind) {
        ensureDiscovered();
        List<Resource> out = new ArrayList<Resource>();
        for (int i = 0; i < resources.size(); i++) {
            Resource resource = resources.get(i);
            if (resource.kind == kind) out.add(resource);
        }
        return Collections.unmodifiableList(out);
    }

    public Resource resourceAt(int offset) {
        ensureDiscovered();
        for (int i = 0; i < resources.size(); i++) {
            Resource resource = resources.get(i);
            if (resource.offset == offset) return resource;
        }
        return null;
    }

    public List<Archive> archives() {
        ensureDiscovered();
        return Collections.unmodifiableList(archives);
    }

    public Archive archiveAt(int offset) throws IOException {
        ensureDiscovered();
        for (int i = 0; i < archives.size(); i++) {
            Archive archive = archives.get(i);
            if (archive.offset == offset) return archive;
        }
        throw new IOException("ZIP not found at scratchpad offset " + offset);
    }

    public void write(int offset, byte[] bytes) throws IOException {
        if (bytes == null) throw new NullPointerException("bytes");
        checkRange(offset, bytes.length);
        System.arraycopy(bytes, 0, data, offset, bytes.length);
    }

    /** 將抽出的 blob 寫進 /assets，回傳值可直接存入 Runtime index。 */
    public String export(String path, Blob blob) throws IOException {
        String safe = sanitizeAssetPath(path);
        File out = new File(assetsDir, safe);
        FileIO.write(out, blob.bytes());
        return "/assets/" + safe;
    }

    /** 將 MLD 轉成適合 MIDP 的 MIDI 或 WAV，並回傳可放回 Scratchpad 的 SND token。 */
    public byte[] sound(String stem, Blob mld) throws Exception {
        return sound(stem, mld, false);
    }

    /** 強制把旋律型 MLD 離線合成 WAV，適合 MIDI backend 表現不穩的裝置。 */
    public byte[] soundWav(String stem, Blob mld) throws Exception {
        return sound(stem, mld, true);
    }

    private byte[] sound(String stem, Blob mld, boolean forceWav) throws Exception {
        String safeStem = sanitizeAssetPath(stem);
        if (safeStem.endsWith(".mid") || safeStem.endsWith(".wav") || safeStem.endsWith(".mld")) {
            throw new IOException("sound stem must not have an extension: " + stem);
        }
        SoundConverter.Result converted = SoundConverter.convert(mld.bytes(), new File(assetsDir, safeStem), forceWav);
        String resource = "/assets/" + safeStem + converted.extension;
        soundEntries.add(new SoundIndex.Entry(resource, converted.durationMillis));
        return ("SND:" + resource + "\n").getBytes("ISO-8859-1");
    }

    /** 依 payload 順序產生「BE u16 筆數 + BE u32 offset」表，offset 會緊接著累加。 */
    public static byte[] offsetTable16_32BE(byte[][] entries) throws IOException {
        if (entries == null) throw new NullPointerException("entries");
        int count = entries.length + 1;
        long header = 2L + count * 4L;
        long total = header;
        for (int i = 0; i < entries.length; i++) {
            if (entries[i] == null) throw new NullPointerException("entries[" + i + "]");
            total += entries[i].length;
        }
        if (count > 65535 || total > Integer.MAX_VALUE) throw new IOException("offset table is too large");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream((int)total);
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(count);
        int position = (int)header;
        for (int i = 0; i < entries.length; i++) {
            out.writeInt(position);
            position += entries[i].length;
        }
        out.writeInt(position);
        for (int i = 0; i < entries.length; i++) out.write(entries[i]);
        out.close();
        return bytes.toByteArray();
    }

    void writeLogical(File output) throws IOException {
        FileIO.write(output, data);
    }

    void build() throws Exception {
        new ScratchpadPackager(this).build();
    }

    byte[] logicalBytes() { return data; }
    File generatedDir() { return generatedDir; }
    File assetsDir() { return assetsDir; }
    List<MutableState> mutableStates() { return states; }
    List<SoundIndex.Entry> soundEntries() { return soundEntries; }

    List<Resource> discoveredResources() {
        ensureDiscovered();
        return resources;
    }

    List<Archive> discoveredArchives() {
        ensureDiscovered();
        return archives;
    }

    private void ensureDiscovered() {
        if (discovered) return;
        resources.clear();
        archives.clear();
        int position = 0;
        while (position < data.length) {
            ResourceFormats.Match match = ResourceFormats.detect(data, position, data.length - position);
            if (match == null) {
                position++;
                continue;
            }
            resources.add(new Resource(match.kind, position, match.length));
            if (match.kind == Kind.ZIP) {
                archives.add(new Archive(new Blob(data, position, match.length, "SP ZIP @" + position),
                        position, match.length, archives.size()));
            }
            position += match.length;
        }
        discovered = true;
    }

    private void checkRange(int offset, int length) throws IOException {
        if (offset < 0 || length < 0 || offset > data.length || length > data.length - offset) {
            throw new IOException("scratchpad range outside image: " + offset + "+" + length
                    + " of " + data.length);
        }
    }

    private static String sanitize(String name) throws IOException {
        String value = name.replace('\\', '/');
        if (value.length() == 0 || value.startsWith("/") || value.indexOf("../") >= 0) {
            throw new IOException("unsafe ZIP entry " + name);
        }
        return value;
    }

    private static String sanitizeAssetPath(String path) throws IOException {
        if (path == null) throw new NullPointerException("path");
        String value = path.replace('\\', '/');
        while (value.startsWith("/")) value = value.substring(1);
        if (value.length() == 0 || value.indexOf("../") >= 0) throw new IOException("unsafe asset path " + path);
        return value;
    }

    static final class MutableState {
        final String name;
        final int offset;
        final int length;
        MutableState(String name, int offset, int length) {
            this.name = name;
            this.offset = offset;
            this.length = length;
        }
    }
}
