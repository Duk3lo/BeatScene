package org.astral.beatscene.audio.visualiser;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Iterator;
import org.astral.beatscene.Main;
import org.astral.beatscene.audio.AudioInput;

public class Terminal {

    private JFrame frame;
    private SpectrumPanel spectrumPanel;
    private Clip audioClip;

    public void open(List<float[]> fftFrames) {
        SwingUtilities.invokeLater(() -> {
            try {
                frame = new JFrame("🎵 BeatScene Ultra Visualizer - Pro Smooth");
                frame.setSize(1000, 600);
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame.getContentPane().setBackground(Color.BLACK);

                spectrumPanel = new SpectrumPanel();
                frame.add(spectrumPanel);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);

                startLocalAudio(fftFrames);
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    private void startLocalAudio(List<float[]> fftFrames) {
        ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(Main.class.getClassLoader());
            InputStream is = Main.class.getResourceAsStream("/Common/Sounds/Beat/perfect.ogg");
            if (is == null) return;

            AudioInputStream oggStream = AudioSystem.getAudioInputStream(new BufferedInputStream(is));
            AudioFormat baseFormat = oggStream.getFormat();
            AudioFormat decodedFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED, baseFormat.getSampleRate(),
                    16, baseFormat.getChannels(), baseFormat.getChannels() * 2,
                    baseFormat.getSampleRate(), false
            );

            AudioInputStream pcmStream = AudioSystem.getAudioInputStream(decodedFormat, oggStream);
            audioClip = AudioSystem.getClip();
            audioClip.open(pcmStream);
            audioClip.start();

            new Thread(() -> {
                try {
                    while (audioClip != null && audioClip.isOpen()) {
                        if (audioClip.isRunning()) {
                            long currentMs = audioClip.getMicrosecondPosition() / 1000;
                            int frameIndex = (int) (currentMs / AudioInput.getChunkTimeMs());
                            if (frameIndex < fftFrames.size()) {
                                spectrumPanel.updateData(fftFrames.get(frameIndex));
                            }
                        }
                        Thread.sleep(10);
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }).start();
        } catch (Exception e) {
            Main.getInstance().getLogger().atSevere().log("Error en audio: " + e.getMessage());
        } finally {
            Thread.currentThread().setContextClassLoader(oldLoader);
        }
    }

    public void close() {
        if (audioClip != null) { audioClip.stop(); audioClip.close(); }
        if (frame != null) frame.dispose();
    }

    private static class SpectrumPanel extends JPanel {
        private float[] amplitudes = new float[AudioInput.NUM_BARS];
        private final List<Particle> particles = new ArrayList<>();
        private final List<ShrinkingCircle> shrinkingCircles = new ArrayList<>();
        private final Random rnd = new Random();
        private float rotation = 0;
        private float lerpRadius = 80; // Radio suave inicial

        private Color beatColor = Color.CYAN;
        private boolean isBeat = false;
        private float lastEnergy = 0;
        private float smoothedEnergy = 0;

        public SpectrumPanel() {
            setBackground(Color.BLACK);
            setDoubleBuffered(true);
            for (int i = 0; i < 200; i++) {
                particles.add(new Particle(rnd.nextInt(1000), rnd.nextInt(600)));
            }
            new Timer(16, e -> {
                rotation += 0.005f;
                repaint();
            }).start();
        }

        public void updateData(float[] newAmps) {
            this.amplitudes = newAmps;
            detectBeat();
        }

        private void detectBeat() {
            float currentEnergy = getAverageEnergy();
            smoothedEnergy = smoothedEnergy * 0.7f + currentEnergy * 0.3f;

            // Detección dinámica basada en la energía suavizada
            if (currentEnergy > lastEnergy + 0.3f && currentEnergy > 0.5f) {
                isBeat = true;
                beatColor = Color.getHSBColor(rnd.nextFloat(), 0.7f, 1.0f);
                if (shrinkingCircles.size() < 4) {
                    shrinkingCircles.add(new ShrinkingCircle(600));
                }
            } else {
                isBeat = false;
            }
            lastEnergy = currentEnergy;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Efecto rastro (Motion Blur)
            g2d.setColor(new Color(0, 0, 0, 80));
            g2d.fillRect(0, 0, getWidth(), getHeight());

            int w = getWidth();
            int h = getHeight();

            float visualEnergy = Math.min(smoothedEnergy, 1.5f);

            // LERP para el radio: el secreto de la fluidez
            float targetRadius = 70 + (visualEnergy * 60);
            lerpRadius = lerpRadius * 0.75f + targetRadius * 0.25f;

            drawShrinkingCircles(g2d, w, h);
            drawDarkCircle(g2d, lerpRadius, visualEnergy, w, h);
            drawParticles(g2d, visualEnergy, w, h);
        }

        private void drawShrinkingCircles(Graphics2D g2d, int w, int h) {
            g2d.setStroke(new BasicStroke(2.0f));
            Iterator<ShrinkingCircle> it = shrinkingCircles.iterator();
            while (it.hasNext()) {
                ShrinkingCircle sc = it.next();
                sc.update();
                if (sc.radius < 20) { it.remove(); continue; }
                int alpha = (int)(Math.min(1.0f, sc.radius / 600f) * 150);
                g2d.setColor(new Color(beatColor.getRed(), beatColor.getGreen(), beatColor.getBlue(), alpha));
                g2d.draw(new Ellipse2D.Float(w/2 - sc.radius, h/2 - sc.radius, sc.radius*2, sc.radius*2));
            }
        }

        private void drawParticles(Graphics2D g2d, float energy, int w, int h) {
            for (Particle p : particles) {
                p.update(energy, w, h);
                int alpha = (int)(p.alpha * 200);
                if (isBeat) {
                    g2d.setColor(Color.WHITE);
                } else {
                    g2d.setColor(new Color(100, 180, 255, alpha));
                }
                g2d.fill(new Ellipse2D.Float(p.x, p.y, p.size, p.size));
            }
        }

        private void drawDarkCircle(Graphics2D g2d, float radius, float energy, int w, int h) {
            int centerX = w / 2;
            int centerY = h / 2;

            // Glow exterior reactivo
            for (int i = 0; i < 5; i++) {
                int alpha = (int) (Math.min(energy * 30, 50) / (i + 1));
                g2d.setColor(new Color(beatColor.getRed(), beatColor.getGreen(), beatColor.getBlue(), alpha));
                float glowR = radius + (i * 15);
                g2d.fill(new Ellipse2D.Float(centerX - glowR, centerY - glowR, glowR * 2, glowR * 2));
            }

            // Barras de espectro circulares
            g2d.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < amplitudes.length; i++) {
                double angle = Math.toRadians((i * (360.0 / amplitudes.length)) + (rotation * 80));

                // Multiplicador balanceado
                float val = amplitudes[i] * 2.8f;

                float x1 = (float) (centerX + Math.cos(angle) * radius);
                float y1 = (float) (centerY + Math.sin(angle) * radius);
                float x2 = (float) (centerX + Math.cos(angle) * (radius + val));
                float y2 = (float) (centerY + Math.sin(angle) * (radius + val));

                g2d.setColor(Color.getHSBColor(0.55f + (i / (float)amplitudes.length) * 0.2f, 0.8f, 1.0f));
                g2d.draw(new Line2D.Float(x1, y1, x2, y2));
            }

            // Núcleo central negro
            g2d.setColor(Color.BLACK);
            g2d.fill(new Ellipse2D.Float(centerX - radius + 2, centerY - radius + 2, (radius - 2) * 2, (radius - 2) * 2));
        }

        private float getAverageEnergy() {
            float sum = 0;
            // Enfocado en bajos para el ritmo del círculo
            for (int i = 0; i < 5; i++) sum += amplitudes[i];
            return sum / 5;
        }
    }

    private static class ShrinkingCircle {
        float radius;
        ShrinkingCircle(float r) { this.radius = r; }
        void update() { this.radius -= 12; }
    }

    private static class Particle {
        float x, y, xv, yv, size, alpha;
        Random r = new Random();

        Particle(float x, float y) {
            this.x = x; this.y = y;
            this.xv = (r.nextFloat() - 0.5f) * 1.5f;
            this.yv = (r.nextFloat() - 0.5f) * 1.5f;
            this.size = r.nextFloat() * 2 + 1;
            this.alpha = r.nextFloat();
        }

        void update(float energy, int w, int h) {
            float speedMult = 1 + (energy * 5);
            x += xv * speedMult;
            y += yv * speedMult;
            if (x < 0) x = w; if (x > w) x = 0;
            if (y < 0) y = h; if (y > h) y = 0;
        }
    }
}