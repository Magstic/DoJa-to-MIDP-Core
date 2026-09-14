package com.nttdocomo.io;

import doja.Resources;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.microedition.rms.RecordStore;

/**
 * 把 DoJa 的 URI 接到 MIDP。resource:/// 直接讀 JAR；
 * scratchpad:/// 則把 JAR 內的分塊 baseline 和目前 suite 的 RMS 修改疊在一起，遊戲看到的仍是一段連續空間。
 * 其餘 scheme 交給 MIDP Connector，這樣網路等標準連線照裝置原本的實作走。
 */
public final class ConnectorProxy {
    private static final String RESOURCE_PREFIX = "resource:///";
    private static final String SCRATCHPAD_PREFIX = "scratchpad:///";
    private static final String SCRATCHPAD_META_RESOURCE = "/assets/sp/meta.bin";
    private static final String ARCHIVE_INDEX_RESOURCE = "/assets/index.bin";

    private static final String RMS_NAME = "DoJaScratchpad";
    private static final int RMS_MAGIC = 0x444A5253; // ASCII「DJRS」，用來擋掉其他格式的 RecordStore。

    private static boolean archivesLoaded;
    private static int[] archivePos = new int[0];
    private static int[] archiveLength = new int[0];
    private static String[] archiveId = new String[0];

    private static boolean scratchpadLoaded;
    private static int scratchpadSize;
    private static int blockSize;
    private static int blockCount;
    private static byte[] blockPresent;
    private static int cachedBlock = -1;
    private static byte[] cachedBlockData;

    private static boolean savesLoaded;
    private static int[] savePos = new int[16];
    private static byte[][] saveData = new byte[16][];
    private static int saveCount;

    private ConnectorProxy() {}

    public static javax.microedition.io.Connection open(String uri) throws IOException {
        return open(uri, javax.microedition.io.Connector.READ_WRITE, false);
    }

    public static javax.microedition.io.Connection open(String uri, int mode) throws IOException {
        return open(uri, mode, false);
    }

    public static javax.microedition.io.Connection open(String uri, int mode, boolean timeouts) throws IOException {
        if (isScratchpad(uri)) {
            ScratchpadRange range = parseScratchpadRange(uri);
            return new ScratchpadConnection(range.pos, range.length);
        }
        if (isResource(uri)) return new ResourceConnection(normalizeResourcePath(uri));
        return new HttpConnectionAdapter(uri, mode, timeouts);
    }

    public static InputStream openInputStream(String uri) throws IOException {
        if (isScratchpad(uri)) {
            ScratchpadRange range = parseScratchpadRange(uri);
            return openScratchpadInputStream(range.pos, range.length);
        }
        if (isResource(uri)) return openResourceInputStream(normalizeResourcePath(uri));
        return javax.microedition.io.Connector.openInputStream(uri);
    }

    public static DataInputStream openDataInputStream(String uri) throws IOException {
        return new DataInputStream(openInputStream(uri));
    }

    public static OutputStream openOutputStream(String uri) throws IOException {
        if (isScratchpad(uri)) {
            ScratchpadRange range = parseScratchpadRange(uri);
            return new ScratchpadOutputStream(range.pos, range.length);
        }
        if (isResource(uri)) throw new IOException("resource URI is read-only");
        return javax.microedition.io.Connector.openOutputStream(uri);
    }

    public static DataOutputStream openDataOutputStream(String uri) throws IOException {
        return new DataOutputStream(openOutputStream(uri));
    }

    public static void preflightScratchpad() throws IOException {
        ensureScratchpadLoaded();
        ensureArchivesLoaded();
        ensureSavesLoaded();
    }

    private static InputStream openScratchpadInputStream(int pos, int length) throws IOException {
        ensureScratchpadLoaded();
        int actualLength = length < 0 ? scratchpadSize - pos : length;
        String id = archiveAt(pos, actualLength);
        if (id != null) return new ArchiveTokenInputStream(id, actualLength);
        return new ScratchpadInputStream(pos, actualLength);
    }

    private static synchronized void ensureScratchpadLoaded() throws IOException {
        if (scratchpadLoaded) return;
        byte[] data = readResourceFully(SCRATCHPAD_META_RESOURCE);
        if (data == null || data.length < 16 || data[0] != 'S' || data[1] != 'P'
                || data[2] != 'B' || data[3] != 'M') {
            throw new IOException("missing or invalid " + SCRATCHPAD_META_RESOURCE);
        }
        scratchpadSize = readInt(data, 4);
        blockSize = readInt(data, 8);
        blockCount = readInt(data, 12);
        if (scratchpadSize < 0 || blockSize <= 0 || blockCount < 0
                || blockCount != (scratchpadSize + blockSize - 1) / blockSize
                || data.length != 16 + blockCount) {
            throw new IOException("invalid scratchpad metadata");
        }
        blockPresent = new byte[blockCount];
        for (int i = 0; i < blockCount; i++) {
            int value = data[16 + i] & 255;
            if (value > 1) throw new IOException("invalid scratchpad block map");
            blockPresent[i] = (byte)value;
        }
        scratchpadLoaded = true;
    }

