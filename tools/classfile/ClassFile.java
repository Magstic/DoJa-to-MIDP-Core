package doja.tools.classfile;

import doja.tools.io.FileIO;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 建置工具共用的可修改 class-file 模型。原 constant pool 逐 byte 保留，
 * 只有新增或改過的 UTF8 項目才用 modified UTF-8 重寫；class 主體也先當成完整 byte[]。
 * 做小 patch 時因此不用重建整份 class，constant pool 長大也不會動到主體裡的相對位置。
 */
public final class ClassFile {
    public static final int ACC_PUBLIC = 0x0001;
    public static final int ACC_PRIVATE = 0x0002;
    public static final int ACC_PROTECTED = 0x0004;
    public static final int ACC_STATIC = 0x0008;

    private final byte[] header; // class magic 加 minor/major，共 8 bytes，寫回時原樣保留。
    private final ArrayList<Entry> pool; // JVM 從 index 1 起算；long/double 的第二格用 null 佔位。
    private byte[] body;

    private ClassFile(byte[] header, ArrayList<Entry> pool, byte[] body) {
        this.header = header;
        this.pool = pool;
        this.body = body;
    }

    public static ClassFile read(File file) throws IOException {
        return read(FileIO.read(file));
    }

    public static ClassFile read(byte[] data) throws IOException {
        if (data == null || data.length < 10 || readInt(data, 0) != 0xCAFEBABE) {
            throw new IOException("invalid class file");
        }
        byte[] header = new byte[8];
        System.arraycopy(data, 0, header, 0, 8);
        int count = u2(data, 8);
        if (count < 1) throw new IOException("invalid constant-pool count " + count);

        ArrayList<Entry> pool = new ArrayList<Entry>(count);
        pool.add(null);
        int p = 10;
        for (int i = 1; i < count; i++) {
            if (p >= data.length) throw new IOException("truncated constant pool");
            int tag = data[p++] & 255;
            int payloadLength;
            switch (tag) {
                case 1: {
                    require(data, p, 2);
                    int length = u2(data, p);
                    payloadLength = 2 + length;
                    break;
                }
                case 3: case 4: payloadLength = 4; break;
                case 5: case 6: payloadLength = 8; break;
                case 7: case 8: case 16: case 19: case 20: payloadLength = 2; break;
                case 9: case 10: case 11: case 12: case 17: case 18: payloadLength = 4; break;
                case 15: payloadLength = 3; break;
                default: throw new IOException("unsupported constant-pool tag " + tag);
            }
            require(data, p, payloadLength);
            byte[] payload = new byte[payloadLength];
            System.arraycopy(data, p, payload, 0, payloadLength);
            p += payloadLength;
            pool.add(new Entry(tag, payload));
            if (tag == 5 || tag == 6) {
                i++;
                if (i >= count) throw new IOException("invalid long/double constant at end of pool");
                pool.add(null);
            }
        }
        byte[] body = new byte[data.length - p];
        System.arraycopy(data, p, body, 0, body.length);
        ClassFile result = new ClassFile(header, pool, body);
        result.validateBodyHeader();
        return result;
    }

    public void write(File file) throws IOException {
        FileIO.write(file, toByteArray());
    }

    public byte[] toByteArray() throws IOException {
        if (pool.size() > 65535) throw new IOException("constant pool overflow");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(header.length + body.length + pool.size() * 8);
        bytes.write(header);
        writeU2(bytes, pool.size());
        for (int i = 1; i < pool.size(); i++) {
            Entry entry = pool.get(i);
            if (entry == null) continue;
            bytes.write(entry.tag);
            bytes.write(entry.payload);
        }
        bytes.write(body);
        return bytes.toByteArray();
    }

    public int constantPoolCount() {
        return pool.size();
    }

    public int constantTag(int index) {
        if (index <= 0 || index >= pool.size()) throw new IllegalArgumentException("invalid constant index " + index);
        Entry entry = pool.get(index);
        return entry == null ? 0 : entry.tag;
    }

