package org.astral.beatscene.audio.visualiser;

import org.astral.beatscene.audio.AudioInput;
import org.lwjgl.openal.*;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.AL11.AL_SEC_OFFSET;

public class Terminal {

    private JFrame frame;
    private VisualizerPanel panel;

    private int sourcePointer;
    private int bufferPointer;
    private boolean isPlaying = false;

    // Usamos el AudioData de STBVorbis
    private AudioInput.AudioData audioData;

    public void open(AudioInput.AudioData data) {
        this.audioData = data;

        SwingUtilities.invokeLater(() -> {
            frame = new JFrame("🎵 BeatScene Ultra Visualizer - STBVorbis Precision");
            frame.setSize(1280, 720);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.getContentPane().setBackground(Color.BLACK);

            panel = new VisualizerPanel();
            frame.add(panel);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            initOpenAL();
            startPlayback();
        });
    }

    private void initOpenAL() {
        try {
            long device = ALC10.alcOpenDevice((ByteBuffer) null);
            ALCCapabilities deviceCaps = ALC.createCapabilities(device);
            long context = ALC10.alcCreateContext(device, (int[]) null);
            ALC10.alcMakeContextCurrent(context);
            AL.createCapabilities(deviceCaps);
        } catch (Exception e) {
            e.printStackTrace();
        }
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

                if (alGetSourcei(sourcePointer, AL_SOURCE_STATE) != AL_PLAYING) {
                    isPlaying = false;
                }
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

    private class VisualizerPanel extends JPanel {
        private final List<Particle> particles = new ArrayList<>();
        private final List<ShrinkingCircle> shrinkingCircles = new ArrayList<>();
        private final Random rnd = new Random();

        private final float[] visualBars = new float[AudioInput.NUM_BARS];
        private float rotation = 0;
        private Color beatColor = Color.CYAN;
        private float lastEnergy = 0, smoothedEnergy = 0, lerpEnergy = 0;
        private float shakeX = 0, shakeY = 0;
        private int spawnCooldown = 0;

        public VisualizerPanel() {
            setDoubleBuffered(true);
            setBackground(Color.BLACK);
            for (int i = 0; i < 150; i++) {
                particles.add(new Particle(rnd.nextInt(1280), rnd.nextInt(720)));
            }
        }

        public void updateVisuals() {
            float offsetSeconds = alGetSourcef(sourcePointer, AL_SEC_OFFSET);
            int frameIdx = (int) (offsetSeconds * (audioData.sampleRate / (AudioInput.FFT_SIZE / 2.0f)));

            if (frameIdx >= 0 && frameIdx < audioData.frames.size()) {
                float[] fft = audioData.frames.get(frameIdx);

                float currentEnergy = 0;
                for (int i = 0; i < 6; i++) currentEnergy += fft[i];
                currentEnergy /= 6.0f;

                smoothedEnergy = smoothedEnergy * 0.85f + currentEnergy * 0.15f;
                lerpEnergy = (currentEnergy > lerpEnergy) ? currentEnergy : lerpEnergy * 0.92f;

                if (currentEnergy > lastEnergy + 0.12f && currentEnergy > 0.4f && spawnCooldown <= 0) {
                    beatColor = Color.getHSBColor(rnd.nextFloat(), 0.7f, 1.0f);
                    shakeX = (rnd.nextFloat() - 0.5f) * 60 * currentEnergy;
                    shakeY = (rnd.nextFloat() - 0.5f) * 60 * currentEnergy;
                    shrinkingCircles.add(new ShrinkingCircle(1000, currentEnergy, beatColor));
                    spawnCooldown = 20;
                }
                lastEnergy = currentEnergy;
                if (spawnCooldown > 0) spawnCooldown--;

                for (int i = 0; i < visualBars.length; i++) {
                    visualBars[i] = visualBars[i] * 0.7f + fft[i] * 0.3f;
                }
            }

            shakeX *= 0.8f; shakeY *= 0.8f;
            rotation += 0.005f + (smoothedEnergy * 0.05f);

            Iterator<ShrinkingCircle> it = shrinkingCircles.iterator();
            while (it.hasNext()) {
                ShrinkingCircle sc = it.next();
                sc.update(smoothedEnergy);
                if (sc.radius <= 0) it.remove();
            }

            for (Particle p : particles) p.update(smoothedEnergy, getWidth(), getHeight(), beatColor);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2d.setColor(new Color(0, 0, 0, 80));
            g2d.fillRect(0, 0, getWidth(), getHeight());

            int cx = (int)((float) getWidth() / 2 + shakeX);
            int cy = (int)((float) getHeight() / 2 + shakeY);
            float currentRadius = 110 + (lerpEnergy * 160);

            for (ShrinkingCircle sc : shrinkingCircles) {
                float stroke = 2f + (sc.originalEnergy * 10f) + (smoothedEnergy * 15f);
                g2d.setStroke(new BasicStroke(stroke));
                float progress = sc.radius / 1000f;
                int alpha = (int)((1.0f - progress) * 200) + 55;
                g2d.setColor(new Color(sc.color.getRed(), sc.color.getGreen(), sc.color.getBlue(), Math.max(0, Math.min(255, alpha))));
                g2d.draw(new Ellipse2D.Float(cx - sc.radius, cy - sc.radius, sc.radius * 2, sc.radius * 2));
            }

            for (Particle p : particles) {
                g2d.setColor(p.color);
                g2d.fill(new Ellipse2D.Float(p.x, p.y, p.size, p.size));
            }

            g2d.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < visualBars.length; i++) {
                double angle = Math.toRadians((i * (360.0 / visualBars.length)) + (rotation * 60));
                float amp = visualBars[i] * 320;
                float x1 = (float) (cx + Math.cos(angle) * currentRadius);
                float y1 = (float) (cy + Math.sin(angle) * currentRadius);
                float x2 = (float) (cx + Math.cos(angle) * (currentRadius + amp));
                float y2 = (float) (cy + Math.sin(angle) * (currentRadius + amp));
                g2d.setColor(Color.getHSBColor((i / (float)visualBars.length) + rotation, 0.7f, 1.0f));
                g2d.draw(new Line2D.Float(x1, y1, x2, y2));
            }

            g2d.setColor(Color.BLACK);
            g2d.fill(new Ellipse2D.Float(cx - currentRadius, cy - currentRadius, currentRadius * 2, currentRadius * 2));
            g2d.setColor(beatColor);
            g2d.setStroke(new BasicStroke(3f));
            g2d.draw(new Ellipse2D.Float(cx - currentRadius, cy - currentRadius, currentRadius * 2, currentRadius * 2));
        }
    }

