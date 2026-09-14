package doja.tools.translation;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.List;

/** 查詢翻譯文本來源位置的索引。 */
public final class TextIndex {
    public static final class Entry {
        public final String id;
        public final String source;
        public final String location;
        public final String original;

        public Entry(String id, String source, String location, String original) {
            this.id = id;
            this.source = source;
            this.location = location;
            this.original = original;
        }
    }

    private TextIndex() {}

    public static void write(File file, List<Entry> entries) throws IOException {
        BufferedWriter writer = TsvCodec.writer(file);
        try {
            writer.write("ID\tSource\tLocation\tOriginal\n");
            for (int i = 0; i < entries.size(); i++) {
                Entry entry = entries.get(i);
                writer.write(entry.id);
                writer.write('\t');
                writer.write(entry.source);
                writer.write('\t');
                writer.write(entry.location);
                writer.write('\t');
                writer.write(TsvCodec.escape(entry.original));
                writer.write('\n');
            }
        } finally {
            writer.close();
        }
    }
}
