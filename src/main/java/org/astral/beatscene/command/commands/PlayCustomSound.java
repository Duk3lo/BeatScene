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
import org.jetbrains.annotations.NotNull;

public class PlayCustomSound extends AbstractPlayerCommand {

    public PlayCustomSound(@NotNull String name, @NotNull String description, boolean requiresConfirmation) {
        super(name, description, requiresConfirmation);
    }

    @Override
    protected void execute(@NotNull CommandContext commandContext, @NotNull Store<EntityStore> store, @NotNull Ref<EntityStore> ref, @NotNull PlayerRef playerRef, @NotNull World world) {
        int index = SoundEvent.getAssetMap().getIndex("Perfect");
        TransformComponent transform = store.getComponent(ref, EntityModule.get().getTransformComponentType());
        if (transform == null)return;
        SoundUtil.playSoundEvent3dToPlayer(ref, index, SoundCategory.UI, transform.getPosition(), store);
        //ItemPrePhysicsSystem.applyGravity();
    }
}
