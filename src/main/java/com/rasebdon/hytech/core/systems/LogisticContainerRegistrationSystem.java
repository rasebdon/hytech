package com.rasebdon.hytech.core.systems;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.core.components.LogisticBlockComponent;
import com.rasebdon.hytech.core.components.LogisticContainerComponent;
import com.rasebdon.hytech.core.components.LogisticPipeComponent;
import com.rasebdon.hytech.core.events.LogisticChangeType;
import com.rasebdon.hytech.core.events.LogisticContainerChangedEvent;
import com.rasebdon.hytech.core.networks.LogisticNetworkSystem;
import com.rasebdon.hytech.energy.util.EnergyUtils;
import com.rasebdon.hytech.energy.util.EventBusUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class LogisticContainerRegistrationSystem<TContainer>
        extends RefSystem<ChunkStore> {

    private final ComponentType<ChunkStore, ? extends LogisticBlockComponent<TContainer>> containerType;
    private final ComponentType<ChunkStore, ? extends LogisticPipeComponent<TContainer>> pipeType;
    private final Query<ChunkStore> query;

    private final LogisticNetworkSystem<TContainer> networkRegistrationSystem;

    protected LogisticContainerRegistrationSystem(
            ComponentType<ChunkStore, ? extends LogisticBlockComponent<TContainer>> containerType,
            ComponentType<ChunkStore, ? extends LogisticPipeComponent<TContainer>> pipeType,
            IEventRegistry eventRegistry,
            Class<? extends LogisticContainerChangedEvent<TContainer>> containerChangedEventClass,
            LogisticNetworkSystem<TContainer> networkRegistrationSystem
    ) {
        this.containerType = containerType;
        this.pipeType = pipeType;
        this.query = Query.or(containerType, pipeType);
        this.networkRegistrationSystem = networkRegistrationSystem;

        eventRegistry.register(containerChangedEventClass, this::onContainerChanged);
    }

    @Override
    public @Nullable Query<ChunkStore> getQuery() {
        return query;
    }

    @Override
    public void onEntityAdded(
            @NotNull Ref<ChunkStore> ref,
            @NotNull AddReason reason,
            @NotNull Store<ChunkStore> store,
            @NotNull CommandBuffer<ChunkStore> commandBuffer
    ) {
        var component = getContainer(store, ref);
        if (component == null) return;

        rebuildTransferTargets(component, ref, store);
        onVisualsAdded(component, ref, store);

        EventBusUtil.dispatchIfListening(
                createLogisticContainerChangedEvent(ref, store, LogisticChangeType.ADDED, component)
        );
    }

    @Override
    public void onEntityRemove(
            @NotNull Ref<ChunkStore> ref,
            @NotNull RemoveReason reason,
            @NotNull Store<ChunkStore> store,
            @NotNull CommandBuffer<ChunkStore> commandBuffer
    ) {
        var component = getContainer(store, ref);
        if (component == null) return;

        removeFromNeighbors(component, ref, store);
        onVisualsRemoved(component, store);

        EventBusUtil.dispatchIfListening(
                createLogisticContainerChangedEvent(ref, store, LogisticChangeType.REMOVED, component)
        );
    }

    private void onContainerChanged(LogisticContainerChangedEvent<TContainer> event) {
        if (event.isChanged()) {
            removeFromNeighbors(event.getComponent(), event.blockRef, event.store);
            rebuildTransferTargets(event.getComponent(), event.blockRef, event.store);
        }

        networkRegistrationSystem.onContainerChanged(event);
    }

    protected void rebuildTransferTargets(
            LogisticContainerComponent<TContainer> container,
            Ref<ChunkStore> ref,
            Store<ChunkStore> store
    ) {
        container.clearTransferTargets();

        var transform = EnergyUtils.getBlockTransform(ref, store);
        if (transform == null) return;

        var world = store.getExternalData().getWorld();

        for (var worldDir : Vector3i.BLOCK_SIDES) {
            var localFace = EnergyUtils.getLocalFace(worldDir, transform.rotation());

            boolean canExtract = container.canExtractFromFace(localFace);
            boolean canReceive = container.canReceiveFromFace(localFace);
            if (!canExtract && !canReceive) continue;

            var neighborRef = EnergyUtils.getBlockEntityRef(
                    world, worldDir.clone().add(transform.worldPos())
            );
            if (neighborRef == null) continue;

            var neighbor = getContainer(store, neighborRef);
            var neighborTransform = EnergyUtils.getBlockTransform(neighborRef, store);
            if (neighbor == null || neighborTransform == null) continue;

            var neighborFace = EnergyUtils.getLocalFace(
                    worldDir.clone().negate(),
                    neighborTransform.rotation()
            );

            if (canExtract && neighbor.canReceiveFromFace(neighborFace)) {
                container.tryAddTransferTarget(
                        neighbor, localFace, neighborFace
                );
            }

            if (canReceive && neighbor.canExtractFromFace(neighborFace)) {
                neighbor.tryAddTransferTarget(
                        container, neighborFace, localFace
                );
            }

            updatePipeVisual(neighbor, world, neighborRef);
        }
    }

    protected void removeFromNeighbors(
            LogisticContainerComponent<TContainer> container,
            Ref<ChunkStore> ref,
            Store<ChunkStore> store
    ) {
        var transform = EnergyUtils.getBlockTransform(ref, store);
        if (transform == null) return;

        var world = store.getExternalData().getWorld();

        for (var worldDir : Vector3i.BLOCK_SIDES) {
            var neighborRef = EnergyUtils.getBlockEntityRef(
                    world, worldDir.clone().add(transform.worldPos())
            );
            if (neighborRef == null) continue;

            var neighbor = getContainer(store, neighborRef);
            if (neighbor == null) continue;

            neighbor.removeTransferTarget(container);
            updatePipeVisual(neighbor, world, neighborRef);
        }
    }

    private @Nullable LogisticContainerComponent<TContainer> getContainer(
            Store<ChunkStore> store, Ref<ChunkStore> ref
    ) {
        var container = store.getComponent(ref, containerType);
        return container != null ? container : store.getComponent(ref, pipeType);
    }

    private void updatePipeVisual(
            LogisticContainerComponent<TContainer> component,
            World world,
            Ref<ChunkStore> ref
    ) {
        if (component instanceof LogisticPipeComponent<TContainer> pipe) {
            pipe.reloadPipeConnectionModels(world, ref);
        }
    }

    private void onVisualsAdded(
            LogisticContainerComponent<TContainer> component,
            Ref<ChunkStore> ref,
            Store<ChunkStore> store
    ) {
        if (component instanceof LogisticPipeComponent<TContainer> pipe) {
            pipe.reloadPipeConnectionModels(
                    store.getExternalData().getWorld(), ref
            );
        }
    }

    private void onVisualsRemoved(
            LogisticContainerComponent<TContainer> component,
            Store<ChunkStore> store
    ) {
        if (component instanceof LogisticPipeComponent<TContainer> pipe) {
            pipe.clearPipeConnections(store.getExternalData().getWorld());
        }
    }

    protected abstract LogisticContainerChangedEvent<TContainer> createLogisticContainerChangedEvent(
            Ref<ChunkStore> blockRef,
            Store<ChunkStore> store,
            LogisticChangeType changeType,
            LogisticContainerComponent<TContainer> component
    );
}