    public String utf8(int index) throws IOException {
        Entry entry = entry(index);
        if (entry.tag != 1) throw new IOException("constant #" + index + " is not UTF8");
        return decodeModifiedUtf8(entry.payload);
    }

    public List<String> utf8Values() throws IOException {
        ArrayList<String> values = new ArrayList<String>();
        for (int i = 1; i < pool.size(); i++) {
            Entry entry = pool.get(i);
            if (entry != null && entry.tag == 1) values.add(decodeModifiedUtf8(entry.payload));
        }
        return Collections.unmodifiableList(values);
    }

    public int replaceUtf8Literal(String from, String to) throws IOException {
        if (from == null || from.length() == 0) throw new IllegalArgumentException("empty source text");
        if (to == null) throw new NullPointerException("to");
        int changed = 0;
        for (int i = 1; i < pool.size(); i++) {
            Entry entry = pool.get(i);
            if (entry == null || entry.tag != 1) continue;
            String value = decodeModifiedUtf8(entry.payload);
            String patched = replaceLiteral(value, from, to);
            if (!value.equals(patched)) {
                entry.payload = encodeModifiedUtf8(patched);
                changed++;
            }
        }
        return changed;
    }

    public int findClass(String internalName) throws IOException {
        for (int i = 1; i < pool.size(); i++) {
            Entry entry = pool.get(i);
            if (entry != null && entry.tag == 7 && internalName.equals(utf8(firstU2(entry)))) return i;
        }
        return 0;
    }

    public int findString(String value) throws IOException {
        for (int i = 1; i < pool.size(); i++) {
            Entry entry = pool.get(i);
            if (entry != null && entry.tag == 8 && value.equals(utf8(firstU2(entry)))) return i;
        }
        return 0;
    }

    /** 列出所有 CONSTANT_String index，翻譯工具可直接按位置建立穩定索引。 */
    public List<Integer> stringConstants() {
        ArrayList<Integer> indices = new ArrayList<Integer>();
        for (int i = 1; i < pool.size(); i++) {
            Entry entry = pool.get(i);
            if (entry != null && entry.tag == 8) indices.add(Integer.valueOf(i));
        }
        return Collections.unmodifiableList(indices);
    }

    /** 讀出 CONSTANT_String 的文字，呼叫端不用跟著處理間接的 UTF8 entry。 */
    public String string(int index) throws IOException {
        Entry entry = entry(index);
        if (entry.tag != 8) throw new IOException("constant #" + index + " is not a String");
        return utf8(firstU2(entry));
    }

    /** 讓 CONSTANT_String 指向指定文字；既有 UTF8 constant 能重用就不另增一筆。 */
    public void setString(int index, String value) throws IOException {
        if (value == null) throw new NullPointerException("value");
        Entry entry = entry(index);
        if (entry.tag != 8) throw new IOException("constant #" + index + " is not a String");
        putU2(entry.payload, 0, addUtf8(value));
    }

    public int findFieldRef(String owner, String name, String descriptor) throws IOException {
        return findRef(9, owner, name, descriptor);
    }

    public int findMethodRef(String owner, String name, String descriptor) throws IOException {
        return findRef(10, owner, name, descriptor);
    }

    public int findInterfaceMethodRef(String owner, String name, String descriptor) throws IOException {
        return findRef(11, owner, name, descriptor);
    }

    public int findRef(int tag, String owner, String name, String descriptor) throws IOException {
        for (int i = 1; i < pool.size(); i++) {
            Entry ref = pool.get(i);
            if (ref == null || ref.tag != tag) continue;
            int classIndex = firstU2(ref);
            int natIndex = secondU2(ref);
            Entry cls = entry(classIndex);
            Entry nat = entry(natIndex);
            if (cls.tag != 7 || nat.tag != 12) continue;
            String actualOwner = utf8(firstU2(cls));
            String actualName = utf8(firstU2(nat));
            String actualDesc = utf8(secondU2(nat));
            if (owner.equals(actualOwner) && name.equals(actualName) && descriptor.equals(actualDesc)) return i;
        }
        return 0;
    }

