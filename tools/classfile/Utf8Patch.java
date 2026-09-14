package doja.tools.classfile;

import doja.tools.io.FileTree;

import java.io.File;
import java.io.IOException;
import java.util.List;

/** 直接換掉 CONSTANT_Utf8 文字，適合 owner、descriptor 等不需改指令長度的 patch。 */
public final class Utf8Patch {
    private Utf8Patch() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Usage: Utf8Patch <class-dir> <from> <to>");
        int count = replace(new File(args[0]), args[1], args[2]);
        System.out.println("Utf8Patch: " + args[1] + " -> " + args[2] + ": " + count + " replacement(s)");
    }

    public static int replace(File root, String from, String to) throws IOException {
        List<File> files = FileTree.filesWithSuffix(root, ".class");
        int total = 0;
        for (int i = 0; i < files.size(); i++) {
            File file = files.get(i);
            ClassFile cls = ClassFile.read(file);
            int changed = cls.replaceUtf8Literal(from, to);
            if (changed != 0) cls.write(file);
            total += changed;
        }
        return total;
    }
}
