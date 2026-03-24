package org.astral.beatscene.audio;

import org.astral.beatscene.Main;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

public final class AudioInput {
    public static final int FFT_SIZE = 2048;
    public static final int NUM_BARS = 128; // ¡Aumentado a 128 barras para más detalle!

    public static class AudioData {
        public List<float[]> frames = new ArrayList<>();
        public ShortBuffer pcmData;
        public int sampleRate;
        public int channels;
    }

    private static AudioData currentAudioData = null;

    public static void prepareAudio() {
        try {
            InputStream is = Main.class.getResourceAsStream("/Common/Sounds/Beat/miss.ogg");
            if (is == null) {
                System.out.println("[BeatScene] ERROR: Archivo perfect.ogg no encontrado.");
                return;
            }

            byte[] oggBytes = is.readAllBytes();
            ByteBuffer oggBuffer = BufferUtils.createByteBuffer(oggBytes.length);
            oggBuffer.put(oggBytes).flip();

            currentAudioData = new AudioData();

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer error = stack.mallocInt(1);
                long decoder = STBVorbis.stb_vorbis_open_memory(oggBuffer, error, null);
                if (decoder == 0) throw new RuntimeException("Error STBVorbis: " + error.get(0));

                STBVorbisInfo info = STBVorbisInfo.malloc(stack);
                STBVorbis.stb_vorbis_get_info(decoder, info);

                currentAudioData.sampleRate = info.sample_rate();
                currentAudioData.channels = info.channels();

                int samplesTotal = STBVorbis.stb_vorbis_stream_length_in_samples(decoder) * currentAudioData.channels;
                currentAudioData.pcmData = BufferUtils.createShortBuffer(samplesTotal);

                STBVorbis.stb_vorbis_get_samples_short_interleaved(decoder, currentAudioData.channels, currentAudioData.pcmData);
                STBVorbis.stb_vorbis_close(decoder);

                analyzeFrames(currentAudioData);
                System.out.println("[BeatScene] Análisis de audio EQ optimizado completado.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void analyzeFrames(AudioData data) {
        int step = FFT_SIZE / 2;
        int limit = data.pcmData.capacity() - (FFT_SIZE * data.channels);

        for (int i = 0; i <= limit; i += (step * data.channels)) {
            float[] samples = new float[FFT_SIZE];
            for (int j = 0; j < FFT_SIZE; j++) {
                int baseIdx = i + (j * data.channels);

                if (data.channels == 2) {
                    samples[j] = (data.pcmData.get(baseIdx) + data.pcmData.get(baseIdx + 1)) / 65536.0f;
                } else {
                    samples[j] = data.pcmData.get(baseIdx) / 32768.0f;
                }
                // Ventana de Hann para suavizar bordes de la señal
                samples[j] *= (float) (0.5 * (1.0 - Math.cos(2 * Math.PI * j / (FFT_SIZE - 1))));
            }
            data.frames.add(computeFFT(samples));
        }
    }

    private static float[] computeFFT(float[] input) {
        int n = input.length;
        float[] real = new float[n];
        float[] imag = new float[n];
        System.arraycopy(input, 0, real, 0, n);

        for (int i = 0, j = 0; i < n; i++) {
            if (i < j) { float t = real[i]; real[i] = real[j]; real[j] = t; }
            int m = n >> 1;
            while (m >= 1 && j >= m) { j -= m; m >>= 1; }
            j += m;
        }

        for (int k = 1; k < n; k <<= 1) {
            float angle = (float) (-Math.PI / k);
            for (int i = 0; i < n; i += (k << 1)) {
                for (int j = 0; j < k; j++) {
                    float c = (float) Math.cos(angle * j), s = (float) Math.sin(angle * j);
                    float tr = c * real[i + j + k] - s * imag[i + j + k];
                    float ti = s * real[i + j + k] + c * imag[i + j + k];
                    real[i + j + k] = real[i + j] - tr;
                    imag[i + j + k] = imag[i + j] - ti;
                    real[i + j] += tr;
                    imag[i + j] += ti;
                }
            }
        }

        float[] magnitudes = new float[n / 2];
        for (int i = 0; i < n / 2; i++) {
            magnitudes[i] = (float) Math.sqrt(real[i] * real[i] + imag[i] * imag[i]) / (n / 2.0f);
        }

        float[] barData = new float[NUM_BARS];

        // Mapeo logarítmico a las barras (como en C++)
        for (int i = 0; i < NUM_BARS; i++) {
            // Escala logarítmica real para las frecuencias
            float startFreq = (float) Math.pow(10, Math.log10(1) + (i / (float)NUM_BARS) * Math.log10(512));
            float endFreq = (float) Math.pow(10, Math.log10(1) + ((i + 1) / (float)NUM_BARS) * Math.log10(512));

            int start = (int) startFreq;
            int end = (int) endFreq;
            if (end <= start) end = start + 1;

            float maxMag = 0;
            for (int j = start; j < end && j < magnitudes.length; j++) {
                if (magnitudes[j] > maxMag) maxMag = magnitudes[j];
            }

            // REFUERZO DE AGUDOS (C++ Logic): Frecuencias más altas necesitan más volumen visual
            float boost = 0.5f + (i / (float)NUM_BARS) * 4.0f;

            // Limitamos a un máximo de 1.0 para que no se salga de la pantalla
            barData[i] = Math.min(1.0f, maxMag * boost * 25.0f);
        }
        return barData;
    }

    public static AudioData getAudioData() {
        return currentAudioData;
    }
}