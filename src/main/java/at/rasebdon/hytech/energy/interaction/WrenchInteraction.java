package at.rasebdon.hytech.energy.interaction;

import at.rasebdon.hytech.core.components.LogisticComponent;
import at.rasebdon.hytech.core.components.LogisticEntityProxyComponent;
import at.rasebdon.hytech.core.util.BlockFaceUtil;
import at.rasebdon.hytech.core.util.HytechUtil;
import at.rasebdon.hytech.energy.EnergyModule;
import at.rasebdon.hytech.energy.HytechEnergyContainer;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class WrenchInteraction extends SimpleInteraction {
    public static final BuilderCodec<WrenchInteraction> CODEC = BuilderCodec.builder(
            WrenchInteraction.class, WrenchInteraction::new, SimpleInteraction.CODEC
    ).build();

    private static void doBlockInteraction(
            @Nonnull InteractionSyncData clientState,
            @Nonnull World world,
            @Nonnull Player player,
            @Nonnull Vector3i targetBlock) {

        var containerComponent = getContainer(world, targetBlock);

        if (containerComponent != null) {
            BlockFace worldFace = clientState.blockFace;
            Vector3i worldDir = BlockFaceUtil.getVectorFromFace(worldFace);

            var blockRef = HytechUtil.getBlockEntityRef(world, targetBlock);
            assert blockRef != null;

            var blockTransform = HytechUtil.getBlockTransform(blockRef, world.getChunkStore().getStore());
            assert blockTransform != null;

            var localFace = BlockFaceUtil.getLocalFace(worldDir, blockTransform.rotation());
            cycleFace(containerComponent, localFace, player);
        }
    }

    private static void cycleFace(LogisticComponent<?> containerComponent, BlockFace localFace, Player player) {
        containerComponent.cycleBlockFaceConfig(localFace);
        player.sendMessage(Message.raw("Side " + localFace.name() + " changed to: " + containerComponent.getFaceConfigTowards(localFace).name()));
    }

    @Nullable
    private static LogisticComponent<HytechEnergyContainer> getContainer(World world, Vector3i targetBlock) {
        var blockContainer = HytechUtil.getComponentAtBlock(world, targetBlock,
                EnergyModule.get().getBlockComponentType());
        return blockContainer != null ? blockContainer :
                HytechUtil.getComponentAtBlock(world, targetBlock, EnergyModule.get().getPipeComponentType());
    }

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Client;
    }

    @Override
    public boolean needsRemoteSync() {
        return true;
    }

    @Override
    protected void simulateTick0(boolean firstRun, float time, @NonNull InteractionType type, @NonNull InteractionContext context, @NonNull CooldownHandler cooldownHandler) {
        super.simulateTick0(firstRun, time, type, context, cooldownHandler);
        if (!Interaction.failed(context.getState().state)) {
            InteractionSyncData clientState = context.getClientState();

            assert clientState != null;

            if (!firstRun) {
                context.getState().state = context.getClientState().state;
            } else {
                clientState.blockFace = BlockFace.None;
            }
        }
    }

    @Override
    protected void tick0(boolean firstRun, float time, @NonNull InteractionType type, @NonNull InteractionContext context, @NonNull CooldownHandler cooldownHandler) {
        var clientState = context.getClientState();
        assert clientState != null;

        if (!firstRun) {
            context.getState().state = clientState.state;
        } else {
            wrenchInteraction(context, clientState);
            super.tick0(firstRun, time, type, context, cooldownHandler);
        }
    }

    protected void wrenchInteraction(
            @Nonnull InteractionContext interactionContext,
            @Nonnull InteractionSyncData clientState) {
        var playerRef = interactionContext.getEntity();
        var entityStore = playerRef.getStore();
        var player = entityStore.getComponent(playerRef, Player.getComponentType());
        assert player != null;

        var world = entityStore.getExternalData().getWorld();

        var targetBlock = interactionContext.getTargetBlock();
        if (targetBlock != null) {
            if (clientState.blockFace == BlockFace.None) {
                interactionContext.getState().state = InteractionState.Failed;
                return;
            }

            doBlockInteraction(
                    clientState,
                    world,
                    player,
                    new Vector3i(targetBlock.x, targetBlock.y, targetBlock.z));
        }

        var targetEntity = interactionContext.getTargetEntity();
        if (targetEntity != null) {
            doEntityProxyInteraction(
                    entityStore,
                    targetEntity,
                    player
            );
        }
    }

    private void doEntityProxyInteraction(
            Store<EntityStore> store,
            Ref<EntityStore> targetEntity,
            Player player) {
        var entityProxy = store.getComponent(targetEntity, LogisticEntityProxyComponent.getComponentType());
        if (entityProxy == null) return;

        cycleFace(entityProxy.getLogisticContainerComponent(), entityProxy.getBlockFace(), player);
    }

    @Override
    protected @NotNull com.hypixel.hytale.protocol.Interaction generatePacket() {
        return new SimpleBlockInteraction();
    }

    @Override
    protected void configurePacket(com.hypixel.hytale.protocol.Interaction packet) {
        super.configurePacket(packet);
        SimpleBlockInteraction p = (SimpleBlockInteraction) packet;
        p.useLatestTarget = false;
    }
}