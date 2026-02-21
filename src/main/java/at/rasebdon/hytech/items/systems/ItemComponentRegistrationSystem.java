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
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerBlockState;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import java.util.HashMap;
import java.util.Map;

public class ItemComponentRegistrationSystem
        extends LogisticComponentRegistrationSystem<HytechItemContainer> {

    private final Map<ItemContainerBlockState, HytechItemContainerWrapper> legacyContainers = new HashMap<>();

    public ItemComponentRegistrationSystem(
            ComponentType<ChunkStore, ? extends LogisticBlockComponent<HytechItemContainer>> blockComponentType,
            ComponentType<ChunkStore, ? extends LogisticPipeComponent<HytechItemContainer>> pipeComponentType,
            IEventRegistry eventRegistry,
            LogisticNetworkSystem<HytechItemContainer> networkSystem
    ) {
        super(
                blockComponentType,
                pipeComponentType,
                eventRegistry,
                ItemContainerChangedEvent.class,
                networkSystem
        );
    }

    /* -----------------------------------------------------------
       Legacy Registration
       ----------------------------------------------------------- */

    public void registerLegacyContainer(
            HytechItemContainerWrapper wrapper,
            Ref<ChunkStore> ref,
            Store<ChunkStore> store
    ) {
        legacyContainers.put(wrapper.getBlockState(), wrapper);
        rebuildNeighborsFor(wrapper, ref, store);
    }

    public void unregisterLegacyContainer(
            ItemContainerBlockState state,
            Ref<ChunkStore> ref,
            Store<ChunkStore> store
    ) {
        var wrapper = legacyContainers.remove(state);
        if (wrapper != null) {
            removeNeighborsFor(wrapper, ref, store);
        }
    }

    /* -----------------------------------------------------------
       Overrides – Also connect wrappers when logistic components spawn
       ----------------------------------------------------------- */

    @Override
    public void onEntityAdded(
            Ref<ChunkStore> ref,
            AddReason reason,
            Store<ChunkStore> store,
            CommandBuffer<ChunkStore> commandBuffer
    ) {
        super.onEntityAdded(ref, reason, store, commandBuffer);

        var component = getContainer(store, ref);
        if (component != null) {
            rebuildNeighborsFor(component, ref, store);
        }
    }

    @Override
    public void onEntityRemove(
            Ref<ChunkStore> ref,
            RemoveReason reason,
            Store<ChunkStore> store,
            CommandBuffer<ChunkStore> commandBuffer
    ) {
        var component = getContainer(store, ref);
        if (component != null) {
            removeNeighborsFor(component, ref, store);
        }

        super.onEntityRemove(ref, reason, store, commandBuffer);
    }

    /* -----------------------------------------------------------
       Unified Neighbor Logic (Works for both wrappers + components)
       ----------------------------------------------------------- */

    private void rebuildNeighborsFor(
            LogisticComponent<HytechItemContainer> component,
            Ref<ChunkStore> ref,
            Store<ChunkStore> store
    ) {
        component.clearNeighbors();

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

            var neighbor = resolveNeighbor(store, neighborRef);
            if (neighbor == null) continue;

            var neighborTransform = HytechUtil.getBlockTransform(neighborRef, store);
            if (neighborTransform == null) continue;

            var neighborFace = BlockFaceUtil.getLocalFace(
                    worldDir.clone().negate(),
                    neighborTransform.rotation()
            );

            component.addNeighbor(localFace, neighborFace, neighbor);
        }
    }

    private void removeNeighborsFor(
            LogisticComponent<HytechItemContainer> component,
            Ref<ChunkStore> ref,
            Store<ChunkStore> store
    ) {
        var transform = HytechUtil.getBlockTransform(ref, store);
        if (transform == null) return;

        var world = store.getExternalData().getWorld();

        for (var worldDir : Vector3i.BLOCK_SIDES) {

            var neighborRef = HytechUtil.getBlockEntityRef(
                    world,
                    worldDir.clone().add(transform.worldPos())
            );

            if (neighborRef == null) continue;

            var neighbor = resolveNeighbor(store, neighborRef);
            if (neighbor != null) {
                component.removeNeighbor(neighbor);
            }
        }
    }

    /**
     * Resolves both ECS logistic components AND legacy wrappers.
     */
    private LogisticComponent<HytechItemContainer> resolveNeighbor(
            Store<ChunkStore> store,
            Ref<ChunkStore> ref
    ) {
        // 1️⃣ Try normal ECS component
        var component = getContainer(store, ref);
        if (component != null) return component;

        // 2️⃣ Try legacy wrapper via block state
        var transform = HytechUtil.getBlockTransform(ref, store);
        if (transform == null) return null;

        var world = store.getExternalData().getWorld();
        var blockState = ItemUtils.getLegacyItemContainer(world, transform.worldPos());
        if (blockState instanceof ItemContainerBlockState itemState) {
            return legacyContainers.get(itemState);
        }

        return null;
    }
}