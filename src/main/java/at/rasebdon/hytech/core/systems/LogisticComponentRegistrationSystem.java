package at.rasebdon.hytech.core.systems;

import at.rasebdon.hytech.core.components.LogisticBlockComponent;
import at.rasebdon.hytech.core.components.LogisticComponent;
import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticComponentChangedEvent;
import at.rasebdon.hytech.core.networks.LogisticNetworkSystem;
import at.rasebdon.hytech.core.util.BlockFaceUtil;
import at.rasebdon.hytech.core.util.HytechUtil;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;

public abstract class LogisticComponentRegistrationSystem<TContainer>
        extends RefSystem<ChunkStore> {

    private final ComponentType<ChunkStore, ? extends LogisticBlockComponent<TContainer>> blockType;
    private final ComponentType<ChunkStore, ? extends LogisticPipeComponent<TContainer>> pipeType;
    private final Query<ChunkStore> query;

    protected LogisticComponentRegistrationSystem(
            ComponentType<ChunkStore, ? extends LogisticBlockComponent<TContainer>> containerType,
            ComponentType<ChunkStore, ? extends LogisticPipeComponent<TContainer>> pipeType,
            IEventRegistry eventRegistry,
            Class<? extends LogisticComponentChangedEvent<TContainer>> containerChangedEventClass,
            LogisticNetworkSystem<TContainer> networkRegistrationSystem
    ) {
        this.blockType = containerType;
        this.pipeType = pipeType;
        this.query = Query.or(containerType, pipeType);

        eventRegistry.register(containerChangedEventClass, networkRegistrationSystem::onContainerChanged);
    }

    @Override
    public @Nullable Query<ChunkStore> getQuery() {
        return query;
    }

    @Override
    public void onEntityAdded(
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull AddReason reason,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer
    ) {
        var containerComponent = getContainer(store, ref);
        if (containerComponent == null) return;

        containerComponent.dispatchChangeEvent(LogisticChangeType.ADDED);
        rebuildNeighborMaps(containerComponent, ref, store);
    }

    @Override
    public void onEntityRemove(
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull RemoveReason reason,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer
    ) {
        var containerComponent = getContainer(store, ref);
        if (containerComponent == null) return;

        removeFromNeighbors(containerComponent, ref, store);
        containerComponent.dispatchChangeEvent(LogisticChangeType.REMOVED);
    }

    protected void rebuildNeighborMaps(
            LogisticComponent<TContainer> container,
            Ref<ChunkStore> ref,
            Store<ChunkStore> store
    ) {
        container.clearNeighbors();

        var transform = HytechUtil.getBlockTransform(ref, store);
        if (transform == null) return;

        var world = store.getExternalData().getWorld();

        for (var worldDir : Vector3i.BLOCK_SIDES) {
            var localFace = BlockFaceUtil.getLocalFace(worldDir, transform.rotation());

            var neighborRef = HytechUtil.getBlockEntityRef(
                    world, worldDir.clone().add(transform.worldPos())
            );
            if (neighborRef == null) continue;

            var neighborContainer = getContainer(store, neighborRef);
            var neighborTransform = HytechUtil.getBlockTransform(neighborRef, store);
            if (neighborContainer == null || neighborTransform == null) continue;

            var neighborFace = BlockFaceUtil.getLocalFace(
                    worldDir.clone().negate(),
                    neighborTransform.rotation()
            );

            // TODO : Trigger network system from here so that container or neighbor
            //  container cannot have empty networks set if they are pipes

            container.addNeighbor(localFace, neighborFace, neighborContainer);
        }
    }

    protected void removeFromNeighbors(
            LogisticComponent<TContainer> containerComponent,
            Ref<ChunkStore> ref,
            Store<ChunkStore> store
    ) {
        var transform = HytechUtil.getBlockTransform(ref, store);
        if (transform == null) return;

        var world = store.getExternalData().getWorld();

        for (var worldDir : Vector3i.BLOCK_SIDES) {
            var neighborRef = HytechUtil.getBlockEntityRef(
                    world, worldDir.clone().add(transform.worldPos())
            );
            if (neighborRef == null) continue;

            var neighborContainer = getContainer(store, neighborRef);
            if (neighborContainer == null) continue;

            containerComponent.removeNeighbor(neighborContainer);
        }
    }

    protected @Nullable LogisticComponent<TContainer> getContainer(
            Store<ChunkStore> store, Ref<ChunkStore> ref
    ) {
        var container = store.getComponent(ref, blockType);
        return container != null ? container : store.getComponent(ref, pipeType);
    }
}
