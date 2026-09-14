package doja.tools.scratchpad;

final class ResourceFormats {
    static final class Match {
        final Scratchpad.Kind kind;
        final int length;
        Match(Scratchpad.Kind kind, int length) { this.kind = kind; this.length = length; }
    }

    private ResourceFormats() {}

    static Match detect(byte[] data, int offset, int available) {
        int length;
        if (available >= 4 && data[offset] == 'P' && data[offset + 1] == 'K'
                && data[offset + 2] == 3 && data[offset + 3] == 4) {
            length = zipLength(data, offset);
            if (length > 0) return new Match(Scratchpad.Kind.ZIP, length);
        }
        if (available >= 6 && data[offset] == 'G' && data[offset + 1] == 'I' && data[offset + 2] == 'F'
                && data[offset + 3] == '8' && (data[offset + 4] == '7' || data[offset + 4] == '9')
                && data[offset + 5] == 'a') {
            length = gifLength(data, offset);
            if (length > 0) return new Match(Scratchpad.Kind.GIF, length);
        }
        if (available >= 8 && (data[offset] & 0xff) == 0x89 && data[offset + 1] == 'P'
                && data[offset + 2] == 'N' && data[offset + 3] == 'G' && data[offset + 4] == 0x0d
                && data[offset + 5] == 0x0a && data[offset + 6] == 0x1a && data[offset + 7] == 0x0a) {
            length = pngLength(data, offset);
            if (length > 0) return new Match(Scratchpad.Kind.PNG, length);
        }
        if (available >= 14 && data[offset] == 'B' && data[offset + 1] == 'M') {
            length = bmpLength(data, offset);
            if (length > 0) return new Match(Scratchpad.Kind.BMP, length);
        }
        if (available >= 4 && data[offset] == 'm' && data[offset + 1] == 'e'
                && data[offset + 2] == 'l' && data[offset + 3] == 'o') {
            length = mldLength(data, offset);
            if (length > 0) return new Match(Scratchpad.Kind.MLD, length);
        }
        return null;
    }

    static Scratchpad.Kind exactKind(byte[] data, int offset, int length) {
        Match match = detect(data, offset, length);
        return match != null && match.length == length ? match.kind : null;
    }

    private static int gifLength(byte[] d, int off) {
        try {
            int end = d.length;
            if (off + 13 > end) return -1;
            int packed = d[off + 10] & 0xff;
            int p = off + 13;
            if ((packed & 0x80) != 0) p += 3 * (1 << ((packed & 7) + 1));
            while (p < end) {
                int marker = d[p++] & 0xff;
                if (marker == 0x3b) return p - off;
                if (marker == 0x2c) {
                    if (p + 9 > end) return -1;
                    int imagePacked = d[p + 8] & 0xff;
                    p += 9;
                    if ((imagePacked & 0x80) != 0) p += 3 * (1 << ((imagePacked & 7) + 1));
                    if (p >= end) return -1;
                    p++;
                    p = skipSubBlocks(d, p);
                } else if (marker == 0x21) {
                    if (p >= end) return -1;
                    p++;
                    p = skipSubBlocks(d, p);
                } else {
                    return -1;
                }
                if (p < 0 || p > end) return -1;
            }
        } catch (RuntimeException ignored) {}
        return -1;
    }

    private static int skipSubBlocks(byte[] d, int p) {
        while (p < d.length) {
            int n = d[p++] & 0xff;
            if (n == 0) return p;
            if (p + n > d.length) return -1;
            p += n;
        }
        return -1;
    }

    private static int bmpLength(byte[] d, int off) {
        if (off + 18 > d.length) return -1;
        long size = u32le(d, off + 2);
        long dib = u32le(d, off + 14);
        if (size < 26 || size > Integer.MAX_VALUE || off + size > d.length) return -1;
        if (dib != 12 && dib < 40) return -1;
        return (int)size;
    }

    private static int pngLength(byte[] d, int off) {
        long p = off + 8L;
        while (p + 12 <= d.length) {
            long n = u32be(d, (int)p);
            if (n > Integer.MAX_VALUE || p + 12L + n > d.length) return -1;
            int type = (int)p + 4;
            p += 12L + n;
            if (d[type] == 'I' && d[type + 1] == 'E' && d[type + 2] == 'N' && d[type + 3] == 'D') {
                long length = p - off;
                return length <= Integer.MAX_VALUE ? (int)length : -1;
            }
        }
        return -1;
    }

    private static int mldLength(byte[] d, int off) {
        if (off + 13 > d.length) return -1;
        long body = u32be(d, off + 4);
        long total = 8L + body;
        if (body < 5 || total > Integer.MAX_VALUE || off + total > d.length) return -1;
        return (int)total;
    }

    private static int zipLength(byte[] source, int offset) {
        final int eocdMin = 22;
        for (int p = offset + 4; p + eocdMin <= source.length; p++) {
            if ((source[p] & 0xff) != 0x50 || (source[p + 1] & 0xff) != 0x4b
                    || (source[p + 2] & 0xff) != 0x05 || (source[p + 3] & 0xff) != 0x06) continue;
            int disk = u16le(source, p + 4);
            int cdDisk = u16le(source, p + 6);
            int entriesDisk = u16le(source, p + 8);
            int entries = u16le(source, p + 10);
            long cdSize = u32le(source, p + 12);
            long cdOffset = u32le(source, p + 16);
            int commentLength = u16le(source, p + 20);
            long end = (long)p + eocdMin + commentLength;
            if (disk != 0 || cdDisk != 0 || entriesDisk != entries || entries <= 0) continue;
            if (end > source.length || (long)offset + cdOffset + cdSize != p) continue;
            long central = (long)offset + cdOffset;
            if (central < offset || central + 4 > source.length) continue;
            int c = (int)central;
            if ((source[c] & 0xff) != 0x50 || (source[c + 1] & 0xff) != 0x4b
                    || (source[c + 2] & 0xff) != 0x01 || (source[c + 3] & 0xff) != 0x02) continue;
            long length = end - offset;
            return length > Integer.MAX_VALUE ? -1 : (int)length;
        }
        return -1;
    }

    private static int u16le(byte[] d, int p) {
        return (d[p] & 0xff) | ((d[p + 1] & 0xff) << 8);
    }

    private static long u32le(byte[] d, int p) {
        return ((long)d[p] & 0xffL) | (((long)d[p + 1] & 0xffL) << 8)
                | (((long)d[p + 2] & 0xffL) << 16) | (((long)d[p + 3] & 0xffL) << 24);
    }

    private static long u32be(byte[] d, int p) {
        return (((long)d[p] & 0xffL) << 24) | (((long)d[p + 1] & 0xffL) << 16)
                | (((long)d[p + 2] & 0xffL) << 8) | ((long)d[p + 3] & 0xffL);
    }
}
