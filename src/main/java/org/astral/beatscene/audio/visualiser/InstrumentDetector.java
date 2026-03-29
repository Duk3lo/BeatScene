package org.astral.beatscene.audio.visualiser;

import org.astral.beatscene.audio.AudioInput;
import org.lwjgl.openal.*;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.AL11.AL_SEC_OFFSET;

public class InstrumentDetector {

    private JFrame frame;
    private DetectorPanel panel;

    private int sourcePointer;
    private int bufferPointer;
    private boolean isPlaying = false;
    private AudioInput.AudioData audioData;

    public void open(AudioInput.AudioData data) {
        this.audioData = data;

        SwingUtilities.invokeLater(() -> {
            frame = new JFrame("🎧 Phonk & Glitchcore - MINIM & AUBIO ENGINE");
            frame.setSize(1500, 450);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.getContentPane().setBackground(Color.BLACK);

            panel = new DetectorPanel();
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

        new Timer(16, _ -> {
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

    // =========================================================================
    // EL MOTOR "MINIM / AUBIO" TRADUCIDO A JAVA PURO
    // =========================================================================
    private static class InstrumentBand {
        String name;
        Color color;
        int minIndex, maxIndex;
        float sensitivity;
        float minVolume;
        boolean isSustained;

        // --- Algoritmo de Historia de Minim ---
        float[] energyHistory = new float[43]; // Guarda ~1 segundo de audio a 43fps
        int historyIdx = 0;
        int cooldown = 0; // Evita "tartamudeos" de detección (Multitriggering)

        float scale = 1.0f;
        boolean isActive = false;
        static Random rnd = new Random();

        public InstrumentBand(String name, Color color, int minIdx, int maxIdx, float sensitivity, float minVolume, boolean isSustained) {
            this.name = name;
            this.color = color;
            this.minIndex = minIdx;
            this.maxIndex = maxIdx;
            this.sensitivity = sensitivity;
            this.minVolume = minVolume;
            this.isSustained = isSustained;
        }

        public void update(float[] fft) {
            // 1. Obtener energía instantánea de esta banda
            float currentEnergy = 0;
            int count = (maxIndex - minIndex) + 1;
            for (int i = minIndex; i <= maxIndex; i++) {
                currentEnergy += fft[i];
            }
            currentEnergy /= count;

            // 2. Calcular promedio del buffer de historia (Algoritmo "Local Energy" de Minim)
            float averageEnergy = 0;
            for (float e : energyHistory) {
                averageEnergy += e;
            }
            averageEnergy /= energyHistory.length;

            // 3. Spectral Flux (Aubio Algorithm): Medir el impacto repentino
            int prevIdx = (historyIdx - 1 + energyHistory.length) % energyHistory.length;
            float flux = Math.max(0, currentEnergy - energyHistory[prevIdx]);

            // 4. Lógica de Detección
            if (isSustained) {
                // Sonidos Largos (Ópera, Sintetizadores)
                if (currentEnergy > minVolume && currentEnergy > averageEnergy * 0.8f) {
                    isActive = true;
                    float targetScale = 1.2f + (currentEnergy * 2.5f);
                    scale += (targetScale - scale) * 0.2f;
                } else {
                    isActive = false;
                    scale += (1.0f - scale) * 0.1f;
                }
            } else {
                // Percusiones (Bombos, Cajas, Platillos)
                // Minim dice: ¿Es la energía actual MAYOR que el (Promedio * Sensibilidad)?
                // Aubio dice: ¿Hubo un Flux repentino?
                boolean beatCondition = (currentEnergy > averageEnergy * sensitivity) && (flux > minVolume);

                if (beatCondition && cooldown == 0) {
                    isActive = true;
                    scale = 2.2f; // Explosión
                    cooldown = 10; // Bloquea la detección por 10 frames (~200ms) para evitar dobles golpes
                } else {
                    isActive = false;
                    scale += (1.0f - scale) * 0.15f;
                }
            }

            // Reducir Cooldown
            if (cooldown > 0) cooldown--;

            // Guardar en la historia para el futuro
            energyHistory[historyIdx] = currentEnergy;
            historyIdx = (historyIdx + 1) % energyHistory.length;
        }

        public void draw(Graphics2D g2d, int cx, int cy) {
            float baseRadius = 40;
            float currentRadius = baseRadius * scale;

            if (isActive) {
                for(int i = 0; i < 3; i++) {
                    int alpha = 100 - (i * 30);
                    g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
                    float glowRadius = currentRadius + (i * 15);
                    g2d.fill(new Ellipse2D.Float(cx - glowRadius, cy - glowRadius, glowRadius * 2, glowRadius * 2));
                }
                g2d.setColor(color);
                g2d.fill(new Ellipse2D.Float(cx - currentRadius, cy - currentRadius, currentRadius * 2, currentRadius * 2));
            } else {
                g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 60));
                g2d.setStroke(new BasicStroke(2));
                g2d.draw(new Ellipse2D.Float(cx - currentRadius, cy - currentRadius, currentRadius * 2, currentRadius * 2));
            }

            int textXOffset = 0, textYOffset = 0;
            if (isActive && !isSustained) {
                textXOffset = (rnd.nextInt(5) - 2);
                textYOffset = (rnd.nextInt(5) - 2);
            }

            g2d.setColor(isActive ? Color.WHITE : Color.LIGHT_GRAY);
            g2d.setFont(new Font("Monospaced", Font.BOLD, 15));
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(name);
            g2d.drawString(name, cx - ((float) textWidth / 2) + textXOffset, cy + (baseRadius * 2.5f) + 20 + textYOffset);
        }
    }

    private class DetectorPanel extends JPanel {
        private final List<InstrumentBand> instruments = new ArrayList<>();

        public DetectorPanel() {
            setDoubleBuffered(true);
            setBackground(Color.BLACK);

            // IMPORTANTE: Al usar el algoritmo de Minim, la Sensibilidad funciona diferente.
            // Una sensibilidad de 1.5f significa: "Golpea si suena un 50% más fuerte que el promedio del último segundo".

            // 1. PHONK 808s
            instruments.add(new InstrumentBand("PHONK 808", new Color(255, 0, 50), 0, 2, 1.4f, 0.2f, false));

            // 2. KICK DRUM
            instruments.add(new InstrumentBand("BREAK KICK", new Color(255, 100, 0), 3, 7, 1.5f, 0.15f, false));

            // 3. SNARE CHOP
            instruments.add(new InstrumentBand("SNARE CHOP", Color.YELLOW, 12, 22, 1.6f, 0.1f, false));

            // 4. COWBELL
            instruments.add(new InstrumentBand("COWBELL", new Color(200, 0, 255), 30, 42, 1.6f, 0.08f, false));

            // 5. MELODY VOX (Sostenido)
            instruments.add(new InstrumentBand("MELODY VOX", new Color(255, 50, 150), 45, 60, 1.0f, 0.05f, true));

            // 6. ANGELIC CHOIR (Sostenido)
            instruments.add(new InstrumentBand("ANGELIC CHOIR", new Color(0, 255, 255), 65, 85, 1.0f, 0.04f, true));

            // 7. GLITCH HATS
            instruments.add(new InstrumentBand("GLITCH HATS", new Color(50, 255, 50), 95, 127, 1.7f, 0.02f, false));
        }

        public void updateVisuals() {
            float offsetSeconds = alGetSourcef(sourcePointer, AL_SEC_OFFSET);
            int frameIdx = (int) (offsetSeconds * (audioData.sampleRate / (AudioInput.FFT_SIZE / 2.0f)));

            if (frameIdx >= 0 && frameIdx < audioData.frames.size()) {
                float[] fft = audioData.frames.get(frameIdx);
                for (InstrumentBand band : instruments) {
                    band.update(fft);
                }
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();

            g2d.setColor(new Color(0, 0, 0, 80));
            g2d.fillRect(0, 0, width, height);

            int cy = height / 2;
            int numInstruments = instruments.size();
            int sectionWidth = width / numInstruments;

            for (int i = 0; i < numInstruments; i++) {
                int cx = (i * sectionWidth) + (sectionWidth / 2);
                instruments.get(i).draw(g2d, cx, cy);
            }
        }
    }
}