package doja;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/** 讀取 Wrapper 從 JAM 整理出的 metadata。 */
public final class ApplicationDescriptor {
    private static ApplicationDescriptor instance;

    private final String appClass;
    private final String appParam;
    private final String sourceUrl;

    private ApplicationDescriptor(String appClass, String appParam, String sourceUrl) {
        this.appClass = appClass;
        this.appParam = appParam;
        this.sourceUrl = sourceUrl;
    }

    public static synchronized ApplicationDescriptor get() throws IOException {
        if (instance != null) return instance;
        InputStream raw = Resources.open("/assets/app.bin");
        if (raw == null) throw new IOException("missing /assets/app.bin");
        DataInputStream in = new DataInputStream(raw);
        try {
            if (in.readUnsignedByte() != 'D' || in.readUnsignedByte() != 'J'
                    || in.readUnsignedByte() != 'A' || in.readUnsignedByte() != 'D') {
                throw new IOException("invalid application descriptor");
            }
            instance = new ApplicationDescriptor(in.readUTF(), in.readUTF(), in.readUTF());
            return instance;
        } finally {
            in.close();
        }
    }

    public String getAppClass() { return appClass; }
    public String getAppParam() { return appParam; }
    public String getSourceUrl() { return sourceUrl; }

    public String[] getArgs() {
        if (appParam == null || appParam.length() == 0) return new String[0];
        java.util.Vector parts = new java.util.Vector();
        int start = 0;
        for (int i = 0; i <= appParam.length(); i++) {
            if (i == appParam.length() || appParam.charAt(i) == ' ') {
                if (i > start) parts.addElement(appParam.substring(start, i));
                start = i + 1;
            }
        }
        String[] result = new String[parts.size()];
        parts.copyInto(result);
        return result;
    }
}
