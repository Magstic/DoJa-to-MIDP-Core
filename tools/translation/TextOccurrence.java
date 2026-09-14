package doja.tools.translation;

/** 字串在建置來源中的位置。 */
public final class TextOccurrence {
    public final String kind;
    public final String file;
    public final int location;
    public final String source;

    public TextOccurrence(String kind, String file, int location, String source) {
        if (kind == null || file == null || source == null) throw new NullPointerException();
        this.kind = kind;
        this.file = file;
        this.location = location;
        this.source = source;
    }
}
