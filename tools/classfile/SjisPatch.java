package doja.tools.classfile;

import doja.tools.io.FileTree;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * 把兩種 String(..., "SJIS") 建構式換成 doja.Sjis.decode(...)。
 * patch 前後的 Code 長度相同，因此 branch offset 和 exception table 都能原封不動留下。
 */
public final class SjisPatch {
    private static final String STRING = "java/lang/String";
    private static final String SJIS_BRIDGE = "doja/Sjis";

    private SjisPatch() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: SjisPatch <class-dir>");
        int total = patch(new File(args[0]));
        System.out.println("SjisPatch: rewrote " + total + " constructor call(s)");
    }

    public static int patch(File root) throws IOException {
        List<File> files = FileTree.filesWithSuffix(root, ".class");
        int total = 0;
        for (int i = 0; i < files.size(); i++) total += patchFile(files.get(i));
        return total;
    }

    private static int patchFile(File file) throws IOException {
        ClassFile cls = ClassFile.read(file);
        int stringClass = cls.findClass(STRING);
        int sjisString = cls.findString("SJIS");
        int ctorBytes = cls.findMethodRef(STRING, "<init>", "([BLjava/lang/String;)V");
        int ctorSlice = cls.findMethodRef(STRING, "<init>", "([BIILjava/lang/String;)V");
        if (stringClass == 0 || sjisString == 0 || (ctorBytes == 0 && ctorSlice == 0)) return 0;

        int decodeBytes = cls.addMethodRef(SJIS_BRIDGE, "decode", "([B)Ljava/lang/String;");
        int decodeSlice = cls.addMethodRef(SJIS_BRIDGE, "decode", "([BII)Ljava/lang/String;");
        int changed = 0;
        List<ClassFile.Member> methods = cls.methods();
        for (int i = 0; i < methods.size(); i++) {
            ClassFile.Code code = methods.get(i).code();
            if (code != null) changed += patchCode(code, stringClass, sjisString, ctorBytes, ctorSlice, decodeBytes, decodeSlice);
        }
        if (changed != 0) cls.write(file);
        return changed;
    }

    private static int patchCode(ClassFile.Code code, int stringClass, int sjisString,
            int ctorBytes, int ctorSlice, int decodeBytes, int decodeSlice) throws IOException {
        int changed = 0;
        for (int p = 0; p < code.length();) {
            int next = code.next(p);
            if (code.opcode(p) != 0xbb || code.u2(p + 1) != stringClass || next >= code.length()
                    || code.opcode(next) != 0x59) {
                p = next;
                continue;
            }

            int limit = Math.min(code.length(), p + 48);
            int q = code.next(next);
            boolean patched = false;
            while (q < limit) {
                int op = code.opcode(q);
                int ldcSize;
                int constantIndex;
                if (op == 0x12) {
                    ldcSize = 2;
                    constantIndex = code.u1(q + 1);
                } else if (op == 0x13) {
                    ldcSize = 3;
                    constantIndex = code.u2(q + 1);
                } else {
                    q = code.next(q);
                    continue;
                }
                if (constantIndex != sjisString) {
                    q = code.next(q);
                    continue;
                }
                int invoke = q + ldcSize;
                if (invoke + 3 > code.length() || code.opcode(invoke) != 0xb7) break;
                int ctor = code.u2(invoke + 1);
                int target = ctor == ctorBytes ? decodeBytes : (ctor == ctorSlice ? decodeSlice : 0);
                if (target == 0) break;

                // NEW + DUP 清成 NOP，載入 "SJIS" 的 LDC 改放 INVOKESTATIC，
                // 舊 invokespecial 也補成 NOP；總長度不變，後面的位址都不用搬。
                code.fill(p, next + 1, 0);
                code.putByte(q, 0xb8);
                code.putU2(q + 1, target);
                int end = invoke + 3;
                code.fill(q + 3, end, 0);
                changed++;
                p = end;
                patched = true;
                break;
            }
            if (!patched) p = next;
        }
        return changed;
    }
}