    private static class ShrinkingCircle {
        float radius, originalEnergy;
        Color color;

        ShrinkingCircle(float initialRadius, float energy, Color c) {
            this.radius = initialRadius;
            this.originalEnergy = energy;
            this.color = c;
        }

        void update(float currentEnergy) {
            this.radius -= (4.0f + (currentEnergy * 50.0f));
        }
    }

    private static class Particle {
        float x, y, xv, yv, size;
        Color color;
        Random r = new Random();

        Particle(float x, float y) {
            this.x = x; this.y = y;
            this.xv = (r.nextFloat() - 0.5f) * 3;
            this.yv = (r.nextFloat() - 0.5f) * 3;
            this.size = r.nextFloat() * 4 + 2;
            this.color = Color.WHITE;
        }

        void update(float energy, int w, int h, Color beatColor) {
            float speedMult = 1.0f + (energy * 20);
            x += xv * speedMult;
            y += yv * speedMult;
            int r2 = (int)(color.getRed() * 0.92f + beatColor.getRed() * 0.08f);
            int g2 = (int)(color.getGreen() * 0.92f + beatColor.getGreen() * 0.08f);
            int b2 = (int)(color.getBlue() * 0.92f + beatColor.getBlue() * 0.08f);
            this.color = new Color(r2, g2, b2);

            if (x < 0) x = w; if (x > w) x = 0;
            if (y < 0) y = h; if (y > h) y = 0;
        }
    }
}