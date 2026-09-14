package com.nttdocomo.ui;

/**
 * 保證寬鬆的調色板實作。
 * 根據 DoJa 5.1 DOc 嚴格實作的測試結果 和 IDKDOJA 的實作來看，官方文檔中提供的並不準確……
 * 至少就《魔界塔士 SAGA》而言，現有的實作是必須的。
 */
public class Palette {
    private final int[] entries;

    public Palette(int count) {
        if (count < 0) count = 0;
        entries = new int[count];
    }

    public Palette(int[] colors) {
        int i;
        entries = new int[colors == null ? 0 : colors.length];
        for (i = 0; i < entries.length; i++) {
            entries[i] = colors[i];
        }
    }

    public void setEntry(int index, int color) {
        if (index >= 0 && index < entries.length) {
            entries[index] = color;
        }
    }

    public int getEntry(int index) {
        if (index < 0 || index >= entries.length) {
            return 0;
        }
        return entries[index];
    }

    public int getEntryCount() {
        return entries.length;
    }

    int[] copyEntries() {
        int[] copy = new int[entries.length];
        System.arraycopy(entries, 0, copy, 0, entries.length);
        return copy;
    }
}
