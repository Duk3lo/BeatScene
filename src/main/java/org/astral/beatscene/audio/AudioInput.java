package org.astral.beatscene.audio;

import org.astral.beatscene.Main;
import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class AudioInput {
    private static final List<float[]> frames = new ArrayList<>();
    private static final List<Float> bassEnergies = new ArrayList<>();
    public static final int NUM_BARS = 64;
    private static final int FFT_SIZE = 2048; // Mayor resolución para bajos definidos

    public static void prepareAudio() {
        frames.clear();
        bassEnergies.clear();
        try {
            InputStream is = Main.class.getResourceAsStream("/Common/Sounds/Beat/perfect.ogg");
            if (is == null) return;

            // Forzamos la decodificación a PCM a través de un stream intermedio
            AudioInputStream rawOgg = AudioSystem.getAudioInputStream(new BufferedInputStream(is));
            AudioFormat baseFormat = rawOgg.getFormat();
            AudioFormat decodedFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    44100, 16, 1, 2, 44100, false // Convertimos a MONO para análisis más rápido
            );

            AudioInputStream pcmStream = AudioSystem.getAudioInputStream(decodedFormat, rawOgg);

            byte[] buffer = new byte[FFT_SIZE * 2]; // 16-bit = 2 bytes por sample
            float[] previousFrame = new float[NUM_BARS];

            while (pcmStream.read(buffer) != -1) {
                float[] samples = new float[FFT_SIZE];
                for (int i = 0; i < FFT_SIZE; i++) {
                    // Convertir 2 bytes a un float de -1.0 a 1.0
                    int sample = (buffer[i * 2 + 1] << 8) | (buffer[i * 2] & 0xFF);
                    // Ventana de Hamming para evitar fugas espectrales
                    float window = (float) (0.54 - 0.46 * Math.cos(2 * Math.PI * i / (FFT_SIZE - 1)));
                    samples[i] = (sample / 32768.0f) * window;
                }

                float[] magnitudes = computeFFT(samples);
                float[] frameBars = new float[NUM_BARS];

                // Energía de bajos (Primeras 8 bins) para el radio del círculo
                float bass = 0;
                for (int i = 0; i < 8; i++) bass += magnitudes[i];
                bassEnergies.add(bass * 2.0f);

                // Mapeo Logarítmico: Las frecuencias bajas ocupan más espacio visual
                for (int i = 0; i < NUM_BARS; i++) {
                    int idx = (int) Math.pow(i, 1.8) / 5; // Curva de distribución
                    if (idx >= magnitudes.length) idx = magnitudes.length - 1;

                    float val = magnitudes[idx] * 350.0f; // Sensibilidad

                    // Suavizado de caída (Gravity/Decay)
                    if (val < previousFrame[i]) {
                        val = previousFrame[i] * 0.82f;
                    }
                    frameBars[i] = val;
                    previousFrame[i] = val;
                }
                frames.add(frameBars);
            }
            pcmStream.close();
        } catch (Exception e) {
            System.out.println("[BeatScene] Error crítico en análisis: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static float[] computeFFT(float[] input) {
        int n = input.length;
        float[] real = new float[n];
        float[] imag = new float[n];
        System.arraycopy(input, 0, real, 0, n);

        // Algoritmo Cooley-Tukey (Bit-reversal)
        for (int i = 0, j = 0; i < n; i++) {
            if (i < j) { float t = real[i]; real[i] = real[j]; real[j] = t; }
            int m = n >> 1;
            while (m >= 1 && j >= m) { j -= m; m >>= 1; }
            j += m;
        }
        // Mariposas FFT
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
        float[] mag = new float[n / 2];
        for (int i = 0; i < n / 2; i++) {
            mag[i] = (float) Math.sqrt(real[i] * real[i] + imag[i] * imag[i]);
        }
        return mag;
    }

    public static List<float[]> getFrames() { return frames; }
    public static List<Float> getBassEnergies() { return bassEnergies; }
    public static int getChunkTimeMs() { return 23; } // Sincronizado con FFT_SIZE 2048
}