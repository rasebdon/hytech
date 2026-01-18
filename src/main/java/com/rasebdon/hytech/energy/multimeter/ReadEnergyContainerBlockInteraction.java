package com.rasebdon.hytech.energy.multimeter;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.block.BlockUtil;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.Interaction;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.rasebdon.hytech.energy.EnergyModule;
import com.rasebdon.hytech.energy.util.EnergyUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ReadEnergyContainerBlockInteraction extends SimpleBlockInteraction {
    @Nonnull
    public static final BuilderCodec<ReadEnergyContainerBlockInteraction> CODEC =
            BuilderCodec.builder(
                    ReadEnergyContainerBlockInteraction.class,
                    ReadEnergyContainerBlockInteraction::new,
                    SimpleBlockInteraction.CODEC)
            .documentation("Attempts to read the target blocks energy container.").build();

    @Override
    protected void interactWithBlock(
            @Nonnull World world,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nullable ItemStack itemInHand,
            @Nonnull Vector3i targetBlock,
            @Nonnull CooldownHandler cooldownHandler) {
        doInteraction(context, world, targetBlock);
    }

    @Override
    protected void simulateInteractWithBlock(
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nullable ItemStack itemInHand,
            @Nonnull World world,
            @Nonnull Vector3i targetBlock) {
        doInteraction(context, world, targetBlock);
    }

    private static void doInteraction(
            @Nonnull InteractionContext context,
            @Nonnull World world,
            @Nonnull Vector3i targetBlock) {

        var energyContainer = EnergyUtils.getComponentAtBlock(
                world,
                targetBlock,
                EnergyModule.get().getEnergyContainerComponentType()
        );

        if (energyContainer != null) {
            EnergyUtils.sendPlayerMessage(context.getEntity(), energyContainer.toString());
        }
    }

    @Nonnull
    @Override
    protected Interaction generatePacket() {
        return new com.hypixel.hytale.protocol.UseBlockInteraction();
    }

    @Nonnull
    @Override
    public String toString() {
        return "ReadEnergyContainerBlockInteraction{} " + super.toString();
    }
}
