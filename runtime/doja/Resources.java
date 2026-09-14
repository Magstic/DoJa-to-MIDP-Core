package doja;

import java.io.InputStream;

/**
 * 從 MIDlet 所在的 JAR 裡讀取打包進去的資產。
 * 使用一個實例當 anchor，而非直接使用 class literal，
 * 因為真機的 CLDC/J2ME 環境不接受 class-literal 這種 bytecode。
 */
public final class Resources {
    private static final Object ANCHOR = new Resources();

    private Resources() {}

    public static InputStream open(String path) {
        return ANCHOR.getClass().getResourceAsStream(path);
    }
}
