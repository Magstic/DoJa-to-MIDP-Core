package com.nttdocomo.ui;

import doja.Resources;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import doja.ImageResource;
import doja.ImageRuntime;

public class MediaManager {
    public static MediaSound getSound(String uri) {
        if (uri == null) throw new NullPointerException("uri");
        String path = normalizeResourcePath(uri);
        if (endsWithIgnoreCase(path, ".mid")) return new SoundRes("/" + path, "audio/midi");
        if (endsWithIgnoreCase(path, ".wav")) return new SoundRes("/" + path, "audio/x-wav");
        return new BasicMediaSound(null, path);
    }

    public static MediaSound getSound(InputStream input) {
        if (input == null) throw new NullPointerException("input");
        return getSound(readAll(input));
    }

    public static MediaSound getSound(byte[] data) {
        if (data == null) throw new NullPointerException("data");
        String resource = soundToken(data);
        if (resource != null) {
            String type = endsWithIgnoreCase(resource, ".wav") ? "audio/x-wav" : "audio/midi";
            return new SoundRes(resource, type);
        }
        return new BasicMediaSound(data, "<unsupported DoJa sound>");
    }

    public static MediaImage getImage(String uri) {
        if (uri == null) throw new NullPointerException("uri");
        return new BasicMediaImage(null, uri);
    }
    public static MediaImage getImage(InputStream input) {
        if (input == null) throw new NullPointerException("input");
        return getImage(readAll(input));
    }
    public static MediaImage getImage(byte[] data) {
        if (data == null) throw new NullPointerException("data");
        return new BasicMediaImage(data, null);
    }

    /** 讓 ImageStore 重新開啟原始編碼來源，避免在 RAM 中額外保留一份 IMG 副本。*/
    public static InputStream openImageData(MediaImage image) throws IOException {
        if (!(image instanceof BasicMediaImage)) throw new IOException("unsupported MediaImage implementation");
        return ((BasicMediaImage)image).openEncodedStream();
    }


    public static boolean isImageUsed(MediaImage image) {
        if (image == null) return false;
        if (image instanceof BasicMediaImage) return ((BasicMediaImage)image).used;
        return image.getImage() != null;
    }

    /** 提供 drawNthImage() 讀取 GIF 畫面幀的專用 API */
    public static Image getImageFrame(MediaImage image, int frame) {
        if (image == null) throw new NullPointerException("image");
        if (frame < 0) throw new IllegalArgumentException("negative GIF frame");
        if (image instanceof BasicMediaImage) return ((BasicMediaImage)image).getFrame(frame);
        if (frame == 0) return image.getImage();
        throw new IllegalArgumentException("MediaImage does not expose animated GIF frames");
    }

    private static final class BasicMediaSound implements MediaSound {
        private final byte[] data;
        private final String resourcePath;
        BasicMediaSound(byte[] bytes, String path) { data = bytes; resourcePath = path; }
        public void use() {}
    }

    private static final class BasicMediaImage implements MediaImage {
        private final byte[] data;
        private final String resourcePath;
        private Image dojaImage;
        private byte[] encodedCache;
        private GifAnimation animation;
        private int cachedFrame = -1;
        private Image cachedFrameImage;
        private boolean used;
        BasicMediaImage(byte[] bytes, String path) { data = bytes; resourcePath = path; encodedCache = bytes; }

        public void use() {
            used = true;
            if (dojaImage != null) return;
            try {
                if (data != null) {
                    dojaImage = isGif(data) ? PalettedImage.createPalettedImage(data) : Image.createImage(data);
                    return;
                }
                String uri = resourcePath == null ? "" : resourcePath;
                if (uri.startsWith("scratchpad:///")) {
                    ImageResource provided = ImageRuntime.openScratchpad(uri);
                    if (provided != null) { dojaImage = Image.fromResource(provided); return; }
                }
                loadUriImage();
            } catch (Exception ignored) {}
        }