    private static String archiveAt(int pos, int length) throws IOException {
        ensureArchivesLoaded();
        for (int i = 0; i < archivePos.length; i++) {
            if (archivePos[i] == pos && archiveLength[i] == length) return archiveId[i];
        }
        return null;
    }

    private static synchronized void ensureArchivesLoaded() throws IOException {
        if (archivesLoaded) return;
        byte[] data = readResourceFully(ARCHIVE_INDEX_RESOURCE);
        if (data == null || data.length < 6 || data[0] != 'S' || data[1] != 'P'
                || data[2] != 'A' || data[3] != 'R') {
            throw new IOException("missing or invalid " + ARCHIVE_INDEX_RESOURCE);
        }
        int count = readU16(data, 4);
        if (data.length < 6 + count * 10) throw new IOException("truncated archive index");
        archivePos = new int[count]; archiveLength = new int[count]; archiveId = new String[count];
        int p = 6;
        for (int i = 0; i < count; i++) {
            archivePos[i] = readInt(data, p); p += 4;
            archiveLength[i] = readInt(data, p); p += 4;
            archiveId[i] = three(readU16(data, p)); p += 2;
        }
        archivesLoaded = true;
    }

    private static boolean isScratchpad(String uri) { return uri != null && uri.startsWith(SCRATCHPAD_PREFIX); }
    private static boolean isResource(String uri) { return uri != null && uri.startsWith(RESOURCE_PREFIX); }

    private static String normalizeResourcePath(String uri) {
        String name = uri == null ? "" : uri;
        if (name.startsWith(RESOURCE_PREFIX)) name = name.substring(RESOURCE_PREFIX.length());
        while (name.startsWith("/")) name = name.substring(1);
        return name;
    }

    private static InputStream openResourceInputStream(String name) throws IOException {
        String path = name.startsWith("/") ? name : "/" + name;
        InputStream input = Resources.open(path);
        if (input == null) throw new IOException("resource not found: " + name);
        return input;
    }

