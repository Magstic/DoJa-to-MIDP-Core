package com.nttdocomo.ui;

/** 單一打包音效的不可變描述資訊與時間軸映射器。 */
final class SoundData {
    static final class Position {
        final int segmentIndex;
        final int segmentMillis;
        final int playlistMillis;

        Position(int segmentIndex, int segmentMillis, int playlistMillis) {
            this.segmentIndex = segmentIndex;
            this.segmentMillis = segmentMillis;
            this.playlistMillis = playlistMillis;
        }
    }

    private final String[] paths;
    private final int[] durations;
    private final int[] starts;
    private final int loopSegmentIndex;
    private final int totalDuration;

    SoundData(String[] paths, int[] durations, int loopSegmentIndex) {
        if (paths == null || durations == null || paths.length == 0
                || paths.length != durations.length) {
            throw new IllegalArgumentException("invalid sound data");
        }
        if (loopSegmentIndex < -1 || loopSegmentIndex >= paths.length) {
            throw new IllegalArgumentException("invalid sound loop segment");
        }
        this.paths = new String[paths.length];
        this.durations = new int[durations.length];
        starts = new int[paths.length];
        long total = 0L;
        for (int i = 0; i < paths.length; i++) {
            this.paths[i] = paths[i];
            this.durations[i] = durations[i];
            starts[i] = total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)total;
            if (durations[i] > 0) total += durations[i];
            if (total > Integer.MAX_VALUE) total = Integer.MAX_VALUE;
        }
        this.loopSegmentIndex = loopSegmentIndex;
        totalDuration = (int)total;
    }

    int segmentCount() { return paths.length; }
    String path(int index) { return paths[index]; }
    int segmentDuration(int index) { return durations[index]; }
    int durationMillis() { return totalDuration; }
    boolean hasNativeLoop() { return loopSegmentIndex >= 0; }

    boolean playerCanLoopSegment(int index) {
        return loopSegmentIndex >= 0
                && index == loopSegmentIndex
                && loopSegmentIndex == paths.length - 1;
    }

    Position positionAt(long timelineMillis) {
        long position = Math.max(0L, timelineMillis);
        if (totalDuration > 0) {
            if (loopSegmentIndex >= 0 && position >= totalDuration) {
                int loopStart = starts[loopSegmentIndex];
                int loopDuration = totalDuration - loopStart;
                position = loopDuration > 0
                        ? loopStart + ((position - loopStart) % loopDuration)
                        : loopStart;
            } else if (loopSegmentIndex < 0 && position >= totalDuration) {
                position %= totalDuration;
            }
        }

        int index = paths.length - 1;
        for (int i = 0; i < paths.length; i++) {
            long end = (long)starts[i] + Math.max(0, durations[i]);
            if (position < end || i == paths.length - 1) {
                index = i;
                break;
            }
        }
        long local = position - starts[index];
        if (local < 0L) local = 0L;
        if (local > Integer.MAX_VALUE) local = Integer.MAX_VALUE;
        return new Position(index, (int)local, (int)position);
    }

    long millisUntilSegmentBoundary(long timelineMillis) {
        Position position = positionAt(timelineMillis);
        if (playerCanLoopSegment(position.segmentIndex)) return -1L;
        int duration = durations[position.segmentIndex];
        if (duration <= 0) return -1L;
        return Math.max(0L, (long)duration - position.segmentMillis);
    }
}
