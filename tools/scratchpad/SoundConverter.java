package doja.tools.scratchpad;

import doja.tools.io.FileIO;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import mld.api.MldConversion;
import mld.api.MldConverter;
import mld.api.MldPcm16;

/** 將 MLD payload 轉成 MIDP 較容易播放的 MIDI/WAV，並順手算好實際長度。 */
final class SoundConverter {
    static final class Result {
        final String extension;
        final int durationMillis;
        Result(String extension, int durationMillis) {
            this.extension = extension;
            this.durationMillis = durationMillis;
        }
    }

    private SoundConverter() {}

    static Result convert(byte[] bytes, File stem, boolean forceWav) throws Exception {
        MldConversion conversion = MldConverter.convert(bytes);
        boolean midi = conversion.hasMidi();
        boolean sampled = conversion.hasRenderableSampledAudio();
        if (midi == sampled) throw new IOException("MLD must resolve to exactly one MIDP sound representation");

        long micros;
        if (midi) {
            Sequence sequence = conversion.createMidiSequence();
            if (forceWav) {
                int millis = MidiToWav.render(sequence, new File(stem.getPath() + ".wav"));
                return new Result(".wav", millis);
            }
            File output = new File(stem.getPath() + ".mid");
            FileIO.ensureParent(output);
            if (MidiSystem.write(sequence, 1, output) <= 0) throw new IOException("no MIDI writer for " + output);
            micros = sequence.getMicrosecondLength();
            return new Result(".mid", millis(micros));
        }

        MldPcm16 pcm = conversion.renderSampledPcm16();
        File output = new File(stem.getPath() + ".wav");
        writeWav(pcm, output);
        micros = pcm.getFrameCount() * 1000000L / pcm.getSampleRate();
        return new Result(".wav", millis(micros));
    }

    private static int millis(long micros) {
        return (int)Math.max(1L, Math.min(Integer.MAX_VALUE, (micros + 999L) / 1000L));
    }

    private static void writeWav(MldPcm16 pcm, File output) throws Exception {
        int sampleRate = pcm.getSampleRate();
        int channels = pcm.getChannels();
        int frames = pcm.getFrameCount();
        short[] samples = pcm.copyInterleavedSamples();
        if (sampleRate <= 0 || channels <= 0 || frames < 0 || samples.length != frames * channels) {
            throw new IOException("invalid PCM returned by MLD player");
        }
        byte[] bytes = new byte[samples.length * 2];
        for (int i = 0, p = 0; i < samples.length; i++) {
            int value = samples[i];
            bytes[p++] = (byte)value;
            bytes[p++] = (byte)(value >>> 8);
        }
        FileIO.ensureParent(output);
        AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                sampleRate, 16, channels, channels * 2, sampleRate, false);
        AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(bytes), format, frames);
        try {
            if (AudioSystem.write(stream, AudioFileFormat.Type.WAVE, output) <= 0) throw new IOException("no WAV writer for " + output);
        } finally {
            stream.close();
        }
    }
}
