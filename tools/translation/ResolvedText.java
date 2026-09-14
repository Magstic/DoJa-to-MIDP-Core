package doja.tools.translation;

public final class ResolvedText {
    public final String kind;
    public final String file;
    public final int location;
    public final String text;

    public ResolvedText(String kind, String file, int location, String text) {
        if (kind == null || file == null || text == null) throw new NullPointerException();
        this.kind = kind;
        this.file = file;
        this.location = location;
        this.text = text;
    }
}
