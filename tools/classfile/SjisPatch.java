package doja.tools.classfile;

import doja.tools.io.FileTree;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Rewrites DoJa String byte constructors to doja.Sjis.decode(...).
 *
 * DoJa 運作時預設使用 Shift-JIS 編碼。 MIDP 實作並不會強制要求使用相同的預設編碼
 * 因此，對於基於位元組資料的遊戲文本，不能直接使用 java.lang.String 依賴平台預設編碼的建構子。
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
        if (stringClass == 0) return 0;

        int sjisString = cls.findString("SJIS");
        int ctorDefaultBytes = cls.findMethodRef(STRING, "<init>", "([B)V");
        int ctorDefaultSlice = cls.findMethodRef(STRING, "<init>", "([BII)V");
        int ctorExplicitBytes = cls.findMethodRef(STRING, "<init>", "([BLjava/lang/String;)V");
        int ctorExplicitSlice = cls.findMethodRef(STRING, "<init>", "([BIILjava/lang/String;)V");
        if (ctorDefaultBytes == 0 && ctorDefaultSlice == 0
                && (sjisString == 0 || (ctorExplicitBytes == 0 && ctorExplicitSlice == 0))) {
            return 0;
        }

        int decodeBytes = cls.addMethodRef(SJIS_BRIDGE, "decode", "([B)Ljava/lang/String;");
        int decodeSlice = cls.addMethodRef(SJIS_BRIDGE, "decode", "([BII)Ljava/lang/String;");
        int changed = 0;
        List<ClassFile.Member> methods = cls.methods();
        for (int i = 0; i < methods.size(); i++) {
            ClassFile.Code code = methods.get(i).code();
            if (code != null) {
                changed += patchCode(code, stringClass, sjisString,
                        ctorDefaultBytes, ctorDefaultSlice,
                        ctorExplicitBytes, ctorExplicitSlice,
                        decodeBytes, decodeSlice);
            }
        }
        if (changed != 0) cls.write(file);
        return changed;
    }

    private static int patchCode(ClassFile.Code code, int stringClass, int sjisString,
            int ctorDefaultBytes, int ctorDefaultSlice,
            int ctorExplicitBytes, int ctorExplicitSlice,
            int decodeBytes, int decodeSlice) throws IOException {
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

                // new String(byte[]) / new String(byte[], off, len): 
                // 在 DoJa 中，這些方法使用執行時期預設的 Shift-JIS 編碼。
                if (op == 0xb7) {
                    int ctor = code.u2(q + 1);
                    int target = ctor == ctorDefaultBytes ? decodeBytes
                            : (ctor == ctorDefaultSlice ? decodeSlice : 0);
                    if (target != 0) {
                        code.fill(p, next + 1, 0);
                        code.putByte(q, 0xb8);
                        code.putU2(q + 1, target);
                        changed++;
                        p = q + 3;
                        patched = true;
                        break;
                    }
                }

                // 針對明確使用 new String(..., "SJIS") 的字串。
                if (sjisString != 0 && (op == 0x12 || op == 0x13)) {
                    int ldcSize = op == 0x12 ? 2 : 3;
                    int constantIndex = op == 0x12 ? code.u1(q + 1) : code.u2(q + 1);
                    if (constantIndex == sjisString) {
                        int invoke = q + ldcSize;
                        if (invoke + 3 <= code.length() && code.opcode(invoke) == 0xb7) {
                            int ctor = code.u2(invoke + 1);
                            int target = ctor == ctorExplicitBytes ? decodeBytes
                                    : (ctor == ctorExplicitSlice ? decodeSlice : 0);
                            if (target != 0) {
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
                        }
                    }
                }

                q = code.next(q);
            }
            if (!patched) p = next;
        }
        return changed;
    }
}
