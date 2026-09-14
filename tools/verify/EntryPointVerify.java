package doja.tools.verify;

import doja.tools.classfile.ClassFile;
import doja.tools.io.FileIO;

import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** 在 shrink 後檢查 AppClass 與 public 無參建構子，維持真機相容性。 */
public final class EntryPointVerify {
    private EntryPointVerify() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: EntryPointVerify <jar> <app-class>");
        verify(args[0], args[1]);
        System.out.println("EntryPointVerify: " + args[1] + " OK");
    }

    public static void verify(String jarPath, String appClass) throws IOException {
        JarFile jar = new JarFile(jarPath);
        try {
            String path = appClass.replace('.', '/') + ".class";
            JarEntry entry = jar.getJarEntry(path);
            if (entry == null) throw new IOException("AppClass was renamed or removed by ProGuard: " + appClass);
            InputStream in = jar.getInputStream(entry);
            byte[] bytes;
            try { bytes = FileIO.read(in); } finally { in.close(); }
            ClassFile cls = ClassFile.read(bytes);
            ClassFile.Member ctor = cls.findMethod("<init>", "()V");
            if (ctor == null || (ctor.accessFlags() & ClassFile.ACC_PUBLIC) == 0) {
                throw new IOException("AppClass has no public no-arg constructor after ProGuard: " + appClass);
            }
        } finally {
            jar.close();
        }
    }
}