    public int addUtf8(String value) throws IOException {
        for (int i = 1; i < pool.size(); i++) {
            Entry entry = pool.get(i);
            if (entry != null && entry.tag == 1 && value.equals(decodeModifiedUtf8(entry.payload))) return i;
        }
        return addEntry(new Entry(1, encodeModifiedUtf8(value)), false);
    }

    public int addClass(String internalName) throws IOException {
        int existing = findClass(internalName);
        if (existing != 0) return existing;
        return addSingleU2Entry(7, addUtf8(internalName));
    }

    public int addString(String value) throws IOException {
        int existing = findString(value);
        if (existing != 0) return existing;
        return addSingleU2Entry(8, addUtf8(value));
    }

    public int addNameAndType(String name, String descriptor) throws IOException {
        int nameIndex = addUtf8(name);
        int descIndex = addUtf8(descriptor);
        for (int i = 1; i < pool.size(); i++) {
            Entry entry = pool.get(i);
            if (entry != null && entry.tag == 12 && firstU2(entry) == nameIndex && secondU2(entry) == descIndex) return i;
        }
        return addPairU2Entry(12, nameIndex, descIndex);
    }

    public int addFieldRef(String owner, String name, String descriptor) throws IOException {
        return addRef(9, owner, name, descriptor);
    }

    public int addMethodRef(String owner, String name, String descriptor) throws IOException {
        return addRef(10, owner, name, descriptor);
    }

    public int addInterfaceMethodRef(String owner, String name, String descriptor) throws IOException {
        return addRef(11, owner, name, descriptor);
    }

    public void retargetRef(int refIndex, String owner, String name, String descriptor) throws IOException {
        Entry ref = entry(refIndex);
        if (ref.tag != 9 && ref.tag != 10 && ref.tag != 11) {
            throw new IOException("constant #" + refIndex + " is not a member reference");
        }
        setPair(ref, addClass(owner), addNameAndType(name, descriptor));
    }

    public String className() throws IOException {
        if (body.length < 6) throw new IOException("truncated class body");
        int thisClass = u2(body, 2);
        Entry cls = entry(thisClass);
        if (cls.tag != 7) throw new IOException("invalid this_class");
        return utf8(firstU2(cls));
    }

    public List<Member> fields() throws IOException {
        ParsedMembers members = parseMembers();
        return buildMembers(members.fieldOffsets, false);
    }

    public List<Member> methods() throws IOException {
        ParsedMembers members = parseMembers();
        return buildMembers(members.methodOffsets, true);
    }

    public Member findField(String name, String descriptor) throws IOException {
        return findMember(fields(), name, descriptor);
    }

    public Member findMethod(String name, String descriptor) throws IOException {
        return findMember(methods(), name, descriptor);
    }

    public int exposeField(String name, String descriptor) throws IOException {
        return clearPrivate(fields(), name, descriptor);
    }

    public int exposeMethod(String name, String descriptor) throws IOException {
        return clearPrivate(methods(), name, descriptor);
    }

    private int clearPrivate(List<Member> members, String name, String descriptor) throws IOException {
        int changed = 0;
        for (int i = 0; i < members.size(); i++) {
            Member member = members.get(i);
            if (name.equals(member.name()) && descriptor.equals(member.descriptor())) {
                int access = member.accessFlags();
                if ((access & ACC_PRIVATE) != 0) member.setAccessFlags(access & ~ACC_PRIVATE);
                changed++;
            }
        }
        return changed;
    }

    private Member findMember(List<Member> members, String name, String descriptor) throws IOException {
        for (int i = 0; i < members.size(); i++) {
            Member member = members.get(i);
            if (name.equals(member.name()) && descriptor.equals(member.descriptor())) return member;
        }
        return null;
    }

    private List<Member> buildMembers(int[] offsets, boolean method) {
        ArrayList<Member> members = new ArrayList<Member>(offsets.length);
        for (int i = 0; i < offsets.length; i++) members.add(new Member(offsets[i], method));
        return Collections.unmodifiableList(members);
    }

