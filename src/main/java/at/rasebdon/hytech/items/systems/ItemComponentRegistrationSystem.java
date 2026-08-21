package at.rasebdon.hytech.items.systems;

import at.rasebdon.hytech.core.components.LogisticBlockComponent;
import at.rasebdon.hytech.core.components.LogisticComponent;
import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.networks.LogisticNetworkSystem;
import at.rasebdon.hytech.core.systems.LogisticComponentRegistrationSystem;
import at.rasebdon.hytech.core.util.BlockFaceUtil;
import at.rasebdon.hytech.core.util.HytechUtil;
import at.rasebdon.hytech.items.HytechItemContainer;
import at.rasebdon.hytech.items.components.HytechItemContainerWrapper;
import at.rasebdon.hytech.items.events.ItemContainerChangedEvent;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.math.vector.Vector3iUtil;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

public class ItemComponentRegistrationSystem
        extends LogisticComponentRegistrationSystem<HytechItemContainer> {

    /// Wrappers for vanilla containers, keyed by world position. Block states used to be
    /// identity-stable objects that could key this map; components are looked up per call, so
    /// the position is now the stable identity.
    private final Map<Vector3i, HytechItemContainerWrapper> wrappers =
            new HashMap<>();

    public ItemComponentRegistrationSystem(
            ComponentType<ChunkStore, ? extends LogisticBlockComponent<HytechItemContainer>> blockType,
            ComponentType<ChunkStore, ? extends LogisticPipeComponent<HytechItemContainer>> pipeType,
            IEventRegistry eventRegistry,
            LogisticNetworkSystem<HytechItemContainer> networkSystem
    ) {
        super(blockType, pipeType, eventRegistry, ItemContainerChangedEvent.class, networkSystem);
    }

    public void registerLegacyContainer(
            @Nonnull HytechItemContainerWrapper wrapper,
            @Nonnull Vector3i worldPos,
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull Store<ChunkStore> store
    ) {
        wrappers.put(new Vector3i(worldPos), wrapper);
        attachWrapperToAdjacentComponents(wrapper, ref, store);
    }

    public void unregisterLegacyContainer(
            @Nonnull Vector3i worldPos,
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull Store<ChunkStore> store
    ) {
        var wrapper = wrappers.remove(worldPos);
        if (wrapper != null) {
            detachWrapperFromAdjacentComponents(wrapper, ref, store);
        }
    }

    @Override
    public void onEntityAdded(
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull AddReason reason,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer
    ) {
        super.onEntityAdded(ref, reason, store, commandBuffer);

        var component = getContainer(store, ref);
        if (component != null) {
            attachAdjacentWrappers(component, ref, store);
        }
    }

    @Override
    public void onEntityRemove(
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull RemoveReason reason,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer
    ) {
        var component = getContainer(store, ref);
        if (component != null) {
            detachAdjacentWrappers(component, ref, store);
        }

        super.onEntityRemove(ref, reason, store, commandBuffer);
    }

    private void attachWrapperToAdjacentComponents(
            HytechItemContainerWrapper wrapper,
            Ref<ChunkStore> ref,
            Store<ChunkStore> store
    ) {
        forEachAdjacentLogisticComponent(ref, store,
                (componentFace, wrapperFace, component) ->
                        component.addNeighbor(componentFace, wrapperFace, wrapper));
    }

    private void detachWrapperFromAdjacentComponents(
            HytechItemContainerWrapper wrapper,
            Ref<ChunkStore> ref,
            Store<ChunkStore> store
    ) {
        forEachAdjacentLogisticComponent(ref, store,
                (_, _, component) ->
                        component.removeNeighbor(wrapper));
    }

    private void attachAdjacentWrappers(
            LogisticComponent<HytechItemContainer> component,
            Ref<ChunkStore> ref,
            Store<ChunkStore> store
    ) {
        var transform = HytechUtil.getBlockTransform(ref, store);
        if (transform == null) return;

        var world = store.getExternalData().getWorld();

        for (var worldDir : Vector3iUtil.BLOCK_SIDES) {

            var localFace = BlockFaceUtil.getLocalFace(worldDir, transform.rotation());
            var neighborPos = new Vector3i(worldDir).add(transform.worldPos());

            var wrapper = wrappers.get(neighborPos);
            if (wrapper == null) continue;

            var wrapperRef = HytechUtil.getBlockEntityRef(world, neighborPos);
            var wrapperTransform = wrapperRef != null ? HytechUtil.getBlockTransform(wrapperRef, store) : null;
            if (wrapperTransform == null) continue;

            var wrapperFace = BlockFaceUtil.getLocalFace(new Vector3i(worldDir).negate(), wrapperTransform.rotation());
            component.addNeighbor(localFace, wrapperFace, wrapper);
        }
    }

    private void detachAdjacentWrappers(
            LogisticComponent<HytechItemContainer> component,
            Ref<ChunkStore> ref,
            Store<ChunkStore> store
    ) {
        var transform = HytechUtil.getBlockTransform(ref, store);
        if (transform == null) return;

        for (var worldDir : Vector3iUtil.BLOCK_SIDES) {

            var neighborPos = new Vector3i(worldDir).add(transform.worldPos());

            var wrapper = wrappers.get(neighborPos);
            if (wrapper != null) {
                component.removeNeighbor(wrapper);
            }
        }
    }

    private void forEachAdjacentLogisticComponent(
            Ref<ChunkStore> ref,
            Store<ChunkStore> store,
            AdjacentConsumer consumer
    ) {
        var transform = HytechUtil.getBlockTransform(ref, store);
        if (transform == null) return;

        var world = store.getExternalData().getWorld();

        for (var worldDir : Vector3iUtil.BLOCK_SIDES) {

            var wrapperFace = BlockFaceUtil.getLocalFace(worldDir, transform.rotation());

            var neighborRef = HytechUtil.getBlockEntityRef(
                    world,
                    new Vector3i(worldDir).add(transform.worldPos())
            );

            if (neighborRef == null) continue;

            var component = getContainer(store, neighborRef);
            if (component == null) continue;

            var neighborTransform = HytechUtil.getBlockTransform(neighborRef, store);
            if (neighborTransform == null) continue;

            var componentFace = BlockFaceUtil.getLocalFace(new Vector3i(worldDir).negate(), neighborTransform.rotation());
            consumer.accept(componentFace, wrapperFace, component);
        }
    }

    @FunctionalInterface
    private interface AdjacentConsumer {
        void accept(BlockFace componentFace, BlockFace wrapperFace, LogisticComponent<HytechItemContainer> component);
    }
}