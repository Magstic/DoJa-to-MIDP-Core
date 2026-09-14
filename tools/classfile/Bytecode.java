package doja.tools.classfile;

import java.io.IOException;

/** 集中計算 JVM 指令長度，patcher 才能逐條走過 Code attribute 而不切進 operand 中間。 */
final class Bytecode {
    private static final int[] LENGTHS = makeLengths();

    private Bytecode() {}

    static int next(ClassFile.Code code, int offset) throws IOException {
        if (offset < 0 || offset >= code.length()) throw new IOException("instruction outside code: " + offset);
        int op = code.opcode(offset);
        if (op == 0xaa || op == 0xab) return nextSwitch(code, offset, op);
        if (op == 0xc4) {
            if (offset + 1 >= code.length()) throw new IOException("truncated wide");
            int next = code.opcode(offset + 1);
            int end = offset + (next == 0x84 ? 6 : 4);
            if (end > code.length()) throw new IOException("truncated wide instruction");
            return end;
        }
        int length = LENGTHS[op];
        if (length <= 0) throw new IOException("unsupported opcode 0x" + Integer.toHexString(op));
        int end = offset + length;
        if (end > code.length()) throw new IOException("truncated instruction");
        return end;
    }

    private static int nextSwitch(ClassFile.Code code, int offset, int op) throws IOException {
        int p = offset + 1;
        while ((p & 3) != 0) p++;
        if (op == 0xaa) {
            if (p + 12 > code.length()) throw new IOException("truncated tableswitch");
            int low = code.i4(p + 4);
            int high = code.i4(p + 8);
            long count = (long)high - low + 1L;
            if (count < 0 || count > Integer.MAX_VALUE / 4) throw new IOException("invalid tableswitch");
            p += 12 + (int)count * 4;
        } else {
            if (p + 8 > code.length()) throw new IOException("truncated lookupswitch");
            int count = code.i4(p + 4);
            if (count < 0 || count > Integer.MAX_VALUE / 8) throw new IOException("invalid lookupswitch");
            p += 8 + count * 8;
        }
        if (p > code.length()) throw new IOException("truncated switch");
        return p;
    }

    private static int[] makeLengths() {
        int[] lengths = new int[256];
        for (int i = 0; i < lengths.length; i++) lengths[i] = 1;
        int[] two = {0x10,0x12,0x15,0x16,0x17,0x18,0x19,0x36,0x37,0x38,0x39,0x3a,0xa9,0xbc};
        for (int i = 0; i < two.length; i++) lengths[two[i]] = 2;
        int[] three = {0x11,0x13,0x14,0x84,0x99,0x9a,0x9b,0x9c,0x9d,0x9e,0x9f,0xa0,0xa1,0xa2,0xa3,0xa4,0xa5,0xa6,0xa7,0xa8,0xb2,0xb3,0xb4,0xb5,0xb6,0xb7,0xb8,0xbb,0xbd,0xc0,0xc1,0xc6,0xc7};
        for (int i = 0; i < three.length; i++) lengths[three[i]] = 3;
        lengths[0xb9] = 5;
        lengths[0xba] = 5;
        lengths[0xc5] = 4;
        lengths[0xc8] = 5;
        lengths[0xc9] = 5;
        lengths[0xaa] = 0;
        lengths[0xab] = 0;
        lengths[0xc4] = 0;
        return lengths;
    }
}