    private static ScratchpadRange parseScratchpadRange(String uri) throws IOException {
        if (!isScratchpad(uri)) throw new IOException("not a scratchpad URI: " + uri);
        ensureScratchpadLoaded();
        String work = uri.substring(SCRATCHPAD_PREFIX.length());
        int pos = 0, length = -1;
        int marker = work.indexOf(";pos=");
        if (marker >= 0) {
            int start = marker + 5;
            int comma = work.indexOf(',', start);
            if (comma < 0) comma = work.length();
            pos = parseInt(work.substring(start, comma), 0);
            int lengthMarker = work.indexOf("length=", comma);
            if (lengthMarker >= 0) length = parseInt(work.substring(lengthMarker + 7), -1);
        }
        if (pos < 0 || pos > scratchpadSize) throw new IOException("bad scratchpad position " + pos);
        int available = scratchpadSize - pos;
        if (length >= 0 && length > available) throw new IOException("bad scratchpad length " + length);
        return new ScratchpadRange(pos, length);
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); } catch (Exception ignored) { return fallback; }
    }

    private static byte[] readResourceFully(String path) throws IOException {
        InputStream input = Resources.open(path);
        if (input == null) return null;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[1024];
            int read; while ((read = input.read(buffer)) >= 0) if (read > 0) out.write(buffer, 0, read);
            return out.toByteArray();
        } finally { input.close(); }
    }

    private static int baseByteAt(int position) throws IOException {
        ensureScratchpadLoaded();
        if (position < 0 || position >= scratchpadSize) throw new IOException("bad scratchpad position");
        int block = position / blockSize;
        byte[] data = loadBlock(block);
        return data[position - block * blockSize] & 0xff;
    }

    private static byte[] loadBlock(int block) throws IOException {
        if (block == cachedBlock && cachedBlockData != null) return cachedBlockData;
        if (block < 0 || block >= blockCount) throw new IOException("bad scratchpad block " + block);
        if (blockPresent[block] == 0) throw new IOException("scratchpad baseline omitted at block " + block);
        byte[] data = readResourceFully("/assets/sp/" + blockName(block));
        int expected = Math.min(blockSize, scratchpadSize - block * blockSize);
        if (data == null || data.length != expected) throw new IOException("missing scratchpad block " + block);
        cachedBlock = block; cachedBlockData = data;
        return data;
    }

    private static synchronized void ensureSavesLoaded() {
        if (savesLoaded) return;
        savesLoaded = true;
        RecordStore store = null;
        try {
            store = RecordStore.openRecordStore(RMS_NAME, false);
            if (store.getNumRecords() == 0) return;
            parseSaveBlob(store.getRecord(1));
        } catch (Throwable ignored) {
        } finally { if (store != null) try { store.closeRecordStore(); } catch (Throwable ignored) {} }
    }

    private static void parseSaveBlob(byte[] data) {
        if (data == null || data.length < 8 || readInt(data, 0) != RMS_MAGIC) return;
        int count = readInt(data, 4), p = 8;
        if (count < 0 || count > 4096) return;
        for (int i = 0; i < count; i++) {
            if (p + 8 > data.length) return;
            int pos = readInt(data, p); p += 4;
            int length = readInt(data, p); p += 4;
            if (pos < 0 || length < 0 || pos > scratchpadSize || length > scratchpadSize - pos || p + length > data.length) return;
            byte[] value = new byte[length]; System.arraycopy(data, p, value, 0, length); p += length;
            putSaved(pos, value);
        }
    }

    private static synchronized void save(int pos, byte[] data) throws IOException {
        ensureScratchpadLoaded(); ensureSavesLoaded();
        if (pos < 0 || data == null || pos > scratchpadSize || data.length > scratchpadSize - pos) throw new IOException("scratchpad write outside range");
        putSaved(pos, data);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(RMS_MAGIC); output.writeInt(saveCount);
        for (int i = 0; i < saveCount; i++) { output.writeInt(savePos[i]); output.writeInt(saveData[i].length); output.write(saveData[i]); }
        output.close(); byte[] blob = bytes.toByteArray();
        RecordStore store = null;
        try {
            store = RecordStore.openRecordStore(RMS_NAME, true);
            if (store.getNumRecords() == 0) store.addRecord(blob, 0, blob.length);
            else store.setRecord(1, blob, 0, blob.length);
        } catch (Exception failure) { throw new IOException(failure.toString()); }
        finally { if (store != null) try { store.closeRecordStore(); } catch (Throwable ignored) {} }
    }

    private static void putSaved(int pos, byte[] data) {
        for (int i = 0; i < saveCount; i++) if (savePos[i] == pos) { saveData[i] = data; return; }
        if (saveCount == savePos.length) {
            int[] positions = new int[savePos.length * 2]; byte[][] values = new byte[saveData.length * 2][];
            System.arraycopy(savePos, 0, positions, 0, saveCount); System.arraycopy(saveData, 0, values, 0, saveCount);
            savePos = positions; saveData = values;
        }
        savePos[saveCount] = pos; saveData[saveCount] = data; saveCount++;
    }

    private static int savedByteAt(int absolutePos, int fallback) {
        for (int i = saveCount - 1; i >= 0; i--) {
            int offset = absolutePos - savePos[i];
            if (offset >= 0 && offset < saveData[i].length) return saveData[i][offset] & 0xff;
        }
        return fallback;
    }

    private static int readU16(byte[] data, int p) { return ((data[p] & 255) << 8) | (data[p + 1] & 255); }
    private static int readInt(byte[] data, int p) { return ((data[p]&255)<<24)|((data[p+1]&255)<<16)|((data[p+2]&255)<<8)|(data[p+3]&255); }
    private static String three(int value) { String text=String.valueOf(value); while(text.length()<3)text="0"+text; return text; }
    private static String blockName(int value) { String text=String.valueOf(value); while(text.length()<4)text="0"+text; return "b"+text+".bin"; }

    private static final class ScratchpadRange {
        final int pos; final int length;
        ScratchpadRange(int pos, int length) { this.pos = pos; this.length = length; }
    }

    private static final class ScratchpadInputStream extends InputStream {
        private final int start; private int remaining; private int cursor;
        ScratchpadInputStream(int pos, int length) throws IOException { ensureScratchpadLoaded(); start=pos; remaining=length; ensureSavesLoaded(); }
        public int read() throws IOException {
            if (remaining <= 0) return -1;
            int absolute = start + cursor;
            int value = savedByteAt(absolute, baseByteAt(absolute));
            cursor++; remaining--; return value;
        }
        public int read(byte[] data, int offset, int length) throws IOException {
            if (data == null) throw new NullPointerException();
            if (length == 0) return 0;
            if (remaining <= 0) return -1;
            if (length > remaining) length = remaining;
            int total = 0;
            while (total < length) {
                int absolute = start + cursor + total;
                int block = absolute / blockSize;
                byte[] source = loadBlock(block);
                int within = absolute - block * blockSize;
                int amount = Math.min(length - total, source.length - within);
                System.arraycopy(source, within, data, offset + total, amount);
                total += amount;
            }
            for (int i = 0; i < total; i++) data[offset + i] = (byte)savedByteAt(start + cursor + i, data[offset + i] & 255);
            cursor += total; remaining -= total; return total;
        }
    }

    private static final class ScratchpadOutputStream extends ByteArrayOutputStream {
        private final int pos; private final int maximum; private boolean closed;
        ScratchpadOutputStream(int pos, int length) { this.pos=pos; maximum=length; }
        public void write(int value) { if (closed) return; if (maximum >= 0 && count >= maximum) return; super.write(value); }
        public void write(byte[] data, int offset, int length) { if (closed) return; if (maximum >= 0 && length > maximum - count) length = maximum - count; if (length > 0) super.write(data, offset, length); }
        public void close() throws IOException { if (closed) return; closed=true; save(pos, toByteArray()); }
    }

    private static final class ArchiveTokenInputStream extends InputStream {
        private final byte[] token; private final int total; private int pos;
        ArchiveTokenInputStream(String id, int length) {
            byte[] value; try { value=("SPARC:"+id+"\n").getBytes("ISO-8859-1"); } catch(Exception ignored){ value=("SPARC:"+id+"\n").getBytes(); }
            token=value; total=length;
        }
        public int read() { if(pos>=total)return -1; int index=pos++; return index<token.length?token[index]&255:0; }
        public int read(byte[] data,int offset,int length){ if(data==null)throw new NullPointerException(); if(length==0)return 0; if(pos>=total)return -1; if(length>total-pos)length=total-pos; for(int i=0;i<length;i++,pos++)data[offset+i]=pos<token.length?token[pos]:0; return length; }
    }

    private static final class ScratchpadConnection implements javax.microedition.io.ContentConnection {
        private final int pos; private final int length;
        ScratchpadConnection(int pos,int length){this.pos=pos;this.length=length;}
        public InputStream openInputStream() throws IOException{return openScratchpadInputStream(pos,length);}
        public DataInputStream openDataInputStream() throws IOException{return new DataInputStream(openInputStream());}
        public OutputStream openOutputStream() throws IOException{return new ScratchpadOutputStream(pos,length);}
        public DataOutputStream openDataOutputStream() throws IOException{return new DataOutputStream(openOutputStream());}
        public String getType(){return null;} public String getEncoding(){return null;}
        public long getLength(){return length<0?scratchpadSize-pos:length;} public void close(){}
    }

    private static final class ResourceConnection implements javax.microedition.io.ContentConnection {
        private final String name; ResourceConnection(String name){this.name=name;}
        public InputStream openInputStream() throws IOException{return openResourceInputStream(name);}
        public DataInputStream openDataInputStream() throws IOException{return new DataInputStream(openInputStream());}
        public OutputStream openOutputStream() throws IOException{throw new IOException("resource URI is read-only");}
        public DataOutputStream openDataOutputStream() throws IOException{throw new IOException("resource URI is read-only");}
        public String getType(){return null;} public String getEncoding(){return null;} public long getLength(){return -1L;} public void close(){}
    }

    private static final class HttpConnectionAdapter implements HttpConnection {
        private final String uri; private final int mode; private final boolean timeouts; private javax.microedition.io.HttpConnection delegate;
        HttpConnectionAdapter(String uri,int mode,boolean timeouts){this.uri=uri;this.mode=mode;this.timeouts=timeouts;}
        public void setRequestMethod(String method)throws IOException{ensureOpen().setRequestMethod(method);} public void connect()throws IOException{ensureOpen();}
        public InputStream openInputStream()throws IOException{return ensureOpen().openInputStream();} public DataInputStream openDataInputStream()throws IOException{return ensureOpen().openDataInputStream();}
        public OutputStream openOutputStream()throws IOException{return ensureOpen().openOutputStream();} public DataOutputStream openDataOutputStream()throws IOException{return ensureOpen().openDataOutputStream();}
        public String getType(){try{return ensureOpen().getType();}catch(IOException ignored){return null;}} public String getEncoding(){try{return ensureOpen().getEncoding();}catch(IOException ignored){return null;}}
        public long getLength(){try{return ensureOpen().getLength();}catch(IOException ignored){return -1L;}} public void close()throws IOException{if(delegate!=null){delegate.close();delegate=null;}}
        private javax.microedition.io.HttpConnection ensureOpen()throws IOException{if(delegate==null)delegate=(javax.microedition.io.HttpConnection)javax.microedition.io.Connector.open(uri,mode,timeouts);return delegate;}
    }
}