    private ParsedMembers parseMembers() throws IOException {
        int p = 6;
        require(body, p, 2);
        int interfaceCount = u2(body, p);
        p += 2;
        require(body, p, interfaceCount * 2);
        p += interfaceCount * 2;

        require(body, p, 2);
        int fieldCount = u2(body, p);
        p += 2;
        int[] fields = new int[fieldCount];
        for (int i = 0; i < fieldCount; i++) {
            fields[i] = p;
            p = skipMember(body, p);
        }

        require(body, p, 2);
        int methodCount = u2(body, p);
        p += 2;
        int[] methods = new int[methodCount];
        for (int i = 0; i < methodCount; i++) {
            methods[i] = p;
            p = skipMember(body, p);
        }
        return new ParsedMembers(fields, methods);
    }

    private void validateBodyHeader() throws IOException {
        if (body.length < 8) throw new IOException("truncated class body");
        int thisClass = u2(body, 2);
        if (thisClass <= 0 || thisClass >= pool.size()) throw new IOException("invalid this_class");
    }

    private int addRef(int tag, String owner, String name, String descriptor) throws IOException {
        int existing = findRef(tag, owner, name, descriptor);
        if (existing != 0) return existing;
        return addPairU2Entry(tag, addClass(owner), addNameAndType(name, descriptor));
    }

    private int addSingleU2Entry(int tag, int value) throws IOException {
        byte[] payload = new byte[2];
        putU2(payload, 0, value);
        return addEntry(new Entry(tag, payload), false);
    }

    private int addPairU2Entry(int tag, int first, int second) throws IOException {
        byte[] payload = new byte[4];
        putU2(payload, 0, first);
        putU2(payload, 2, second);
        return addEntry(new Entry(tag, payload), false);
    }

    private int addEntry(Entry entry, boolean wide) throws IOException {
        int slots = wide ? 2 : 1;
        if (pool.size() + slots > 65535) throw new IOException("constant pool overflow");
        int index = pool.size();
        pool.add(entry);
        if (wide) pool.add(null);
        return index;
    }

    private Entry entry(int index) {
        if (index <= 0 || index >= pool.size()) throw new IllegalArgumentException("invalid constant index " + index);
        Entry entry = pool.get(index);
        if (entry == null) throw new IllegalArgumentException("invalid wide-constant continuation index " + index);
        return entry;
    }

    private static int firstU2(Entry entry) {
        return u2(entry.payload, 0);
    }

    private static int secondU2(Entry entry) {
        return u2(entry.payload, 2);
    }

    private static void setPair(Entry entry, int first, int second) {
        putU2(entry.payload, 0, first);
        putU2(entry.payload, 2, second);
    }

    private static String replaceLiteral(String value, String from, String to) {
        int at = value.indexOf(from);
        if (at < 0) return value;
        StringBuilder out = new StringBuilder(value.length() + Math.max(0, to.length() - from.length()));
        int start = 0;
        while (at >= 0) {
            out.append(value, start, at).append(to);
            start = at + from.length();
            at = value.indexOf(from, start);
        }
        out.append(value.substring(start));
        return out.toString();
    }

