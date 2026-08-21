package at.rasebdon.hytech.core.interactions;

import at.rasebdon.hytech.core.HytechCoreModule;
import at.rasebdon.hytech.core.components.LogisticComponent;
import at.rasebdon.hytech.core.components.LogisticEntityProxyComponent;
import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.util.BlockFaceUtil;
import at.rasebdon.hytech.core.util.HytechUtil;
import at.rasebdon.hytech.core.util.PipeConnectionMask;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.joml.Vector3i;
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
            @Nonnull Ref<EntityStore> playerRef,
            @Nonnull Vector3i targetBlock) {

        var containerComponent = getContainer(world, targetBlock);

        if (containerComponent != null) {
            BlockFace worldFace = resolveTargetedFace(clientState, containerComponent, playerRef, targetBlock);
            if (worldFace == BlockFace.None) return;

            Vector3i worldDir = BlockFaceUtil.getVectorFromFace(worldFace);

            var blockRef = HytechUtil.getBlockEntityRef(world, targetBlock);
            assert blockRef != null;

            var blockTransform = HytechUtil.getBlockTransform(blockRef, world.getChunkStore().getStore());
            assert blockTransform != null;

            var localFace = BlockFaceUtil.getLocalFace(worldDir, blockTransform.rotation());
            cycleFace(containerComponent, localFace, playerRef);
        }
    }

    /// Works out which face the player actually aimed at.
    ///
    /// A pipe renders as a hub plus one arm per connection, and clicking an arm should
    /// configure that connection rather than whichever outer face the ray crossed. The
    /// client is no help here: it sends no raycast data for a block interaction, and the
    /// engine only exposes one bounding box per hitbox set, so the highlighted box is the
    /// union of hub and arms. So the ray is recomputed here from the player's own eye and
    /// look direction, and tested against this pipe's arm boxes. A hit on the hub, or on
    /// no arm at all, falls back to the face the client reported.
    @Nonnull
    private static BlockFace resolveTargetedFace(
            @Nonnull InteractionSyncData clientState,
            @Nonnull LogisticComponent<?> containerComponent,
            @Nonnull Ref<EntityStore> playerRef,
            @Nonnull Vector3i targetBlock) {

        if (containerComponent instanceof LogisticPipeComponent<?> pipe) {
            var armFace = faceUnderCrosshair(pipe, playerRef, targetBlock);
            if (armFace != BlockFace.None) {
                return armFace;
            }
        }

        return clientState.blockFace;
    }

    /// Casts the player's eye ray against the pipe's arm boxes.
    @Nonnull
    private static BlockFace faceUnderCrosshair(
            @Nonnull LogisticPipeComponent<?> pipe,
            @Nonnull Ref<EntityStore> playerRef,
            @Nonnull Vector3i targetBlock) {

        var store = playerRef.getStore();

        var transform = store.getComponent(playerRef, TransformComponent.getComponentType());
        var headRotation = store.getComponent(playerRef, HeadRotation.getComponentType());
        if (transform == null || headRotation == null) return BlockFace.None;

        var eye = new Vector3d(transform.getPosition());

        // Eye height comes from the player's model so crouching and sitting are accounted
        // for; without a model we would be casting from the feet.
        var modelComponent = store.getComponent(playerRef, ModelComponent.getComponentType());
        if (modelComponent != null && modelComponent.getModel() != null) {
            eye.y += modelComponent.getModel().getEyeHeight(playerRef, store);
        }

        return PipeConnectionMask.faceAlongRay(
                PipeConnectionMask.maskOf(pipe),
                pipe.getHubSize(),
                targetBlock,
                eye,
                headRotation.getDirection());
    }

    private static void cycleFace(LogisticComponent<?> containerComponent, BlockFace localFace, Ref<EntityStore> playerRef) {
        containerComponent.cycleBlockFaceConfig(localFace);
        HytechUtil.sendPlayerMessage(playerRef,
                "Side " + localFace.name() + " changed to: " + containerComponent.getFaceConfigTowards(localFace).name());
    }

    @Nullable
    private static LogisticComponent<?> getContainer(World world, Vector3i targetBlock) {
        // TODO : Wrench Config for specific block type (especially important for machines)

        for (var blockType : HytechCoreModule.get().blockComponents) {
            var blockContainer = HytechUtil.getBlockComponent(world, targetBlock, blockType);
            if (blockContainer != null) {
                return blockContainer;
            }
        }

        for (var pipeType : HytechCoreModule.get().pipeComponents) {
            var pipeContainer = HytechUtil.getBlockComponent(world, targetBlock, pipeType);
            if (pipeContainer != null) {
                return pipeContainer;
            }
        }

        return null;
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
                    playerRef,
                    new Vector3i(targetBlock.x, targetBlock.y, targetBlock.z));
            return;
        }

        // A face set to push or pull is drawn by a marker entity rather than the block
        // model, so aiming at that arm targets the entity and never the block.
        var targetEntity = interactionContext.getTargetEntity();
        if (targetEntity != null) {
            doMarkerInteraction(entityStore, targetEntity, playerRef);
        }
    }

    private void doMarkerInteraction(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> targetEntity,
            @Nonnull Ref<EntityStore> playerRef) {

        var proxy = store.getComponent(targetEntity, LogisticEntityProxyComponent.getComponentType());
        if (proxy == null) return;

        // Null after a deserialize: the proxy carries no persisted state by design.
        var component = proxy.getLogisticContainerComponent();
        if (component == null || proxy.getBlockFace() == BlockFace.None) return;

        cycleFace(component, proxy.getBlockFace(), playerRef);
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