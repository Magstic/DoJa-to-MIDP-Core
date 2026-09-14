package doja;

/** Provider 所持有的圖片控制程式碼，Runtime 僅從中擷取最終用於繪製的 LCDUI Image 物件。 */
public interface ImageResource {
    javax.microedition.lcdui.Image getImage();
    int getWidth();
    int getHeight();
    void dispose();
}
