package doja.tools.jam;

import doja.tools.io.FileIO;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;

/** 將 JAM 轉為 Runtime 直接讀取的二進位檔。 */
public final class DescriptorBuild {
    private DescriptorBuild() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: DescriptorBuild <app.jam> <output.bin>");
        build(new File(args[0]), new File(args[1]));
    }

    public static void build(File jamFile, File output) throws Exception {
        Jam jam = Jam.read(jamFile);
        FileIO.ensureParent(output);
        DataOutputStream out = new DataOutputStream(new FileOutputStream(output));
        try {
            out.writeBytes("DJAD");
            out.writeUTF(jam.require("AppClass"));
            out.writeUTF(jam.getOrEmpty("AppParam"));
            out.writeUTF(sourceUrl(jam));
        } finally {
            out.close();
        }
    }

    private static String sourceUrl(Jam jam) {
        String appParam = jam.getOrEmpty("AppParam");
        if (isAbsoluteUrl(appParam) && appParam.indexOf(' ') < 0) return appParam;

        String packageUrl = jam.getOrEmpty("PackageURL");
        int scheme = packageUrl.indexOf("://");
        int hostEnd = scheme < 0 ? -1 : packageUrl.indexOf('/', scheme + 3);
        String origin = hostEnd < 0 ? packageUrl : packageUrl.substring(0, hostEnd);
        int marker = packageUrl.indexOf("&f=");
        if (marker < 0) marker = packageUrl.indexOf("?f=");
        if (marker >= 0) {
            int start = marker + 3;
            int slash = packageUrl.indexOf('/', start);
            if (slash > start && origin.length() > 0) return origin + "/" + packageUrl.substring(start, slash) + "/";
        }
        int query = packageUrl.indexOf('?');
        String path = query < 0 ? packageUrl : packageUrl.substring(0, query);
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(0, slash + 1) : path;
    }

    private static boolean isAbsoluteUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }
}
