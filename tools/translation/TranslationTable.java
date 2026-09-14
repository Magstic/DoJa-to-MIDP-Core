package doja.tools.translation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 翻譯表。 */
public final class TranslationTable {
    public static final String HEADER = "Original\tTranslation";

    private final LinkedHashMap<String,String> values;

    private TranslationTable(LinkedHashMap<String,String> values) {
        this.values = values;
    }

    public static TranslationTable empty() {
        return new TranslationTable(new LinkedHashMap<String,String>());
    }

    public static TranslationTable read(File file) throws IOException {
        if (!file.isFile()) return empty();
        LinkedHashMap<String,String> values = new LinkedHashMap<String,String>();
        BufferedReader reader = TsvCodec.reader(file);
        try {
            String line = TsvCodec.stripBom(reader.readLine());
            if (line == null) throw new IOException(file + ": empty TSV");
            if (!HEADER.equals(line)) throw new IOException(file + ": expected header Original<TAB>Translation");
            int number = 1;
            while ((line = reader.readLine()) != null) {
                number++;
                if (line.length() == 0) continue;
                String[] columns = TsvCodec.splitExact(line, 2, file, number);
                String original = TsvCodec.unescape(columns[0], file, number);
                String translation = TsvCodec.unescape(columns[1], file, number);
                if (original.length() == 0) throw new IOException(file + ":" + number + ": empty original text");
                if (values.put(original, translation) != null) {
                    throw new IOException(file + ":" + number + ": duplicate original text");
                }
            }
        } finally {
            reader.close();
        }
        return new TranslationTable(values);
    }

    public String get(String original) {
        return values.get(original);
    }

    /** 優先回傳非空譯文，其他情況沿用原文。 */
    public String resolve(String original) {
        String translated = values.get(original);
        return translated == null || translated.length() == 0 ? original : translated;
    }

    public int translatedCount() {
        int count = 0;
        for (Map.Entry<String,String> entry : values.entrySet()) {
            String translated = entry.getValue();
            if (translated != null && translated.length() != 0 && !translated.equals(entry.getKey())) count++;
        }
        return count;
    }

    public Map<String,String> asMap() {
        return java.util.Collections.unmodifiableMap(values);
    }

    public void write(File file, List<String> sources) throws IOException {
        Set<String> current = new LinkedHashSet<String>(sources);
        for (String old : values.keySet()) {
            if (!current.contains(old)) {
                throw new IOException(file + ": original column contains text not present in the base package: " + old);
            }
        }
        BufferedWriter writer = TsvCodec.writer(file);
        try {
            writer.write(HEADER);
            writer.write('\n');
            for (int i = 0; i < sources.size(); i++) {
                String source = sources.get(i);
                writer.write(TsvCodec.escape(source));
                writer.write('\t');
                String translated = values.get(source);
                if (translated != null) writer.write(TsvCodec.escape(translated));
                writer.write('\n');
            }
        } finally {
            writer.close();
        }
    }
}
