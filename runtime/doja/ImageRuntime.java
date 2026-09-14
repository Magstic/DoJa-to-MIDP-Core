package doja;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** 從建置產物載入選定的 image provider，讓遊戲專屬圖片格式保持可插拔。 */
public final class ImageRuntime {
    private static boolean initialized;
    private static ImageProvider provider;

    private ImageRuntime() {}

    public static ImageResource openEncoded(byte[] data) {
        ImageProvider value = provider();
        return value == null ? null : value.openEncoded(data);
    }

    public static ImageResource openScratchpad(String uri) {
        ImageProvider value = provider();
        return value == null ? null : value.openScratchpad(uri);
    }

    private static synchronized ImageProvider provider() {
        if (initialized) return provider;
        initialized = true;

        InputStream in = Resources.open("/assets/image-provider");
        if (in == null) return null;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            int value;
            while ((value = in.read()) >= 0) bytes.write(value);
            String className;
            try { className = new String(bytes.toByteArray(), "UTF-8").trim(); }
            catch (Exception ignored) { className = new String(bytes.toByteArray()).trim(); }
            if (className.length() == 0) return null;
            Object instance = Class.forName(className).newInstance();
            if (!(instance instanceof ImageProvider)) {
                throw new IllegalArgumentException(className + " is not an ImageProvider");
            }
            provider = (ImageProvider)instance;
            return provider;
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException(failure.toString());
        } finally {
            try { in.close(); } catch (Exception ignored) {}
        }
    }
}
