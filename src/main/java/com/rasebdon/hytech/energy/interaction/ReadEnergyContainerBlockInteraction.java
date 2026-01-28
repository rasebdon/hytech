package com.rasebdon.hytech.energy.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.Interaction;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.rasebdon.hytech.core.components.LogisticContainerComponent;
import com.rasebdon.hytech.energy.EnergyModule;
import com.rasebdon.hytech.energy.IEnergyContainer;
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

    private static void doInteraction(
            @Nonnull InteractionContext context,
            @Nonnull World world,
            @Nonnull Vector3i targetBlock) {

        var energyBlock = EnergyUtils.getComponentAtBlock(
                world,
                targetBlock,
                EnergyModule.get().getBlockEnergyContainerComponentType()
        );

        var energyPipe = EnergyUtils.getComponentAtBlock(
                world,
                targetBlock,
                EnergyModule.get().getEnergyPipeComponentType()
        );

        var component = energyBlock == null ? energyPipe : energyBlock;
        if (component != null) {
            sendEnergyMessageToPlayer(context.getEntity(), component);
        }
    }

    private static void sendEnergyMessageToPlayer(Ref<EntityStore> playerRef, LogisticContainerComponent<IEnergyContainer> component) {
        EnergyUtils.sendPlayerMessage(playerRef, component.toString());
    }

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
