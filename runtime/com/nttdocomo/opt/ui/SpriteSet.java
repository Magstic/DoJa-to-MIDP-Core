package com.nttdocomo.opt.ui;

import com.nttdocomo.ui.Image;

/** 帶矩形碰撞標籤的 Graphics2 舊版可選精靈集。 */
public class SpriteSet {
    private final Sprite[] sprites;
    private final int[] collisionFlags;

    public SpriteSet(Sprite[] sprites) {
        if (sprites == null) throw new NullPointerException("sprites");
        if (sprites.length == 0 || sprites.length > 32) throw new IllegalArgumentException("sprite count");
        this.sprites = sprites;
        collisionFlags = new int[sprites.length];
    }

    public int getCount() { return sprites.length; }
    public Sprite[] getSprites() { return sprites; }

    public Sprite getSprite(int index) {
        checkIndex(index);
        return sprites[index];
    }

    public void setCollisionAll() {
        validateElements();
        for (int i = 0; i < sprites.length; i++) collisionFlags[i] = collisionMask(i);
    }

    public void setCollisionOf(int index) {
        checkIndex(index);
        validateElements();
        collisionFlags[index] = collisionMask(index);
    }

    public boolean isCollision(int index1, int index2) {
        checkIndex(index1);
        checkIndex(index2);
        if (index1 == index2) return false;
        Sprite a = sprites[index1];
        Sprite b = sprites[index2];
        if (a == null || b == null) return false;
        return collides(a, b);
    }

    public int getCollisionFlag(int index) {
        checkIndex(index);
        return collisionFlags[index];
    }

    private int collisionMask(int index) {
        Sprite source = sprites[index];
        int flag = 0;
        if (!isCollisionTarget(source)) return 0;
        for (int i = 0; i < sprites.length; i++) {
            if (i != index && collides(source, sprites[i])) flag |= (1 << i);
        }
        return flag;
    }

    private static boolean collides(Sprite a, Sprite b) {
        if (!isCollisionTarget(a) || !isCollisionTarget(b)) return false;
        int ar = a.getX() + a.getWidth();
        int ab = a.getY() + a.getHeight();
        int br = b.getX() + b.getWidth();
        int bb = b.getY() + b.getHeight();
        return a.getX() < br && b.getX() < ar && a.getY() < bb && b.getY() < ab;
    }

    private static boolean isCollisionTarget(Sprite sprite) {
        if (sprite == null || !sprite.isVisible()) return false;
        Image image = sprite.image();
        return image != null && image.getMIDPImage() != null && sprite.getWidth() > 0 && sprite.getHeight() > 0;
    }

    private void validateElements() {
        for (int i = 0; i < sprites.length; i++) if (sprites[i] == null) throw new NullPointerException("sprite");
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= sprites.length) throw new ArrayIndexOutOfBoundsException(index);
    }
}
