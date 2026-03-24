package org.astral.beatscene.command.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.astral.beatscene.Main;
import org.astral.beatscene.audio.AudioInput;
import org.astral.beatscene.audio.visualiser.InstrumentDetector;
import org.astral.beatscene.audio.visualiser.SimpleVisualizer;
import org.astral.beatscene.audio.visualiser.Terminal;
import org.jetbrains.annotations.NotNull;

public class PlayCustomSound extends AbstractPlayerCommand {

    public PlayCustomSound(@NotNull String name, @NotNull String description, boolean requiresConfirmation) {
        super(name, description, requiresConfirmation);
    }

    @Override
    protected void execute(@NotNull CommandContext commandContext, @NotNull Store<EntityStore> store, @NotNull Ref<EntityStore> ref, @NotNull PlayerRef playerRef, @NotNull World world) {
        TransformComponent transform = store.getComponent(ref, EntityModule.get().getTransformComponentType());
        if (transform == null) return;

        // Ejecuta el sonido dentro del juego Hytale
        int index = SoundEvent.getAssetMap().getIndex("Miss");
        SoundUtil.playSoundEvent3dToPlayer(ref, index, SoundCategory.UI, transform.getPosition(), store);

        // Obtenemos los datos desde STBVorbis
        AudioInput.AudioData data = AudioInput.getAudioData();

        if (data == null || data.frames.isEmpty()) {
            Main.getInstance().getLogger().atSevere().log("Error: No hay datos de audio cargados.");
            return;
        }

        // Abrimos la terminal
        Terminal terminal = new Terminal();
        terminal.open(data);
        //SimpleVisualizer simpleVisualizer = new SimpleVisualizer();
        //simpleVisualizer.open(data);
        InstrumentDetector detector = new InstrumentDetector();
        detector.open(data);
    }
}