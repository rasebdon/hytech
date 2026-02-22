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
import at.rasebdon.hytech.items.utils.ItemUtils;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerBlockState;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

public class ItemComponentRegistrationSystem
        extends LogisticComponentRegistrationSystem<HytechItemContainer> {

    private final Map<ItemContainerBlockState, HytechItemContainerWrapper> wrappers =
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
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull Store<ChunkStore> store
    ) {
        wrappers.put(wrapper.blockState(), wrapper);
        attachWrapperToAdjacentComponents(wrapper, ref, store);
    }

    public void unregisterLegacyContainer(
            @Nonnull ItemContainerBlockState state,
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull Store<ChunkStore> store
    ) {
        var wrapper = wrappers.remove(state);
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
                (face, component) ->
                        component.addExternalNeighbor(face, wrapper));
    }

    private void detachWrapperFromAdjacentComponents(
            HytechItemContainerWrapper wrapper,
            Ref<ChunkStore> ref,
            Store<ChunkStore> store
    ) {
        forEachAdjacentLogisticComponent(ref, store,
                (_, component) ->
                        component.removeExternalNeighbor(wrapper));
    }

    private void attachAdjacentWrappers(
            LogisticComponent<HytechItemContainer> component,
            Ref<ChunkStore> ref,
            Store<ChunkStore> store
    ) {
        var transform = HytechUtil.getBlockTransform(ref, store);
        if (transform == null) return;

        var world = store.getExternalData().getWorld();

        for (var worldDir : Vector3i.BLOCK_SIDES) {

            var localFace = BlockFaceUtil.getLocalFace(worldDir, transform.rotation());
            var neighborPos = worldDir.clone().add(transform.worldPos());

            var blockState = ItemUtils.getLegacyItemContainer(world, neighborPos);
            if (blockState instanceof ItemContainerBlockState itemState) {

                var wrapper = wrappers.get(itemState);
                if (wrapper != null) {
                    component.addExternalNeighbor(localFace, wrapper);
                }
            }
        }
    }

    private void detachAdjacentWrappers(
            LogisticComponent<HytechItemContainer> component,
            Ref<ChunkStore> ref,
            Store<ChunkStore> store
    ) {
        var transform = HytechUtil.getBlockTransform(ref, store);
        if (transform == null) return;

        var world = store.getExternalData().getWorld();

        for (var worldDir : Vector3i.BLOCK_SIDES) {

            var neighborPos = worldDir.clone().add(transform.worldPos());
            var blockState = ItemUtils.getLegacyItemContainer(world, neighborPos);

            if (blockState instanceof ItemContainerBlockState itemState) {
                var wrapper = wrappers.get(itemState);
                if (wrapper != null) {
                    component.removeExternalNeighbor(wrapper);
                }
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

        for (var worldDir : Vector3i.BLOCK_SIDES) {

            var localFace = BlockFaceUtil.getLocalFace(worldDir, transform.rotation());

            var neighborRef = HytechUtil.getBlockEntityRef(
                    world,
                    worldDir.clone().add(transform.worldPos())
            );

            if (neighborRef == null) continue;

            var component = getContainer(store, neighborRef);
            if (component != null) {
                consumer.accept(localFace, component);
            }
        }
    }

    @FunctionalInterface
    private interface AdjacentConsumer {
        void accept(BlockFace face, LogisticComponent<HytechItemContainer> component);
    }
}