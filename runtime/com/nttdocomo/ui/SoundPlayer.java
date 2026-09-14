package com.nttdocomo.ui;

import doja.Resources;

import java.io.InputStream;

import javax.microedition.media.Manager;
import javax.microedition.media.Player;
import javax.microedition.media.control.VolumeControl;

/**
 * 掌管單一 DoJa 音訊通道的播放控制器。
 * 
 * 1. 邏輯與發聲解耦：遊戲層可見的時間軸、循環計數與播放完成事件，統一由邏輯時鐘（Logical Clock）維護；MMAPI 僅單純負責音訊發聲。
 * 2. 靜音資源優化：當處於靜音狀態時，完全不建立 MMAPI Player，顯著降低舊型 S60 等裝置的 CPU 負擔。
 * 3. Worker 執行緒獨立串行：每個 Port 最多維持一個 Player 實體。同一資源重複播放時直接沿用；更換資源則交由背景 Worker 執行緒處理，避免建立或釋放音訊裝置時阻塞遊戲主執行緒（Update Thread）。
 */
final class SoundPlayer implements Runnable {
    interface Listener {
        void onPlaybackStarted(int token);
        void onPlaybackCompleted(int token);
        void onPlaybackPreempted(int token);
        void onPlaybackError(int token, String message);
    }

    private static final int COMMAND_NONE = 0;
    private static final int COMMAND_PLAY = 1;
    private static final int COMMAND_STOP = 2;

    private final int port;

    /* MMAPI 生命週期完全由該 Port 的 Worker 執行緒串行處理，避免 start/stop/close 操作產生併發衝突。
     * 播放完成的 DoJa 事件依然由邏輯時鐘決定。 */
    private Player player;
    private InputStream input;
    private SoundRes loadedSound;

    /* 遊戲可見的播放狀態，和實體 Player 分開保存。 */
    private SoundRes activeSound;
    private Listener activeListener;
    private int activeToken;
    private int activeLoops = 1;
    private int activeVolume = 100;
    private int activeRatePercent = 100;
    private int activePitchSemitones;
    private long timelineBaseMillis;
    private long timelineWallMillis;

    /* 指令佇列僅保留最新一筆。
     * 連續播放音效時，可避免舊的音效阻塞 JVM。 */
    private Thread worker;
    private int commandSerial;
    private int handledCommandSerial;
    private int command = COMMAND_NONE;
    private SoundRes commandSound;
    private Listener commandListener;
    private int commandToken;
    private int commandLoops = 1;
    private int commandStartMillis;
    private int commandVolume = 100;
    private int commandRatePercent = 100;
    private int commandPitchSemitones;

    private int controlSerial;
    private int handledControlSerial;
    private Listener controlListener;
    private int controlToken;
    private int controlVolume = 100;
    private int controlRatePercent = 100;
    private int controlPitchSemitones;

    SoundPlayer(int port) {
        this.port = port;
    }

    boolean play(SoundRes sound, int loops, int startMillis, int volume,
            int ratePercent, int pitchSemitones, Listener listener, int token) {
        if (sound == null || listener == null) return false;

        Listener superseded = null;
        int supersededToken = 0;
        synchronized (this) {
            ensureWorkerLocked();
            if (commandSerial != handledCommandSerial
                    && command == COMMAND_PLAY && commandListener != null
                    && (commandListener != listener || commandToken != token)) {
                superseded = commandListener;
                supersededToken = commandToken;
            }
            commandSound = sound;
            commandListener = listener;
            commandToken = token;
            commandLoops = normalizeLoops(loops);
            commandStartMillis = startMillis < 0 ? 0 : startMillis;
            commandVolume = clamp(volume, 0, 100);
            commandRatePercent = normalizeRate(ratePercent);
            commandPitchSemitones = pitchSemitones;
            command = COMMAND_PLAY;
            commandSerial++;
            notifyAll();
        }
        if (superseded != null) superseded.onPlaybackPreempted(supersededToken);
        return true;
    }

    boolean stop(Listener listener, int token) {
        if (listener == null) return false;
        synchronized (this) {
            boolean ownsPending = commandSerial != handledCommandSerial
                    && command == COMMAND_PLAY
                    && commandListener == listener && commandToken == token;
            boolean ownsActive = activeListener == listener && activeToken == token;
            if (!ownsPending && !ownsActive) return false;
            ensureWorkerLocked();
            commandSound = null;
            commandListener = listener;
            commandToken = token;
            command = COMMAND_STOP;
            commandSerial++;
            notifyAll();
            return true;
        }
    }

