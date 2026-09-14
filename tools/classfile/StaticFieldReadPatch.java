package doja.tools.classfile;

import java.io.File;
import java.io.IOException;

/** 將指定 GETSTATIC 換成相同 stack shape 的 INVOKESTATIC，可在讀值時插入相容邏輯。 */
public final class StaticFieldReadPatch {
    private StaticFieldReadPatch() {}

    public static int redirect(File file,
            String methodName, String methodDesc,
            String fieldOwner, String fieldName, String fieldDesc,
            String targetOwner, String targetName, String targetDesc,
            int expectedReads, int[] selectedReads) throws IOException {
        ClassFile cls = ClassFile.read(file);
        int fieldRef = cls.findFieldRef(fieldOwner, fieldName, fieldDesc);
        if (fieldRef == 0) {
            throw new IOException("field reference not found: " + fieldOwner + "." + fieldName + fieldDesc);
        }
        int targetRef = cls.addMethodRef(targetOwner, targetName, targetDesc);
        ClassFile.Member method = cls.findMethod(methodName, methodDesc);
        if (method == null) throw new IOException("method not found: " + methodName + methodDesc);
        ClassFile.Code code = method.code();
        if (code == null) throw new IOException("method has no Code attribute: " + methodName + methodDesc);

        int total = 0;
        int changed = 0;
        for (int p = 0; p < code.length(); p = code.next(p)) {
            if (code.opcode(p) == 0xb2 && code.u2(p + 1) == fieldRef) {
                if (contains(selectedReads, total)) {
                    code.putByte(p, 0xb8);
                    code.putU2(p + 1, targetRef);
                    changed++;
                }
                total++;
            }
        }
        if (total != expectedReads) {
            throw new IOException(methodName + methodDesc + " field reads: expected " + expectedReads + ", got " + total);
        }
        if (changed != selectedReads.length) {
            throw new IOException("selected field reads: expected " + selectedReads.length + ", patched " + changed);
        }
        cls.write(file);
        return changed;
    }

    private static boolean contains(int[] values, int value) {
        for (int i = 0; i < values.length; i++) if (values[i] == value) return true;
        return false;
    }
}
