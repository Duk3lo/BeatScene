package org.astral.beatscene.audio.visualiser;

import org.astral.beatscene.audio.AudioInput;
import org.lwjgl.openal.*;

import javax.swing.*;
import java.awt.*;
import java.nio.ByteBuffer;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.AL11.AL_SEC_OFFSET;

public class SimpleVisualizer {

    private JFrame frame;
    private SimplePanel panel;

    private int sourcePointer;
    private int bufferPointer;
    private boolean isPlaying = false;
    private AudioInput.AudioData audioData;

    public void open(AudioInput.AudioData data) {
        this.audioData = data;

        SwingUtilities.invokeLater(() -> {
            frame = new JFrame("📊 Visualizador Pro - Smooth Rhythm");
            frame.setSize(1000, 600);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

            panel = new SimplePanel();
            frame.add(panel);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            initOpenAL();
            startPlayback();
        });
    }

    private void initOpenAL() {
        long device = ALC10.alcOpenDevice((ByteBuffer) null);
        ALCCapabilities deviceCaps = ALC.createCapabilities(device);
        long context = ALC10.alcCreateContext(device, (int[]) null);
        ALC10.alcMakeContextCurrent(context);
        AL.createCapabilities(deviceCaps);
    }

    private void startPlayback() {
        bufferPointer = alGenBuffers();
        int format = (audioData.channels == 1) ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16;
        alBufferData(bufferPointer, format, audioData.pcmData, audioData.sampleRate);
        sourcePointer = alGenSources();
        alSourcei(sourcePointer, AL_BUFFER, bufferPointer);
        alSourcePlay(sourcePointer);
        isPlaying = true;

        new Timer(16, e -> {
            if (isPlaying) {
                panel.updateVisuals();
                panel.repaint();
                if (alGetSourcei(sourcePointer, AL_SOURCE_STATE) != AL_PLAYING) isPlaying = false;
            }
        }).start();
    }

    public void close() {
        isPlaying = false;
        alSourceStop(sourcePointer);
        alDeleteSources(sourcePointer);
        alDeleteBuffers(bufferPointer);
        if (frame != null) frame.dispose();
    }

    // --- PANEL DONDE SE DIBUJA EL ESPECTRO ---
    private class SimplePanel extends JPanel {

        private final float[] visualBars = new float[AudioInput.NUM_BARS];
        private final float[] peakBars = new float[AudioInput.NUM_BARS];

        private float shakeY = 0;

        public SimplePanel() {
            setDoubleBuffered(true);
            setBackground(Color.BLACK);
        }

        public void updateVisuals() {
            float offsetSeconds = alGetSourcef(sourcePointer, AL_SEC_OFFSET);
            int frameIdx = (int) (offsetSeconds * (audioData.sampleRate / (AudioInput.FFT_SIZE / 2.0f)));

            if (frameIdx >= 0 && frameIdx < audioData.frames.size()) {
                float[] currentFFT = audioData.frames.get(frameIdx);

                // Calcular la energía del bajo para el salto de pantalla
                float bassEnergy = 0;
                for (int i = 0; i < 6; i++) {
                    bassEnergy += currentFFT[i];
                }
                bassEnergy /= 6.0f;

                // --- 1. SUAVIZADO DEL SHAKE (SALTO) ---
                // En lugar de saltar violento, se desliza hacia la posición del bajo
                float targetShake = bassEnergy * 15.0f;
                shakeY += (targetShake - shakeY) * 0.3f; // 0.3f es la suavidad del rebote

                // --- 2. LÓGICA DE BARRAS SÚPER SUAVES ---
                for (int i = 0; i < AudioInput.NUM_BARS; i++) {
                    float target = currentFFT[i];

                    if (target > visualBars[i]) {
                        // SUBIDA SUAVE: En lugar de igualar el valor de golpe, avanzamos un 35% hacia el objetivo
                        visualBars[i] += (target - visualBars[i]) * 0.35f;
                    } else {
                        // BAJADA FLUIDA: Cuando cae, lo hace aún más lento (12% por frame)
                        visualBars[i] += (target - visualBars[i]) * 0.12f;
                    }

                    // --- 3. PICOS BLANCOS FLOTANTES ---
                    // El pico ahora es "empujado" por la barra suave, no por el audio violento
                    if (visualBars[i] > peakBars[i]) {
                        peakBars[i] = visualBars[i];
                    } else {
                        // Caen muy lentamente
                        peakBars[i] -= 0.005f;
                        if (peakBars[i] < 0) peakBars[i] = 0;
                    }
                }
            } else {
                // Si se acaba el audio, la pantalla regresa a su posición suavemente
                shakeY *= 0.8f;
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;

            // Antialiasing activado para bordes perfectos
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();

            float barWidth = (float) width / AudioInput.NUM_BARS;
            int offsetY = (int) shakeY;

            for (int i = 0; i < AudioInput.NUM_BARS; i++) {

                int barHeight = (int) (visualBars[i] * (height * 0.7f));
                int peakHeight = (int) (peakBars[i] * (height * 0.7f));

                int x = (int) (i * barWidth);
                int y = height - barHeight + offsetY;

                // Generar degradado de color
                float hue = (i / (float)AudioInput.NUM_BARS) * 0.8f;
                g2d.setColor(Color.getHSBColor(hue, 0.9f, 1.0f));

                // Barra principal redondeada
                g2d.fillRoundRect(x + 1, y, (int) barWidth - 2, barHeight, 5, 5);

                // Pico superior flotante
                int peakY = height - peakHeight + offsetY;
                g2d.setColor(new Color(255, 255, 255, 200)); // Blanco un poco transparente
                g2d.fillRect(x + 1, peakY - 3, (int) barWidth - 2, 3);
            }
        }
    }
}