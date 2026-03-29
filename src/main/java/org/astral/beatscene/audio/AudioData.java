package org.astral.beatscene.audio;

import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

public class AudioData {
    public List<float[]> frames = new ArrayList<>();
    public ShortBuffer pcmData;
    public int sampleRate;
    public int channels;
}