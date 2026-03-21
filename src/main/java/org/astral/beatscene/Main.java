package org.astral.beatscene;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import org.astral.beatscene.command.BeatCommand;
import org.jetbrains.annotations.NotNull;

public final class Main extends JavaPlugin {

    private static Main instance;

    public Main(@NotNull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        instance = this;
        BeatCommand.registerCommand(getCommandRegistry());

        getLogger().atInfo().log("BeatScene Loaded");
    }

    @Override
    protected void start() {
    }

    @Override
    protected void shutdown() {
        instance.getLogger().atInfo().log("Beat Scene close");
    }

    public static Main getInstance(){
        return instance;
    }

}
