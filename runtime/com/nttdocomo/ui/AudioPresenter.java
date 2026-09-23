package com.nttdocomo.ui;

import doja.SoundPolicy;

/**
 * DoJa audio presenter 的薄包裝，目前對應兩條 logical port。
 * 遊戲常用「拿不拿得到 presenter」探測能力，因此回傳結果要和 backend 真正可播的 port 一致。
 */
public class AudioPresenter extends MediaPresenter implements SoundPlayer.Listener {
    public static final int AUDIO_PLAYING = 1;
    public static final int AUDIO_STOPPED = 2;
    public static final int AUDIO_COMPLETE = 3;

    private static final int ATTR_KEY = 3;
    private static final int ATTR_VOLUME = 4;
    private static final int ATTR_TEMPO = 5;
    private static final int ATTR_LOOP_COUNT = 6;

    private static final int PORT_COUNT = 2;
    private static final SoundPlayer[] PORTS = {
        new SoundPlayer(0), new SoundPlayer(1)
    };

    private final SoundPlayer player;
    private MediaSound sound;
    private int volume = 100;
    private int tempo = 100;
    private int key = 5;
    private int loopCount;
    private SoundPolicy soundPolicy;
    private boolean bgmOutputEnabled = true;
    private boolean sfxOutputEnabled = true;
    private int generation;
    private volatile boolean playing;
    private boolean paused;
    private int pausedMillis;

    private AudioPresenter(int port) {
        if (port < 0 || port >= PORT_COUNT) {
            throw new IllegalArgumentException("audio port unavailable: " + port);
        }
        player = PORTS[port];
    }

    /** 取得預設音訊通道：當未指定 Port 時，預設使用第 0 號 Lane（符合絕大多數 DoJa 遊戲的運作預期）。 */
    public static AudioPresenter getAudioPresenter() { return new AudioPresenter(0); }

    /** 取得指定 Port 的音訊通道（建構子內部會自動檢查並確保該 Port 符合後端支援範圍）。 */
    public static AudioPresenter getAudioPresenter(int port) { return new AudioPresenter(port); }

    /** 綁定並載入指定音效資源。設定前會先停止當前播放，若傳入資源不為 null 則會將其標記為使用中。 */
    public void setSound(MediaSound sound) {
        stop();
        paused = false;
        pausedMillis = 0;
        this.sound = sound;
        if (sound != null) sound.use();
    }

    public void setAttribute(int attribute, int value) {
        switch (attribute) {
            case ATTR_KEY: key = value; break;
            case ATTR_VOLUME: volume = clamp(value, 0, 100); break;
            case ATTR_TEMPO: tempo = value; break;
            case ATTR_LOOP_COUNT: loopCount = value; break;
            default: return;
        }
        player.updateControls(this, generation, effectiveVolume(), tempo, key - 5);
    }


    /** 讓 Wrapper 按遊戲用途分 BGM/SFX，避免拿轉檔後的副檔名直接決定聲音類型。 */
    public void setSoundPolicy(SoundPolicy policy, boolean bgmEnabled, boolean sfxEnabled) {
        soundPolicy = policy;
        bgmOutputEnabled = bgmEnabled;
        sfxOutputEnabled = sfxEnabled;
        player.updateControls(this, generation, effectiveVolume(), tempo, key - 5);
    }


    public int getCurrentTime() {
        return player.getCurrentTimeMillis(this, generation);
    }

    public MediaResource getMediaResource() {
        return sound;
    }

    public void play() { play(0); }

    public void play(int startMillis) {
        paused = false;
        pausedMillis = 0;
        int token = ++generation;
        if (!(sound instanceof SoundRes)) {
            playing = false;
            fireMediaAction(AUDIO_COMPLETE, 0);
            return;
        }
        playing = true;
        int loops = loopCount < 0 ? -1 : (loopCount < 1 ? 1 : loopCount);
        if (!player.play((SoundRes)sound, loops, startMillis, effectiveVolume(), tempo,
                key - 5, this, token)) {
            if (generation == token) playing = false;
            fireMediaAction(AUDIO_COMPLETE, 0);
        }
    }

    public void pause() {
        if (!playing) return;
        pausedMillis = player.getCurrentTimeMillis(this, generation);
        int token = generation;
        playing = false;
        paused = true;
        player.stop(this, token);
        generation++;
    }

    public void restart() {
        if (!paused) return;
        int startMillis = pausedMillis;
        paused = false;
        pausedMillis = 0;
        play(startMillis);
    }

    public void stop() {
        int token = generation;
        boolean notify = playing;
        playing = false;
        paused = false;
        pausedMillis = 0;
        player.stop(this, token);
        generation++;
        if (notify) fireMediaAction(AUDIO_STOPPED, 0);
    }

    public void onPlaybackStarted(int token) {
        if (token == generation && playing) fireMediaAction(AUDIO_PLAYING, 0);
    }

    public void onPlaybackCompleted(int token) {
        if (token != generation || !playing) return;
        playing = false;
        fireMediaAction(AUDIO_COMPLETE, 0);
    }

    public void onPlaybackPreempted(int token) {
        if (token != generation || !playing) return;
        playing = false;
        // 若是同一個 port 換了 owner，舊 presenter 必須要收到停止事件，狀態才不會卡在 playing。
        fireMediaAction(AUDIO_STOPPED, 0);
    }

    public void onPlaybackError(int token, String message) {
        if (token != generation || !playing) return;
        playing = false;
        fireMediaAction(AUDIO_COMPLETE, 0);
    }

    private int effectiveVolume() {
        if (!(sound instanceof SoundRes) || soundPolicy == null) return volume;
        String resourcePath = ((SoundRes)sound).getResourcePath();
        int category = soundPolicy.classify(resourcePath);
        if (category == SoundPolicy.BGM) return bgmOutputEnabled ? volume : 0;
        if (category == SoundPolicy.SFX) return sfxOutputEnabled ? volume : 0;
        throw new IllegalArgumentException("unclassified sound: " + resourcePath);
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }
}