    private static String decodeModifiedUtf8(byte[] payload) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        return in.readUTF();
    }

    private static byte[] encodeModifiedUtf8(String value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(value.length() + 2);
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeUTF(value);
        out.flush();
        return bytes.toByteArray();
    }

    private static int skipMember(byte[] data, int offset) throws IOException {
        require(data, offset, 8);
        int attributes = u2(data, offset + 6);
        int p = offset + 8;
        for (int i = 0; i < attributes; i++) {
            require(data, p, 6);
            long length = u4(data, p + 2);
            if (length > Integer.MAX_VALUE) throw new IOException("attribute too large");
            p += 6;
            require(data, p, (int)length);
            p += (int)length;
        }
        return p;
    }

    private static void require(byte[] data, int offset, int length) throws IOException {
        if (offset < 0 || length < 0 || offset > data.length || length > data.length - offset) {
            throw new IOException("truncated class file");
        }
    }

    static int u2(byte[] data, int p) {
        return ((data[p] & 255) << 8) | (data[p + 1] & 255);
    }

    static long u4(byte[] data, int p) {
        return ((long)(data[p] & 255) << 24) | ((long)(data[p + 1] & 255) << 16)
                | ((long)(data[p + 2] & 255) << 8) | (long)(data[p + 3] & 255);
    }

    static int readInt(byte[] data, int p) {
        return ((data[p] & 255) << 24) | ((data[p + 1] & 255) << 16)
                | ((data[p + 2] & 255) << 8) | (data[p + 3] & 255);
    }

    static void putU2(byte[] data, int p, int value) {
        data[p] = (byte)(value >>> 8);
        data[p + 1] = (byte)value;
    }

    private static void writeU2(ByteArrayOutputStream out, int value) {
        out.write((value >>> 8) & 255);
        out.write(value & 255);
    }

    private static final class Entry {
        final int tag;
        byte[] payload;
        Entry(int tag, byte[] payload) {
            this.tag = tag;
            this.payload = payload;
        }
    }

    private static final class ParsedMembers {
        final int[] fieldOffsets;
        final int[] methodOffsets;
        ParsedMembers(int[] fieldOffsets, int[] methodOffsets) {
            this.fieldOffsets = fieldOffsets;
            this.methodOffsets = methodOffsets;
        }
    }

    public final class Member {
        private final int offset;
        private final boolean method;

        private Member(int offset, boolean method) {
            this.offset = offset;
            this.method = method;
        }

        public boolean isMethod() { return method; }
        public int accessFlags() { return u2(body, offset); }
        public void setAccessFlags(int flags) { putU2(body, offset, flags); }
        public String name() throws IOException { return utf8(u2(body, offset + 2)); }
        public String descriptor() throws IOException { return utf8(u2(body, offset + 4)); }

        public Code code() throws IOException {
            if (!method) return null;
            int attributes = u2(body, offset + 6);
            int p = offset + 8;
            for (int i = 0; i < attributes; i++) {
                require(body, p, 6);
                String attrName = utf8(u2(body, p));
                long attrLength = u4(body, p + 2);
                if (attrLength > Integer.MAX_VALUE) throw new IOException("attribute too large");
                int info = p + 6;
                require(body, info, (int)attrLength);
                if ("Code".equals(attrName)) {
                    if (attrLength < 8) throw new IOException("truncated Code attribute");
                    int codeLength = (int)u4(body, info + 4);
                    int codeStart = info + 8;
                    require(body, codeStart, codeLength);
                    return new Code(codeStart, codeLength);
                }
                p = info + (int)attrLength;
            }
            return null;
        }
    }

    public final class Code {
        private final int start;
        private final int length;

        private Code(int start, int length) {
            this.start = start;
            this.length = length;
        }

        public int length() { return length; }
        public int opcode(int offset) { checkOffset(offset, 1); return body[start + offset] & 255; }
        public int u1(int offset) { checkOffset(offset, 1); return body[start + offset] & 255; }
        public int u2(int offset) { checkOffset(offset, 2); return ClassFile.u2(body, start + offset); }
        public int i4(int offset) { checkOffset(offset, 4); return ClassFile.readInt(body, start + offset); }
        public void putByte(int offset, int value) { checkOffset(offset, 1); body[start + offset] = (byte)value; }
        public void putU2(int offset, int value) { checkOffset(offset, 2); ClassFile.putU2(body, start + offset, value); }
        public void fill(int from, int to, int value) {
            if (from < 0 || to < from || to > length) throw new IndexOutOfBoundsException();
            for (int i = from; i < to; i++) body[start + i] = (byte)value;
        }
        public int next(int offset) throws IOException { return Bytecode.next(this, offset); }

        private void checkOffset(int offset, int size) {
            if (offset < 0 || size < 0 || offset > length || size > length - offset) {
                throw new IndexOutOfBoundsException("code offset " + offset + "+" + size + " of " + length);
            }
        }
    }
}
