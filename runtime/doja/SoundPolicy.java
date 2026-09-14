package doja;

/** 令遊戲判斷一段聲音是 BGM 或 SFX。 */
public interface SoundPolicy {
    int BGM = 1;
    int SFX = 2;

    int classify(String resourcePath);
}
