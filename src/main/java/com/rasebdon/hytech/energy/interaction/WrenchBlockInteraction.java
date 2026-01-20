package com.rasebdon.hytech.energy.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.rasebdon.hytech.energy.EnergyModule;
import com.rasebdon.hytech.energy.util.EnergyUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class WrenchBlockInteraction extends SimpleBlockInteraction {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

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
                EnergyModule.get().getEnergyContainerComponentType());

        if (energyContainer != null) {
            var clientState = context.getClientState();
            assert clientState != null;

            var player = world.getEntityStore().getStore().getComponent(context.getEntity(), Player.getComponentType());
            assert player != null;

            // Map the clicked world face to the local face based on block rotation
            BlockFace worldFace = clientState.blockFace;
            Vector3i worldDir = getVectorFromFace(worldFace);

            var blockRef = EnergyUtils.getBlockEntityRef(world, targetBlock);
            assert blockRef != null;

            var blockTransform = EnergyUtils.getBlockTransform(blockRef, world.getChunkStore().getStore());
            assert blockTransform != null;

            BlockFace localFace = getLocalFace(worldDir, blockTransform.rotation());

            energyContainer.cycleSideConfig(localFace);

            player.sendMessage(Message.raw("Side " + worldFace.name() + " (Local: " + localFace.name() + ") changed to: " + energyContainer.getSideConfig(localFace).name()));
        }
    }

    private static BlockFace getLocalFace(Vector3i worldDir, RotationTuple rotation) {
        // Apply inverse rotation to the world direction to find the local face
        Rotation invYaw = Rotation.None.subtract(rotation.yaw());
        Rotation invPitch = Rotation.None.subtract(rotation.pitch());
        Rotation invRoll = Rotation.None.subtract(rotation.roll());

        Vector3i localVec = Rotation.rotate(worldDir, invYaw, invPitch, invRoll);

        if (localVec.y > 0) return BlockFace.Up;
        if (localVec.y < 0) return BlockFace.Down;
        if (localVec.z < 0) return BlockFace.North;
        if (localVec.z > 0) return BlockFace.South;
        if (localVec.x > 0) return BlockFace.East;
        if (localVec.x < 0) return BlockFace.West;
        return BlockFace.None;
    }

    private static Vector3i getVectorFromFace(BlockFace face) {
        return switch (face) {
            case Up -> new Vector3i(0, 1, 0);
            case Down -> new Vector3i(0, -1, 0);
            case North -> new Vector3i(0, 0, -1);
            case South -> new Vector3i(0, 0, 1);
            case East -> new Vector3i(1, 0, 0);
            case West -> new Vector3i(-1, 0, 0);
            default -> new Vector3i(0, 0, 0);
        };
    }
}