    void updateControls(Listener listener, int token, int volume,
            int ratePercent, int pitchSemitones) {
        if (listener == null) return;
        synchronized (this) {
            if (activeListener != listener || activeToken != token) return;
            ensureWorkerLocked();
            controlListener = listener;
            controlToken = token;
            controlVolume = clamp(volume, 0, 100);
            controlRatePercent = normalizeRate(ratePercent);
            controlPitchSemitones = pitchSemitones;
            controlSerial++;
            notifyAll();
        }
    }

    int getCurrentTimeMillis(Listener listener, int token) {
        synchronized (this) {
            if (activeListener != listener || activeToken != token || activeSound == null) return 0;
            long timeline = currentTimelineLocked(System.currentTimeMillis());
            int duration = activeSound.getDurationMillis();
            if (duration > 0) timeline %= duration;
            if (timeline < 0) timeline = 0;
            return timeline > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)timeline;
        }
    }

    public void run() {
        for (;;) {
            int nextCommand = COMMAND_NONE;
            int serial = 0;
            SoundRes sound = null;
            Listener listener = null;
            int token = 0;
            int loops = 1;
            int startMillis = 0;
            int volume = 100;
            int ratePercent = 100;
            int pitchSemitones = 0;

            boolean hasControls = false;
            Listener controlsOwner = null;
            int controlsToken = 0;
            int controlsVolume = 100;
            int controlsRate = 100;
            int controlsPitch = 0;

            Listener completedOwner = null;
            int completedToken = 0;

            synchronized (this) {
                for (;;) {
                    if (commandSerial != handledCommandSerial) {
                        handledCommandSerial = commandSerial;
                        serial = commandSerial;
                        nextCommand = command;
                        sound = commandSound;
                        listener = commandListener;
                        token = commandToken;
                        loops = commandLoops;
                        startMillis = commandStartMillis;
                        volume = commandVolume;
                        ratePercent = commandRatePercent;
                        pitchSemitones = commandPitchSemitones;
                        break;
                    }
                    if (controlSerial != handledControlSerial) {
                        handledControlSerial = controlSerial;
                        controlsOwner = controlListener;
                        controlsToken = controlToken;
                        controlsVolume = controlVolume;
                        controlsRate = controlRatePercent;
                        controlsPitch = controlPitchSemitones;
                        hasControls = true;
                        break;
                    }
                    if (isLogicalCompleteLocked(System.currentTimeMillis())) {
                        completedOwner = activeListener;
                        completedToken = activeToken;
                        clearActiveLocked();
                        break;
                    }

                    long waitMillis = millisUntilCompletionLocked(System.currentTimeMillis());
                    try {
                        if (waitMillis < 0) wait();
                        else wait(waitMillis < 1 ? 1 : waitMillis);
                    } catch (InterruptedException ignored) {}
                }
            }

            if (completedOwner != null) {
                stopPhysicalRetain();
                completedOwner.onPlaybackCompleted(completedToken);
                continue;
            }
            if (nextCommand == COMMAND_PLAY) {
                processPlay(serial, sound, loops, startMillis, volume,
                        ratePercent, pitchSemitones, listener, token);
            } else if (nextCommand == COMMAND_STOP) {
                processStop(serial, listener, token);
            } else if (hasControls) {
                processControls(controlsOwner, controlsToken, controlsVolume,
                        controlsRate, controlsPitch);
            }
        }
    }

    private void processPlay(int serial, SoundRes sound, int loops, int startMillis,
            int volume, int ratePercent, int pitchSemitones, Listener listener, int token) {
        if (sound == null || listener == null || !isCurrentCommand(serial, COMMAND_PLAY)) return;

        Listener previousOwner;
        int previousToken;
        synchronized (this) {
            previousOwner = activeListener;
            previousToken = activeToken;
            if (previousOwner != null) clearActiveLocked();
        }
        if (previousOwner != null && (previousOwner != listener || previousToken != token)) {
            previousOwner.onPlaybackPreempted(previousToken);
        }
        if (!isCurrentCommand(serial, COMMAND_PLAY)) return;

        stopPhysicalRetain();

        long now = System.currentTimeMillis();
        int duration = sound.getDurationMillis();
        long start = startMillis;
        if (duration > 0 && start > duration) start = duration;
        synchronized (this) {
            if (!isCurrentCommandLocked(serial, COMMAND_PLAY)) return;
            activeSound = sound;
            activeListener = listener;
            activeToken = token;
            activeLoops = loops;
            activeVolume = volume;
            activeRatePercent = ratePercent;
            activePitchSemitones = pitchSemitones;
            timelineBaseMillis = start;
            timelineWallMillis = now;
            notifyAll();
        }

        if (volume <= 0) {
            // 靜音直接跳過 MMAPI。
            closePhysical();
            if (isActiveOwner(listener, token)) listener.onPlaybackStarted(token);
            return;
        }

        try {
            if (!startPhysical(listener, token)) return;
            // S60 的 create/realize 可能很慢，需要等 MMAPI 真正啟動後，再重設時間。
            synchronized (this) {
                if (activeListener == listener && activeToken == token) {
                    timelineBaseMillis = start;
                    timelineWallMillis = System.currentTimeMillis();
                    notifyAll();
                }
            }
            if (isActiveOwner(listener, token)) listener.onPlaybackStarted(token);
        } catch (Throwable failure) {
            clearActiveIfOwned(listener, token);
            closePhysical();
            if (isCurrentCommand(serial, COMMAND_PLAY)) {
                listener.onPlaybackError(token, "port " + port + ": " + failure.toString());
            }
        }
    }

    private void processStop(int serial, Listener owner, int token) {
        if (owner == null || !isCurrentCommand(serial, COMMAND_STOP)) return;
        boolean stopped = false;
        synchronized (this) {
            if (activeListener == owner && activeToken == token) {
                clearActiveLocked();
                stopped = true;
                notifyAll();
            }
        }
        if (stopped) stopPhysicalRetain();
    }

    private void processControls(Listener owner, int token, int volume,
            int ratePercent, int pitchSemitones) {
        SoundRes sound;
        synchronized (this) {
            if (activeListener != owner || activeToken != token || activeSound == null) return;
            long now = System.currentTimeMillis();
            timelineBaseMillis = currentTimelineLocked(now);
            timelineWallMillis = now;
            activeVolume = volume;
            activeRatePercent = ratePercent;
            activePitchSemitones = pitchSemitones;
            sound = activeSound;
            notifyAll();
        }

        if (volume <= 0) {
            // 音量歸零就不再創建 PLAYER。
            closePhysical();
            return;
        }

        try {
            Player current;
            SoundRes currentSound;
            synchronized (this) {
                if (activeListener != owner || activeToken != token) return;
                current = player;
                currentSound = loadedSound;
            }
            if (current == null || !sameResource(currentSound, sound)) {
                startPhysical(owner, token);
            } else {
                applyControls(current, volume, ratePercent, pitchSemitones);
            }
        } catch (Throwable failure) {
            clearActiveIfOwned(owner, token);
            closePhysical();
            owner.onPlaybackError(token, "port " + port + ": " + failure.toString());
        }
    }

    // 斷點播放
    private boolean startPhysical(Listener owner, int token) throws Exception {
        SoundRes sound;
        int volume;
        int rate;
        int pitch;
        int loops;
        long timeline;
        synchronized (this) {
            if (activeListener != owner || activeToken != token || activeSound == null) return false;
            sound = activeSound;
            volume = activeVolume;
            rate = activeRatePercent;
            pitch = activePitchSemitones;
            loops = activeLoops;
            timeline = currentTimelineLocked(System.currentTimeMillis());
        }
        if (volume <= 0) return false;

        int duration = sound.getDurationMillis();
        int mediaPosition = timeline > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)timeline;
        int remainingLoops = loops;
        if (duration > 0) {
            long completedLoops = timeline / duration;
            if (loops >= 0) {
                long remain = (long)loops - completedLoops;
                if (remain <= 0) return false;
                remainingLoops = remain > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)remain;
            }
            mediaPosition = (int)(timeline % duration);
        }

        Player current;
        SoundRes currentSound;
        synchronized (this) {
            current = player;
            currentSound = loadedSound;
        }

        if (current == null || !sameResource(currentSound, sound)) {
            closePhysical();
            InputStream newInput = Resources.open(sound.getResourcePath());
            if (newInput == null) {
                throw new IllegalStateException("Missing sound resource: " + sound.getResourcePath());
            }
            Player newPlayer = null;
            try {
                newPlayer = Manager.createPlayer(newInput, sound.getContentType());
                newPlayer.realize();
                boolean rejected;
                synchronized (this) {
                    rejected = activeListener != owner || activeToken != token || activeVolume <= 0;
                    if (!rejected) {
                        player = newPlayer;
                        input = newInput;
                        loadedSound = sound;
                        current = newPlayer;
                    }
                }
                if (rejected) {
                    closeDetached(newPlayer, newInput);
                    return false;
                }
            } catch (Throwable failure) {
                closeDetached(newPlayer, newInput);
                if (failure instanceof Exception) throw (Exception)failure;
                throw new Exception(failure.toString());
            }
        } else {
            try { current.stop(); } catch (Throwable ignored) {}
        }

        current.setLoopCount(remainingLoops < 0 ? -1 : (remainingLoops < 1 ? 1 : remainingLoops));
        try { current.setMediaTime((long)mediaPosition * 1000L); }
        catch (Throwable ignored) {}
        applyControls(current, volume, rate, pitch);

        synchronized (this) {
            if (activeListener != owner || activeToken != token || activeVolume <= 0 || player != current) {
                return false;
            }
        }
        current.start();
        // 部分後端直到調用 start() 時才會真正取得音訊裝置，
        // 因此於啟動後再次套用音量、速率與音高，確保延遲建立裝置的實作能套用正確設定。
        applyControls(current, volume, rate, pitch);
        return true;
    }

    private void applyControls(Player target, int volume, int ratePercent, int pitchSemitones) {
        try {
            VolumeControl control = (VolumeControl)target.getControl("VolumeControl");
            if (control != null) {
                if (volume <= 0) control.setMute(true);
                else {
                    control.setMute(false);
                    control.setLevel(clamp(volume, 0, 100));
                }
            }
        } catch (Throwable ignored) {}
    }

    /** 
     * 停止播放後，保留同一資源已載入的 Player。
     * 這可以減少下一次重播時昂貴的初始化開銷。 
    */
    private void stopPhysicalRetain() {
        Player current;
        synchronized (this) { current = player; }
        if (current != null) {
            try { current.stop(); } catch (Throwable ignored) {}
        }
    }

    /** 
     * 關閉並釋放原生後端資源。
     * 當靜音或更換資源時，可立即回收硬體裝置資源。 
    */
    private void closePhysical() {
        Player oldPlayer;
        InputStream oldInput;
        synchronized (this) {
            oldPlayer = player;
            oldInput = input;
            player = null;
            input = null;
            loadedSound = null;
        }
        closeDetached(oldPlayer, oldInput);
    }

    private void closeDetached(Player oldPlayer, InputStream oldInput) {
        if (oldPlayer != null) {
            try { oldPlayer.close(); } catch (Throwable ignored) {}
        }
        if (oldInput != null) {
            try { oldInput.close(); } catch (Throwable ignored) {}
        }
    }

    private boolean isLogicalCompleteLocked(long now) {
        if (activeListener == null || activeSound == null || activeLoops < 0) return false;
        int duration = activeSound.getDurationMillis();
        if (duration <= 0) return false;
        long end = (long)duration * activeLoops;
        return currentTimelineLocked(now) >= end;
    }

    /** 回傳 -1 代表無需設定計時器（例如：空閒、無限循環或聲音長度未知）。 */
    private long millisUntilCompletionLocked(long now) {
        if (activeListener == null || activeSound == null || activeLoops < 0) return -1;
        int duration = activeSound.getDurationMillis();
        if (duration <= 0) return -1;
        long remainingMedia = (long)duration * activeLoops - currentTimelineLocked(now);
        if (remainingMedia <= 0) return 0;
        int rate = normalizeRate(activeRatePercent);
        long remainingWall = (remainingMedia * 100L + rate - 1L) / rate;
        return remainingWall > Integer.MAX_VALUE ? Integer.MAX_VALUE : remainingWall;
    }

    private long currentTimelineLocked(long now) {
        long elapsed = now - timelineWallMillis;
        if (elapsed < 0) elapsed = 0;
        return timelineBaseMillis + elapsed * normalizeRate(activeRatePercent) / 100L;
    }

    private void clearActiveIfOwned(Listener owner, int token) {
        synchronized (this) {
            if (activeListener == owner && activeToken == token) {
                clearActiveLocked();
                notifyAll();
            }
        }
    }

    private void clearActiveLocked() {
        activeSound = null;
        activeListener = null;
        activeToken = 0;
        activeLoops = 1;
        timelineBaseMillis = 0;
        timelineWallMillis = 0;
    }

    private boolean isActiveOwner(Listener owner, int token) {
        synchronized (this) {
            return activeListener == owner && activeToken == token;
        }
    }

    private boolean isCurrentCommand(int serial, int expectedCommand) {
        synchronized (this) { return isCurrentCommandLocked(serial, expectedCommand); }
    }

    private boolean isCurrentCommandLocked(int serial, int expectedCommand) {
        return serial == commandSerial && command == expectedCommand;
    }

    private void ensureWorkerLocked() {
        if (worker != null) return;
        worker = new Thread(this);
        worker.start();
    }

    private static boolean sameResource(SoundRes first, SoundRes second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        String firstPath = first.getResourcePath();
        String secondPath = second.getResourcePath();
        if (firstPath == null ? secondPath != null : !firstPath.equals(secondPath)) return false;
        String firstType = first.getContentType();
        String secondType = second.getContentType();
        return firstType == null ? secondType == null : firstType.equals(secondType);
    }

    private static int normalizeLoops(int loops) {
        return loops < 0 ? -1 : (loops < 1 ? 1 : loops);
    }

    private static int normalizeRate(int ratePercent) {
        return ratePercent <= 0 ? 100 : ratePercent;
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }
}
