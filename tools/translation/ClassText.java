package doja.tools.translation;

import doja.tools.classfile.ClassFile;
import doja.tools.io.FileIO;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 字串的抽取／替換／驗證工具。
 * 在 class 檔案的常量池裡提取 CONSTANT_String，交給譯者。
 * 翻譯完再回寫 constant index 進行驗證比對。
 */
public final class ClassText {
    private ClassText() {}

    public static List<TextOccurrence> extractJar(File jarFile, String[] classNames) throws IOException {
        ZipFile zip = new ZipFile(jarFile);
        try {
            ArrayList<TextOccurrence> result = new ArrayList<TextOccurrence>();
            for (int i = 0; i < classNames.length; i++) {
                String name = classNames[i];
                ZipEntry entry = zip.getEntry(name);
                if (entry == null) throw new IOException(jarFile + ": missing " + name);
                InputStream in = zip.getInputStream(entry);
                try {
                    appendHumanStrings(result, name, ClassFile.read(FileIO.read(in)));
                } finally {
                    in.close();
                }
            }
            return result;
        } finally {
            zip.close();
        }
    }

    public static List<TextOccurrence> extractDirectory(File classesDir, String[] classNames) throws IOException {
        ArrayList<TextOccurrence> result = new ArrayList<TextOccurrence>();
        for (int i = 0; i < classNames.length; i++) {
            String name = classNames[i];
            File file = new File(classesDir, name);
            if (!file.isFile()) throw new IOException("missing " + file);
            appendHumanStrings(result, name, ClassFile.read(file));
        }
        return result;
    }

    private static void appendHumanStrings(List<TextOccurrence> output, String className, ClassFile cls)
            throws IOException {
        List<Integer> indices = cls.stringConstants();
        for (int i = 0; i < indices.size(); i++) {
            int index = indices.get(i).intValue();
            String text = cls.string(index);
            if (looksHumanText(text)) output.add(new TextOccurrence("class", className, index, text));
        }
    }

    /**
     * 判斷一個字串是否是『遊戲前端真正呈現』的。
     */
    public static boolean looksHumanText(String text) {
        if (text == null || text.length() == 0) return false;
        if (text.indexOf("://") >= 0 || text.endsWith(".bin") || text.endsWith(".mld")
                || text.endsWith(".dat") || "/".equals(text) || "data".equals(text)) return false;
        if (text.matches("[a-z]{1,4}[0-9]+")) return false;
        boolean unicodeText = false;
        boolean asciiLetter = false;
        boolean upper = false;
        boolean whitespace = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= 0x3040 && c <= 0x30ff) || (c >= 0x3400 && c <= 0x9fff)
                    || (c >= 0xf900 && c <= 0xfaff) || (c >= 0xff00 && c <= 0xffef)) {
                unicodeText = true;
            }
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
                asciiLetter = true;
                if (c >= 'A' && c <= 'Z') upper = true;
            }
            if (Character.isWhitespace(c)) whitespace = true;
        }
        return unicodeText || (text.length() >= 4 && asciiLetter && (upper || whitespace));
    }

    public static void patchDirectory(File classesDir, Map<String,Map<Integer,String>> replacements)
            throws IOException {
        for (Map.Entry<String,Map<Integer,String>> classPatch : replacements.entrySet()) {
            File file = new File(classesDir, classPatch.getKey());
            ClassFile cls = ClassFile.read(file);
            for (Map.Entry<Integer,String> patch : classPatch.getValue().entrySet()) {
                cls.setString(patch.getKey().intValue(), patch.getValue());
            }
            cls.write(file);
            ClassFile verify = ClassFile.read(file);
            for (Map.Entry<Integer,String> expected : classPatch.getValue().entrySet()) {
                String actual = verify.string(expected.getKey().intValue());
                if (!expected.getValue().equals(actual)) {
                    throw new IOException(file + ": class translation verification failed at constant #"
                            + expected.getKey());
                }
            }
        }
    }

    public static void verify(byte[] bytes, int constantIndex, String expected, String label) throws IOException {
        String actual = ClassFile.read(bytes).string(constantIndex);
        if (!expected.equals(actual)) {
            throw new IOException(label + ": expected " + expected + ", got " + actual);
        }
    }

    public static Map<String,Map<Integer,String>> newPatchMap() {
        return new LinkedHashMap<String,Map<Integer,String>>();
    }

    public static void addPatch(Map<String,Map<Integer,String>> patches, String file, int constantIndex,
            String value) {
        Map<Integer,String> classPatches = patches.get(file);
        if (classPatches == null) {
            classPatches = new LinkedHashMap<Integer,String>();
            patches.put(file, classPatches);
        }
        classPatches.put(Integer.valueOf(constantIndex), value);
    }
}
