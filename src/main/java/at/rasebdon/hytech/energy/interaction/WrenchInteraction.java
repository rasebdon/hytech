package at.rasebdon.hytech.energy.interaction;

import at.rasebdon.hytech.core.components.LogisticContainerComponent;
import at.rasebdon.hytech.core.components.LogisticEntityProxyComponent;
import at.rasebdon.hytech.core.util.BlockFaceUtil;
import at.rasebdon.hytech.core.util.HytechUtil;
import at.rasebdon.hytech.energy.EnergyModule;
import at.rasebdon.hytech.energy.IEnergyContainer;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class WrenchInteraction extends SimpleInteraction {
    public static final BuilderCodec<WrenchInteraction> CODEC = BuilderCodec.builder(
            WrenchInteraction.class, WrenchInteraction::new
    ).build();
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Client;
    }

    @Override
    public boolean needsRemoteSync() {
        return true;
    }

    private static void doBlockInteraction(
            @Nonnull InteractionContext context,
            @Nonnull World world,
            @Nonnull Player player,
            @Nonnull Vector3i targetBlock) {

        var containerComponent = getContainer(world, targetBlock);

        if (containerComponent != null) {
            var clientState = context.getClientState();
            assert clientState != null;

            // Map the clicked world face to the local face based on block rotation
            LOGGER.atInfo().log(context.getState().blockFace.toString());
            LOGGER.atInfo().log(context.getClientState().blockFace.toString());
            LOGGER.atInfo().log(context.getServerState().blockFace.toString());


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

    @Override
    protected void simulateTick0(boolean firstRun, float time, @NonNull InteractionType type, @NonNull InteractionContext context, @NonNull CooldownHandler cooldownHandler) {
        LOGGER.atInfo().log("SIMULATE");

        super.simulateTick0(firstRun, time, type, context, cooldownHandler);
    }

    private static void cycleFace(LogisticContainerComponent<?> containerComponent, BlockFace localFace, Player player) {
        containerComponent.cycleBlockFaceConfig(localFace);
        player.sendMessage(Message.raw("Side " + localFace.name() + " changed to: " + containerComponent.getFaceConfigTowards(localFace).name()));
    }

    @Nullable
    private static LogisticContainerComponent<IEnergyContainer> getContainer(World world, Vector3i targetBlock) {
        var blockContainer = HytechUtil.getComponentAtBlock(world, targetBlock,
                EnergyModule.get().getBlockEnergyContainerComponentType());
        return blockContainer != null ? blockContainer :
                HytechUtil.getComponentAtBlock(world, targetBlock, EnergyModule.get().getEnergyPipeComponentType());
    }

    @Override
    protected void tick0(boolean firstRun, float time, @NonNull InteractionType type, @NonNull InteractionContext context, @NonNull CooldownHandler cooldownHandler) {
        if (!firstRun) {
            return;
        }

        wrenchInteraction(type, context, cooldownHandler);
        super.tick0(true, time, type, context, cooldownHandler);
    }

    protected void wrenchInteraction(
            @Nonnull InteractionType interactionType,
            @Nonnull InteractionContext interactionContext,
            @Nonnull CooldownHandler cooldownHandler) {
        var playerRef = interactionContext.getEntity();
        var entityStore = playerRef.getStore();
        var player = entityStore.getComponent(playerRef, Player.getComponentType());
        assert player != null;

        var world = entityStore.getExternalData().getWorld();

        var targetBlock = interactionContext.getTargetBlock();
        if (targetBlock != null) {
            var clientState = interactionContext.getClientState();
            assert clientState != null;

            LOGGER.atInfo().log(clientState.state.name());

            if (clientState.blockFace == BlockFace.None) {
                interactionContext.getState().state = InteractionState.Failed;
                return;
            }

            doBlockInteraction(
                    interactionContext,
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
}