package doja.tools.image;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

/**
 * 從 byte 區段解出內嵌 GIF，再展開到它自己的 logical canvas。
 * 可見像素與二值 alpha 會照原圖留下；透明像素補上鄰近顏色，縮放時就不會滲出黑邊。
 * 最後仍輸出 indexed palette，PNG 大小通常能維持得很小。
 */
public final class GifImage {
    public static final class Result {
        public final BufferedImage image;
        public final int encodedLength;
        public final int encodedHash;
        public final int width;
        public final int height;

        Result(BufferedImage image, int encodedLength, int encodedHash, int width, int height) {
            this.image = image;
            this.encodedLength = encodedLength;
            this.encodedHash = encodedHash;
            this.width = width;
            this.height = height;
        }
    }

    private GifImage() {}

    public static boolean startsAt(byte[] data, int offset) {
        return offset >= 0 && offset + 6 <= data.length && data[offset] == 'G' && data[offset + 1] == 'I'
                && data[offset + 2] == 'F' && data[offset + 3] == '8'
                && (data[offset + 4] == '7' || data[offset + 4] == '9') && data[offset + 5] == 'a';
    }

    public static Result decode(byte[] data, int offset) throws IOException {
        Info info = parse(data, offset);
        BufferedImage frame = ImageIO.read(new ByteArrayInputStream(data, offset, info.length));
        if (frame == null) throw new IOException("cannot decode GIF at " + offset);
        if (frame.getWidth() != info.frameWidth || frame.getHeight() != info.frameHeight) {
            throw new IOException("unexpected GIF frame dimensions at " + offset);
        }
        ColorModel model = frame.getColorModel();
        if (!(model instanceof IndexColorModel)) throw new IOException("GIF is not palette indexed at " + offset);
        BufferedImage image = makeLogicalImage(frame, (IndexColorModel)model, info);
        return new Result(image, info.length, fnv1a(data, offset, info.length),
                info.logicalWidth, info.logicalHeight);
    }

