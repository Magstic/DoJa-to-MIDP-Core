package doja.tools.font;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** 拉取 Fusion Pixel 12px 字體。 */
public final class FontFetch {
    private static final String LATEST_URL =
            "https://github.com/TakWolf/fusion-pixel-font/releases/latest";
    private static final String DOWNLOAD_BASE =
            "https://github.com/TakWolf/fusion-pixel-font/releases/download/";
    private static final String ASSET_PREFIX =
            "fusion-pixel-font-12px-monospaced-ttf-v";
    private static final String FONT_NAME =
            "fusion-pixel-12px-monospaced-ja.ttf";
    private static final int MAX_REDIRECTS = 10;
    private static final int BUFFER_SIZE = 32768;

    private FontFetch() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: FontFetch <cache-dir>");
        }

        File cacheDir = new File(args[0]);
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            throw new IOException("Could not create " + cacheDir.getPath());
        }

        File fontFile = new File(cacheDir, FONT_NAME);
        if (fontFile.isFile() && fontFile.length() > 0) {
            System.out.println("FontFetch: cached " + fontFile.getPath());
            return;
        }

        String tag = latestStableTag();
        String version = tag.startsWith("v") ? tag.substring(1) : tag;
        String assetName = ASSET_PREFIX + version + ".zip";
        URL assetUrl = new URL(DOWNLOAD_BASE + tag + "/" + assetName);
        File zipFile = new File(cacheDir, assetName + ".part");
        File fontTemp = new File(cacheDir, FONT_NAME + ".part");

        delete(zipFile);
        delete(fontTemp);

        try {
            download(assetUrl, zipFile);
            extractFont(zipFile, fontTemp);
            if (!fontTemp.renameTo(fontFile)) {
                copy(fontTemp, fontFile);
                delete(fontTemp);
            }
        } finally {
            delete(zipFile);
            delete(fontTemp);
        }

        if (!fontFile.isFile() || fontFile.length() == 0) {
            throw new IOException("Font extraction did not produce " + fontFile.getPath());
        }
        System.out.println("FontFetch: " + tag + " -> " + fontFile.getPath());
    }

    private static String latestStableTag() throws IOException {
        URL current = new URL(LATEST_URL);
        for (int i = 0; i < MAX_REDIRECTS; i++) {
            HttpURLConnection connection = open(current);
            int status = connection.getResponseCode();
            if (isRedirect(status)) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || location.length() == 0) {
                    throw new IOException("GitHub latest release redirect has no Location header");
                }
                current = new URL(current, location);
                continue;
            }
            if (status < 200 || status >= 300) {
                connection.disconnect();
                throw new IOException("GitHub latest release returned HTTP " + status);
            }
            InputStream input = null;
            try {
                input = connection.getInputStream();
            } finally {
                if (input != null) try { input.close(); } catch (IOException ignored) {}
                connection.disconnect();
            }
            String path = current.getPath();
            int slash = path.lastIndexOf('/');
            String tag = slash >= 0 ? path.substring(slash + 1) : path;
            tag = URLDecoder.decode(tag, "UTF-8");
            if (tag.length() == 0 || "latest".equals(tag)) {
                throw new IOException("Could not determine Fusion Pixel Font release tag from " + current);
            }
            return tag;
        }
        throw new IOException("Too many redirects while resolving Fusion Pixel Font latest release");
    }

    private static void download(URL url, File destination) throws IOException {
        URL current = url;
        for (int i = 0; i < MAX_REDIRECTS; i++) {
            HttpURLConnection connection = open(current);
            int status = connection.getResponseCode();
            if (isRedirect(status)) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || location.length() == 0) {
                    throw new IOException("Download redirect has no Location header: " + current);
                }
                current = new URL(current, location);
                continue;
            }
            if (status < 200 || status >= 300) {
                connection.disconnect();
                throw new IOException("Font download returned HTTP " + status + ": " + current);
            }
            InputStream input = null;
            OutputStream output = null;
            try {
                input = new BufferedInputStream(connection.getInputStream());
                output = new BufferedOutputStream(new FileOutputStream(destination));
                copy(input, output);
            } finally {
                if (output != null) try { output.close(); } catch (IOException ignored) {}
                if (input != null) try { input.close(); } catch (IOException ignored) {}
                connection.disconnect();
            }
            return;
        }
        throw new IOException("Too many redirects while downloading " + url);
    }

    private static void extractFont(File zipFile, File destination) throws IOException {
        ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)));
        try {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory() && FONT_NAME.equals(baseName(entry.getName()))) {
                    OutputStream output = new BufferedOutputStream(new FileOutputStream(destination));
                    try {
                        copy(zip, output);
                    } finally {
                        output.close();
                    }
                    return;
                }
            }
        } finally {
            zip.close();
        }
        throw new IOException("Font archive does not contain " + FONT_NAME);
    }

    private static HttpURLConnection open(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(60000);
        connection.setRequestProperty("User-Agent", "DoJa-Wrapper-MIDP");
        connection.setRequestProperty("Accept", "*/*");
        return connection;
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static String baseName(String name) {
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    private static void copy(File source, File destination) throws IOException {
        InputStream input = new BufferedInputStream(new FileInputStream(source));
        OutputStream output = new BufferedOutputStream(new FileOutputStream(destination));
        try {
            copy(input, output);
        } finally {
            try { output.close(); } finally { input.close(); }
        }
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read > 0) output.write(buffer, 0, read);
        }
    }

    private static void delete(File file) {
        if (file.exists() && !file.delete()) file.deleteOnExit();
    }
}
