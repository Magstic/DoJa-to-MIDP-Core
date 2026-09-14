package doja.tools.translation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** 定位到已輸出檔案的翻譯文字。 */
public final class ResolvedTextTable {
    private static final String HEADER = "kind\tfile\tlocation\ttext";

    private ResolvedTextTable() {}

    public static void write(File file, List<ResolvedText> entries) throws IOException {
        BufferedWriter writer = TsvCodec.writer(file);
        try {
            writer.write(HEADER);
            writer.write('\n');
            for (int i = 0; i < entries.size(); i++) {
                ResolvedText entry = entries.get(i);
                writer.write(entry.kind);
                writer.write('\t');
                writer.write(entry.file);
                writer.write('\t');
                writer.write(String.valueOf(entry.location));
                writer.write('\t');
                writer.write(TsvCodec.escape(entry.text));
                writer.write('\n');
            }
        } finally {
            writer.close();
        }
    }

    public static List<ResolvedText> read(File file) throws IOException {
        ArrayList<ResolvedText> result = new ArrayList<ResolvedText>();
        BufferedReader reader = TsvCodec.reader(file);
        try {
            String line = TsvCodec.stripBom(reader.readLine());
            if (!HEADER.equals(line)) throw new IOException(file + ": bad resolved header");
            int number = 1;
            while ((line = reader.readLine()) != null) {
                number++;
                if (line.length() == 0) continue;
                String[] columns = TsvCodec.splitExact(line, 4, file, number);
                int location;
                try {
                    location = Integer.parseInt(columns[2]);
                } catch (NumberFormatException ex) {
                    throw new IOException(file + ":" + number + ": invalid location");
                }
                result.add(new ResolvedText(columns[0], columns[1], location,
                        TsvCodec.unescape(columns[3], file, number)));
            }
        } finally {
            reader.close();
        }
        return result;
    }
}
