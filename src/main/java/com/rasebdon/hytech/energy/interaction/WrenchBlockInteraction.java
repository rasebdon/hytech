package com.rasebdon.hytech.energy.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.rasebdon.hytech.core.events.LogisticContainerChangedEvent;
import com.rasebdon.hytech.energy.EnergyModule;
import com.rasebdon.hytech.energy.events.EnergyContainerChangedEvent;
import com.rasebdon.hytech.energy.util.BlockFaceUtil;
import com.rasebdon.hytech.energy.util.EnergyUtils;
import com.rasebdon.hytech.energy.util.EventBusUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class WrenchBlockInteraction extends SimpleBlockInteraction {
    @Nonnull
    public static final BuilderCodec<WrenchBlockInteraction> CODEC =
            BuilderCodec.builder(
                    WrenchBlockInteraction.class,
                    WrenchBlockInteraction::new,
                    SimpleBlockInteraction.CODEC)
            .documentation("Attempts to configure the target block energy container.").build();

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

        var energyContainer = EnergyUtils.getComponentAtBlock(world, targetBlock,
                EnergyModule.get().getBlockEnergyContainerComponentType());

        if (energyContainer != null) {
            var clientState = context.getClientState();
            assert clientState != null;

            var player = world.getEntityStore().getStore().getComponent(context.getEntity(), Player.getComponentType());
            assert player != null;

            // Map the clicked world face to the local face based on block rotation
            BlockFace worldFace = clientState.blockFace;
            Vector3i worldDir = BlockFaceUtil.getVectorFromFace(worldFace);

            var blockRef = EnergyUtils.getBlockEntityRef(world, targetBlock);
            assert blockRef != null;

            var blockTransform = EnergyUtils.getBlockTransform(blockRef, world.getChunkStore().getStore());
            assert blockTransform != null;

            var localFace = BlockFaceUtil.getLocalFace(worldDir, blockTransform.rotation());
            var blockFaceConfig = energyContainer.getCurrentBlockFaceConfig();
            blockFaceConfig.cycleFaceConfigType(localFace);

            var event = new EnergyContainerChangedEvent(
                    LogisticContainerChangedEvent.ChangeType.CHANGED,
                    energyContainer
            );
            EventBusUtil.dispatchIfListening(event);

            player.sendMessage(Message.raw("Side " + worldFace.name() + " (Local: " + localFace.name() + ") changed to: " + blockFaceConfig.getFaceConfigType(localFace).name()));
        }
    }
}
