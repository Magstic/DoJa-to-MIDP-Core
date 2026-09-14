package com.nttdocomo.ui;

final class SoundRes implements MediaSound {
    private final String resourcePath;
    private final int durationMillis;

    SoundRes(String resourcePath, String ignoredContentType) {
        this.resourcePath = resourcePath;
        this.durationMillis = SoundCatalog.durationMillis(resourcePath);
    }

    public void use() {
        // 在需要 Player 時再創建。
    }

    String getResourcePath() { return resourcePath; }
    String getContentType() {
        return resourcePath != null && resourcePath.endsWith(".wav")
                ? "audio/x-wav" : "audio/midi";
    }
    int getDurationMillis() { return durationMillis; }
}