        InputStream openEncodedStream() throws IOException {
            if (data != null) return new ByteArrayInputStream(data);
            String uri = resourcePath == null ? "" : resourcePath;
            if (uri.startsWith("scratchpad:///") || uri.startsWith("resource:///")) {
                return com.nttdocomo.io.ConnectorProxy.openInputStream(uri);
            }
            InputStream input = Resources.open("/" + normalizeResourcePath(uri));
            if (input == null) throw new IOException("image resource not found: " + uri);
            return input;
        }

        private void loadUriImage() throws IOException {
            String uri = resourcePath == null ? "" : resourcePath;
            if (uri.startsWith("scratchpad:///") || uri.startsWith("resource:///")) {
                InputStream input = com.nttdocomo.io.ConnectorProxy.openInputStream(uri);
                try {
                    if (endsWithIgnoreCase(uri, ".gif")) dojaImage = PalettedImage.createPalettedImage(input);
                    else dojaImage = new Image(javax.microedition.lcdui.Image.createImage(input));
                } finally { input.close(); }
                return;
            }
            String name = normalizeResourcePath(uri);
            if (endsWithIgnoreCase(name, ".gif")) {
                InputStream input = Resources.open("/" + name);
                if (input == null) throw new IOException("image resource not found: " + uri);
                try { dojaImage = PalettedImage.createPalettedImage(input); }
                finally { input.close(); }
            } else {
                dojaImage = new Image(javax.microedition.lcdui.Image.createImage("/" + name));
            }
        }

        public Image getImage() { use(); return dojaImage; }

        Image getFrame(int frame) {
            if (frame == cachedFrame && cachedFrameImage != null) return cachedFrameImage;
            try {
                byte[] encoded = encodedBytes();
                if (!isGif(encoded)) {
                    if (frame == 0) { use(); return dojaImage; }
                    throw new IllegalArgumentException("MediaImage is not an animated GIF");
                }
                if (animation == null) animation = new GifAnimation(encoded);
                cachedFrameImage = animation.getFrame(frame);
                cachedFrame = frame;
                return cachedFrameImage;
            } catch (IOException e) {
                throw new IllegalArgumentException("cannot read animated GIF");
            }
        }

        private byte[] encodedBytes() throws IOException {
            if (encodedCache != null) return encodedCache;
            InputStream input = openEncodedStream();
            try { encodedCache = readAllStrict(input); }
            finally { input.close(); }
            return encodedCache;
        }
    }

    private static boolean isGif(byte[] data) {
        return data != null && data.length >= 6
                && data[0] == 'G' && data[1] == 'I' && data[2] == 'F';
    }

    private static boolean endsWithIgnoreCase(String value, String suffix) { return value != null && value.toLowerCase().endsWith(suffix); }
    private static String normalizeResourcePath(String uri) {
        String path = uri == null ? "" : uri;
        if (path.startsWith("resource:///")) path = path.substring("resource:///".length());
        return path;
    }
    private static byte[] readAllStrict(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = input.read(buffer)) >= 0) if (read > 0) output.write(buffer, 0, read);
        return output.toByteArray();
    }

    private static byte[] readAll(InputStream input) {
        if (input == null) return new byte[0];
        ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[1024];
        try { int read; while ((read = input.read(buffer)) >= 0) if (read > 0) output.write(buffer, 0, read); }
        catch (IOException ignored) {}
        return output.toByteArray();
    }
    private static String soundToken(byte[] data) {
        if (data == null || data.length < 6 || data[0] != 'S' || data[1] != 'N' || data[2] != 'D' || data[3] != ':') return null;
        StringBuffer path = new StringBuffer();
        for (int i = 4; i < data.length; i++) { int value = data[i] & 255; if (value == '\n' || value == 0) break; path.append((char)value); }
        return path.length() == 0 ? null : path.toString();
    }
}
