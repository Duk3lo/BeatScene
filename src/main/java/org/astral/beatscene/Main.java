package org.astral.beatscene;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import org.astral.beatscene.audio.AudioInput;
import org.astral.beatscene.command.BeatCommand;
import org.jetbrains.annotations.NotNull;

public final class Main extends JavaPlugin {

    private static Main instance;

    static {
        System.setProperty("java.awt.headless", "false");
    }

    public Main(@NotNull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        instance = this;

        try {
            getLogger().atInfo().log("Preparando análisis de audio mediante STBVorbis...");
            AudioInput.prepareAudio();
            getLogger().atInfo().log("Audio cargado y procesado exitosamente.");
        } catch (Throwable t) {
            getLogger().atWarning().log("No se pudo preparar el audio (Entorno Servidor), pero el plugin continuará. Error: " + t.getMessage());
        }

        BeatCommand.registerCommand(getCommandRegistry());
        getLogger().atInfo().log("BeatScene Loaded");
    }

    @Override
    protected void start() {
    }

    @Override
    protected void shutdown() {
        if (instance != null) {
            instance.getLogger().atInfo().log("Beat Scene close");
        }
    }

    public static Main getInstance(){
        return instance;
    }
}