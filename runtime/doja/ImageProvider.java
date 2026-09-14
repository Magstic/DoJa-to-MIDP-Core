package doja;

/** Wrapper 可插拔的圖片載入來源介面。
 * 當回傳 null 時，自動降級到內建的解碼處理路徑。 */
public interface ImageProvider {
    ImageResource openEncoded(byte[] data);
    ImageResource openScratchpad(String uri);
}
