package io.github.jeremyjia.stardrift;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.SystemClock;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Small original synthesized effects; no network, external audio, or long-running audio thread. */
final class SoundFx {
    private static final int SAMPLE_RATE = 24000;
    private static final double[] UPGRADE_NOTES = {523.25, 659.25, 783.99, 1046.5};
    private static final double[] WIN_NOTES = {523.25, 659.25, 783.99, 1046.5, 1318.5};
    private final SoundPool pool;
    private final Map<String, Integer> sounds = new HashMap<>();
    private final Set<Integer> loaded = new HashSet<>();
    private final int[] streams = new int[16];
    private int nextStream;
    private boolean enabled = true;
    private boolean active;
    private boolean released;
    private long lastShot;
    private long lastKill;

    SoundFx(Context context) {
        pool = new SoundPool.Builder().setMaxStreams(6)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .build();
        pool.setOnLoadCompleteListener((soundPool, sampleId, status) -> {
            if (status == 0) loaded.add(sampleId);
        });
        File directory = new File(context.getCacheDir(), "sounds-v1");
        if (!directory.exists() && !directory.mkdirs()) return;
        String[] names = {"shoot", "kill", "hit", "upgrade", "pulse", "win", "lose", "tap"};
        for (String name : names) {
            try {
                File wave = new File(directory, name + ".wav");
                if (!wave.isFile()) writeWave(wave, name);
                sounds.put(name, pool.load(wave.getAbsolutePath(), 1));
            } catch (IOException | RuntimeException ignored) {
                // Audio is optional, including when the cache cannot be written.
            }
        }
    }

    void setEnabled(boolean value) {
        enabled = value;
        if (!enabled) stopStreams();
    }

    void resume() {
        active = true;
    }

    void play(String name) {
        if (released || !enabled || !active) return;
        if ("start".equals(name)) name = "upgrade";
        Integer soundId = sounds.get(name);
        if (soundId == null || !loaded.contains(soundId)) return;
        long now = SystemClock.uptimeMillis();
        if ("shoot".equals(name)) {
            if (now - lastShot < 90) return;
            lastShot = now;
        }
        if ("kill".equals(name)) {
            if (now - lastKill < 65) return;
            lastKill = now;
        }
        float volume;
        switch (name) {
            case "shoot": volume = 0.14f; break;
            case "kill": volume = 0.25f; break;
            case "tap": volume = 0.2f; break;
            case "hit": volume = 0.42f; break;
            default: volume = 0.4f; break;
        }
        streams[nextStream] = pool.play(
                soundId, volume, volume, "shoot".equals(name) ? 0 : 1, 0, 1f);
        nextStream = (nextStream + 1) % streams.length;
    }

    void stop() {
        active = false;
        stopStreams();
    }

    private void stopStreams() {
        if (released) return;
        for (int i = 0; i < streams.length; i++) {
            if (streams[i] != 0) pool.stop(streams[i]);
            streams[i] = 0;
        }
    }

    void release() {
        if (released) return;
        stop();
        released = true;
        pool.release();
        loaded.clear();
        sounds.clear();
    }

    private static void writeWave(File file, String name) throws IOException {
        double duration;
        switch (name) {
            case "shoot": duration = 0.055; break;
            case "kill": duration = 0.105; break;
            case "hit": duration = 0.18; break;
            case "upgrade": duration = 0.32; break;
            case "pulse": duration = 0.42; break;
            case "win": duration = 0.76; break;
            case "lose": duration = 0.55; break;
            default: duration = 0.055;
        }
        int sampleCount = (int) (duration * SAMPLE_RATE);
        ByteBuffer output = ByteBuffer.allocate(44 + sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN);
        output.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + sampleCount * 2);
        output.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        output.putInt(16).putShort((short) 1).putShort((short) 1);
        output.putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2).putShort((short) 2).putShort((short) 16);
        output.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(sampleCount * 2);
        double phase = 0;
        long noiseState = name.hashCode();
        for (int i = 0; i < sampleCount; i++) {
            double t = (double) i / SAMPLE_RATE;
            double progress = t / duration;
            double frequency;
            double noiseAmount = 0;
            switch (name) {
                case "shoot": frequency = 1500 - 1000 * progress; break;
                case "kill": frequency = 660 - 370 * progress; noiseAmount = 0.12; break;
                case "hit": frequency = 150 - 70 * progress; noiseAmount = 0.3; break;
                case "upgrade":
                    frequency = UPGRADE_NOTES[Math.min(3, (int) (progress * 4))];
                    break;
                case "pulse": frequency = 210 * Math.pow(0.22, progress); noiseAmount = 0.1; break;
                case "win":
                    frequency = WIN_NOTES[Math.min(4, (int) (progress * 5))];
                    break;
                case "lose": frequency = 320 * Math.pow(0.28, progress); break;
                default: frequency = 880;
            }
            phase += 2 * Math.PI * frequency / SAMPLE_RATE;
            double attack = Math.min(1, t / 0.005);
            double envelope = attack * Math.pow(1 - progress, "win".equals(name) ? 0.7 : 1.5);
            noiseState = noiseState * 1664525L + 1013904223L;
            double noise = ((noiseState >>> 8) & 65535) / 32767.5 - 1;
            double tone = Math.sin(phase) * 0.78 + Math.sin(phase * 2) * 0.15;
            double sample = envelope * (tone * (1 - noiseAmount) + noise * noiseAmount);
            output.putShort((short) (Math.max(-1, Math.min(1, sample)) * 23000));
        }
        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(output.array());
        }
    }
}
