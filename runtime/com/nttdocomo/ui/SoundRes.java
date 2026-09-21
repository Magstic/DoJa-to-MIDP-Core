package com.nttdocomo.ui;

final class SoundRes implements MediaSound {
    private final String resourcePath;
    private final SoundData data;

    SoundRes(String resourcePath, String ignoredContentType) {
        this.resourcePath = resourcePath;
        this.data = SoundCatalog.find(resourcePath);
    }

    public void use() {
        // 在需要 Player 時再創建。
    }

    String getResourcePath() { return resourcePath; }
    String getContentType() {
        return getContentType(0);
    }
    String getContentType(int segment) {
        String path = data.path(segment);
        return path != null && path.endsWith(".wav")
                ? "audio/x-wav" : "audio/midi";
    }
    int getDurationMillis() { return data.durationMillis(); }
    int getSegmentCount() { return data.segmentCount(); }
    String getSegmentPath(int segment) { return data.path(segment); }
    boolean hasNativeLoop() { return data.hasNativeLoop(); }
    boolean playerCanLoopSegment(int segment) { return data.playerCanLoopSegment(segment); }
    SoundData.Position positionAt(long timelineMillis) { return data.positionAt(timelineMillis); }
    long millisUntilSegmentBoundary(long timelineMillis) {
        return data.millisUntilSegmentBoundary(timelineMillis);
    }
}
