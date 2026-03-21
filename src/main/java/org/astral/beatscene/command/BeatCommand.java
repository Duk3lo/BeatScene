package org.astral.beatscene.command;

import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import org.astral.beatscene.command.commands.PlayCustomSound;
import org.jetbrains.annotations.NotNull;

public class BeatCommand {
    public static void registerCommand(@NotNull CommandRegistry commandRegistry){
        commandRegistry.registerCommand(new PlayCustomSound("beat","plays sound assets", false));
    }
}