    private static BufferedImage makeLogicalImage(BufferedImage frame, IndexColorModel source, Info info)
            throws IOException {
        int width = info.logicalWidth;
        int height = info.logicalHeight;
        int count = width * height;
        int sourceColors = source.getMapSize();
        byte[] sr = new byte[sourceColors];
        byte[] sg = new byte[sourceColors];
        byte[] sb = new byte[sourceColors];
        byte[] sa = new byte[sourceColors];
        source.getReds(sr);
        source.getGreens(sg);
        source.getBlues(sb);
        source.getAlphas(sa);

        int transparent = source.getTransparentPixel();
        int fill = transparent >= 0 ? transparent : info.backgroundIndex;
        int[] sourceIndex = new int[count];
        for (int i = 0; i < count; i++) sourceIndex[i] = fill;

        Raster input = frame.getRaster();
        for (int y = 0; y < info.frameHeight; y++) {
            int dy = info.frameTop + y;
            if (dy < 0 || dy >= height) continue;
            for (int x = 0; x < info.frameWidth; x++) {
                int dx = info.frameLeft + x;
                if (dx >= 0 && dx < width) sourceIndex[dy * width + dx] = input.getSample(x, y, 0);
            }
        }

        int[] opaqueMap = new int[sourceColors];
        for (int i = 0; i < opaqueMap.length; i++) opaqueMap[i] = -1;
        int[] pr = new int[256];
        int[] pg = new int[256];
        int[] pb = new int[256];
        int[] pa = new int[256];
        int opaqueCount = 0;
        for (int i = 0; i < count; i++) {
            int sourceId = sourceIndex[i];
            if ((sa[sourceId] & 0xff) == 0 || opaqueMap[sourceId] >= 0) continue;
            if (opaqueCount >= 255) throw new IOException("GIF uses too many opaque palette entries");
            opaqueMap[sourceId] = opaqueCount;
            pr[opaqueCount] = sr[sourceId] & 0xff;
            pg[opaqueCount] = sg[sourceId] & 0xff;
            pb[opaqueCount] = sb[sourceId] & 0xff;
            pa[opaqueCount] = 255;
            opaqueCount++;
        }

        int[] nearest = new int[count];
        for (int i = 0; i < count; i++) nearest[i] = -1;
        int[] queue = new int[count];
        int head = 0;
        int tail = 0;
        for (int i = 0; i < count; i++) {
            int sourceId = sourceIndex[i];
            int opaqueId = sourceId >= 0 && sourceId < opaqueMap.length ? opaqueMap[sourceId] : -1;
            if (opaqueId >= 0) {
                nearest[i] = opaqueId;
                queue[tail++] = i;
            }
        }
        while (head < tail) {
            int position = queue[head++];
            int owner = nearest[position];
            int x = position % width;
            int y = position / width;
            if (x > 0) tail = visitNearest(position - 1, owner, nearest, queue, tail);
            if (x + 1 < width) tail = visitNearest(position + 1, owner, nearest, queue, tail);
            if (y > 0) tail = visitNearest(position - width, owner, nearest, queue, tail);
            if (y + 1 < height) tail = visitNearest(position + width, owner, nearest, queue, tail);
        }

        int[] edgeUse = new int[opaqueCount];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int position = y * width + x;
                int sourceId = sourceIndex[position];
                if ((sa[sourceId] & 0xff) != 0) continue;
                boolean edge = (x > 0 && (sa[sourceIndex[position - 1]] & 0xff) != 0)
                        || (x + 1 < width && (sa[sourceIndex[position + 1]] & 0xff) != 0)
                        || (y > 0 && (sa[sourceIndex[position - width]] & 0xff) != 0)
                        || (y + 1 < height && (sa[sourceIndex[position + width]] & 0xff) != 0);
                if (edge && nearest[position] >= 0) edgeUse[nearest[position]]++;
            }
        }

        int transparentCapacity = 256 - opaqueCount;
        if (transparentCapacity <= 0) throw new IOException("no palette entry left for transparency");
        int[] transparentMap = new int[opaqueCount];
        for (int i = 0; i < opaqueCount; i++) transparentMap[i] = -1;

        int transparentCount = 0;
        while (transparentCount < transparentCapacity && transparentCount < opaqueCount) {
            int best = -1;
            int bestUse = -1;
            for (int i = 0; i < opaqueCount; i++) {
                if (transparentMap[i] < 0 && edgeUse[i] > bestUse) {
                    best = i;
                    bestUse = edgeUse[i];
                }
            }
            if (best < 0) break;
            int paletteIndex = opaqueCount + transparentCount;
            transparentMap[best] = paletteIndex;
            pr[paletteIndex] = pr[best];
            pg[paletteIndex] = pg[best];
            pb[paletteIndex] = pb[best];
            pa[paletteIndex] = 0;
            transparentCount++;
        }
        if (transparentCount == 0) {
            int paletteIndex = opaqueCount;
            pr[paletteIndex] = 0;
            pg[paletteIndex] = 0;
            pb[paletteIndex] = 0;
            pa[paletteIndex] = 0;
            transparentCount = 1;
        }

        for (int i = 0; i < opaqueCount; i++) {
            if (transparentMap[i] >= 0) continue;
            int best = opaqueCount;
            int bestDistance = Integer.MAX_VALUE;
            for (int j = 0; j < transparentCount; j++) {
                int paletteIndex = opaqueCount + j;
                int dr = pr[i] - pr[paletteIndex];
                int dg = pg[i] - pg[paletteIndex];
                int db = pb[i] - pb[paletteIndex];
                int distance = dr * dr + dg * dg + db * db;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = paletteIndex;
                }
            }
            transparentMap[i] = best;
        }

        int paletteSize = opaqueCount + transparentCount;
        byte[] r = new byte[paletteSize];
        byte[] g = new byte[paletteSize];
        byte[] b = new byte[paletteSize];
        byte[] a = new byte[paletteSize];
        for (int i = 0; i < paletteSize; i++) {
            r[i] = (byte)pr[i];
            g[i] = (byte)pg[i];
            b[i] = (byte)pb[i];
            a[i] = (byte)pa[i];
        }
        IndexColorModel palette = new IndexColorModel(8, paletteSize, r, g, b, a);
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED, palette);
        WritableRaster raster = output.getRaster();
        for (int i = 0; i < count; i++) {
            int sourceId = sourceIndex[i];
            int opaqueId = opaqueMap[sourceId];
            int paletteIndex;
            if ((sa[sourceId] & 0xff) != 0 && opaqueId >= 0) {
                paletteIndex = opaqueId;
            } else {
                int owner = nearest[i];
                paletteIndex = owner >= 0 ? transparentMap[owner] : opaqueCount;
            }
            raster.setSample(i % width, i / width, 0, paletteIndex);
        }
        return output;
    }

    private static int visitNearest(int position, int owner, int[] nearest, int[] queue, int tail) {
        if (nearest[position] >= 0) return tail;
        nearest[position] = owner;
        queue[tail++] = position;
        return tail;
    }

    private static Info parse(byte[] data, int start) throws IOException {
        if (!startsAt(data, start) || start + 13 > data.length) throw new IOException("invalid GIF header");
        Info info = new Info();
        info.logicalWidth = u16le(data, start + 6);
        info.logicalHeight = u16le(data, start + 8);
        info.backgroundIndex = data[start + 11] & 0xff;
        int position = start + 13;
        int packed = data[start + 10] & 0xff;
        if ((packed & 0x80) != 0) position += 3 * (1 << ((packed & 7) + 1));
        boolean foundFrame = false;
        while (position < data.length) {
            int marker = data[position++] & 0xff;
            if (marker == 0x3b) {
                info.length = position - start;
                if (!foundFrame) throw new IOException("GIF has no image frame at " + start);
                return info;
            }
            if (marker == 0x21) {
                if (position >= data.length) break;
                position++;
                position = skipSubBlocks(data, position);
            } else if (marker == 0x2c) {
                if (position + 9 > data.length) break;
                int left = u16le(data, position);
                int top = u16le(data, position + 2);
                int width = u16le(data, position + 4);
                int height = u16le(data, position + 6);
                int flags = data[position + 8] & 0xff;
                if (!foundFrame) {
                    info.frameLeft = left;
                    info.frameTop = top;
                    info.frameWidth = width;
                    info.frameHeight = height;
                    foundFrame = true;
                }
                position += 9;
                if ((flags & 0x80) != 0) position += 3 * (1 << ((flags & 7) + 1));
                if (position >= data.length) break;
                position++;
                position = skipSubBlocks(data, position);
            } else {
                throw new IOException("unexpected GIF marker 0x" + Integer.toHexString(marker));
            }
        }
        throw new IOException("unterminated GIF at " + start);
    }

    private static int skipSubBlocks(byte[] data, int position) throws IOException {
        while (position < data.length) {
            int size = data[position++] & 0xff;
            if (size == 0) return position;
            if (position + size > data.length) throw new IOException("truncated GIF sub-block");
            position += size;
        }
        throw new IOException("unterminated GIF sub-block");
    }

    private static int u16le(byte[] data, int position) {
        return (data[position] & 0xff) | ((data[position + 1] & 0xff) << 8);
    }

    private static int fnv1a(byte[] data, int offset, int length) {
        int hash = 0x811c9dc5;
        for (int i = 0; i < length; i++) {
            hash ^= data[offset + i] & 0xff;
            hash *= 0x01000193;
        }
        return hash;
    }

    private static final class Info {
        int length;
        int logicalWidth;
        int logicalHeight;
        int backgroundIndex;
        int frameLeft;
        int frameTop;
        int frameWidth;
        int frameHeight;
    }
}
