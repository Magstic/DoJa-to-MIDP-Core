package doja.tools.io;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** 建置流程共用的檔案操作。 */
public final class FileIO {
    private FileIO() {}

    public static byte[] read(File file) throws IOException {
        FileInputStream in = new FileInputStream(file);
        try {
            long size = file.length();
            if (size > Integer.MAX_VALUE) throw new IOException("file too large: " + file);
            byte[] data = new byte[(int)size];
            int offset = 0;
            while (offset < data.length) {
                int n = in.read(data, offset, data.length - offset);
                if (n < 0) throw new IOException("truncated read: " + file);
                offset += n;
            }
            return data;
        } finally {
            in.close();
        }
    }

    public static byte[] read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) >= 0) {
            if (n > 0) out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    public static void write(File file, byte[] data) throws IOException {
        ensureParent(file);
        FileOutputStream out = new FileOutputStream(file);
        try {
            out.write(data);
        } finally {
            out.close();
        }
    }

    public static void ensureDirectory(File dir) throws IOException {
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("cannot create directory: " + dir);
        }
    }

    public static void ensureParent(File file) throws IOException {
        ensureDirectory(file.getParentFile());
    }
}
