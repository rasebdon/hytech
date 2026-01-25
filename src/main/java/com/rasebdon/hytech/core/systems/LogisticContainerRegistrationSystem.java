package com.rasebdon.hytech.core.systems;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.core.components.ILogisticContainer;
import com.rasebdon.hytech.core.components.LogisticContainerComponent;
import com.rasebdon.hytech.core.components.LogisticPipeComponent;
import com.rasebdon.hytech.core.events.LogisticChangeType;
import com.rasebdon.hytech.core.events.LogisticContainerChangedEvent;
import com.rasebdon.hytech.energy.util.EnergyUtils;
import com.rasebdon.hytech.energy.util.EventBusUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class LogisticContainerRegistrationSystem<TContainer extends ILogisticContainer> extends RefSystem<ChunkStore> {
    private final ComponentType<ChunkStore, ? extends LogisticContainerComponent<TContainer>> containerComponentType;
    private final ComponentType<ChunkStore, ? extends LogisticPipeComponent<TContainer>> pipeComponentType;

    private final Query<ChunkStore> query;

    protected LogisticContainerRegistrationSystem(
            ComponentType<ChunkStore, ? extends LogisticContainerComponent<TContainer>> containerComponentType,
            ComponentType<ChunkStore, ? extends LogisticPipeComponent<TContainer>> pipeComponentType,
            IEventRegistry eventRegistry,
            Class<? extends LogisticContainerChangedEvent<TContainer>> eventClass) {
        this.containerComponentType = containerComponentType;
        this.pipeComponentType = pipeComponentType;

        query = Query.or(containerComponentType, pipeComponentType);

        eventRegistry.register(eventClass, this::handleEnergyContainerChangedEvent);
    }

    private void handleEnergyContainerChangedEvent(LogisticContainerChangedEvent<TContainer> event) {
        if (event.isChanged()) {
            removeAsTransferTargetFromNeighbors(event.getComponent(), event.blockRef, event.store);
            reloadTransferTargets(event.getComponent(), event.blockRef, event.store);
        }
    }

    @Override
    public void onEntityAdded(
            @NotNull Ref<ChunkStore> ref,
            @NotNull AddReason reason,
            @NotNull Store<ChunkStore> store,
            @NotNull CommandBuffer<ChunkStore> commandBuffer) {
        var containerComponent = store.getComponent(ref, this.containerComponentType);
        if (containerComponent != null) {
            reloadTransferTargets(containerComponent, ref, store);
            EventBusUtil.dispatchIfListening(createEvent(ref, store, LogisticChangeType.ADDED, containerComponent));
        }

        var pipeComponent = store.getComponent(ref, this.pipeComponentType);
        if (pipeComponent != null) {
            reloadTransferTargets(pipeComponent, ref, store);
            pipeComponent.reloadPipeConnections(store.getExternalData().getWorld(), ref);
        }
    }

    protected abstract LogisticContainerChangedEvent<TContainer> createEvent(
            Ref<ChunkStore> blockRef, Store<ChunkStore> store,
            LogisticChangeType changeType, LogisticContainerComponent<TContainer> component
    );

    @Override
    public void onEntityRemove(
            @NotNull Ref<ChunkStore> ref,
            @NotNull RemoveReason reason,
            @NotNull Store<ChunkStore> store,
            @NotNull CommandBuffer<ChunkStore> commandBuffer) {
        var containerComponent = store.getComponent(ref, this.containerComponentType);
        if (containerComponent != null) {
            removeAsTransferTargetFromNeighbors(containerComponent, ref, store);
            EventBusUtil.dispatchIfListening(createEvent(ref, store, LogisticChangeType.REMOVED, containerComponent));
        }

        var pipeComponent = store.getComponent(ref, this.pipeComponentType);
        if (pipeComponent != null) {
            removeAsTransferTargetFromNeighbors(pipeComponent, ref, store);
            pipeComponent.clearPipeConnections(store.getExternalData().getWorld());
        }
    }


    @Override
    public @Nullable Query<ChunkStore> getQuery() {
        return query;
    }

    public void reloadTransferTargets(
            LogisticContainerComponent<TContainer> containerComponent,
            Ref<ChunkStore> blockRef,
            Store<ChunkStore> store) {
        containerComponent.clearTransferTargets();

        var world = store.getExternalData().getWorld();
        var blockLocation = EnergyUtils.getBlockTransform(blockRef, store);
        if (blockLocation == null) return;

        for (var worldSide : Vector3i.BLOCK_SIDES) {
            var localFace = EnergyUtils.getLocalFace(worldSide, blockLocation.rotation());

            var containerCanExtract = containerComponent.canExtractFromFace(localFace);
            var containerCanReceive = containerComponent.canExtractFromFace(localFace);

            if (!containerCanExtract && !containerCanReceive)
                continue;

            var neighborWorldPos = worldSide.clone().add(blockLocation.worldPos());
            var neighborRef = EnergyUtils.getBlockEntityRef(world, neighborWorldPos);
            if (neighborRef == null) continue;

            var neighborLoc = EnergyUtils.getBlockTransform(neighborRef, store);

            var neighborContainerComponent = getNeighborContainer(store, neighborRef);

            if (neighborContainerComponent != null && neighborLoc != null) {
                var oppositeWorldDir = worldSide.clone().negate();
                var neighborLocalFace = EnergyUtils.getLocalFace(oppositeWorldDir, neighborLoc.rotation());

                var neighborCanReceive = neighborContainerComponent.canReceiveFromFace(neighborLocalFace);

                if (containerCanExtract && neighborCanReceive) {
                    containerComponent.tryAddTransferTarget(
                            neighborContainerComponent.getContainer(),
                            localFace,
                            neighborLocalFace
                    );
                }

                var neighborCanExtract = neighborContainerComponent.canExtractFromFace(neighborLocalFace);
                if (containerCanReceive && neighborCanExtract) {
                    neighborContainerComponent.tryAddTransferTarget(
                            containerComponent.getContainer(),
                            neighborLocalFace,
                            localFace
                    );

                    if (neighborContainerComponent instanceof LogisticPipeComponent<TContainer> neighborPipe) {
                        neighborPipe.reloadPipeConnections(world, neighborRef);
                    }
                }
            }
        }
    }

    private @Nullable LogisticContainerComponent<TContainer> getNeighborContainer(
            Store<ChunkStore> store,
            Ref<ChunkStore> neighborRef
    ) {
        var component = store.getComponent(neighborRef, containerComponentType);
        return component != null
                ? component
                : store.getComponent(neighborRef, pipeComponentType);
    }

    public void removeAsTransferTargetFromNeighbors(
            LogisticContainerComponent<TContainer> containerComponent,
            @NotNull Ref<ChunkStore> blockRef,
            @NotNull Store<ChunkStore> store) {
        var world = store.getExternalData().getWorld();
        var blockLocation = EnergyUtils.getBlockTransform(blockRef, store);
        if (blockLocation == null) return;

        for (var worldSide : Vector3i.BLOCK_SIDES) {
            var neighborWorldPos = worldSide.clone().add(blockLocation.worldPos());
            var neighborRef = EnergyUtils.getBlockEntityRef(world, neighborWorldPos);
            if (neighborRef == null) continue;

            var neighborLoc = EnergyUtils.getBlockTransform(neighborRef, store);
            var neighborContainerComponent = getNeighborContainer(store, neighborRef);

            if (neighborContainerComponent != null && neighborLoc != null) {
                neighborContainerComponent.removeTransferTarget(containerComponent.getContainer());

                if (neighborContainerComponent instanceof LogisticPipeComponent<TContainer> neighborPipe) {
                    neighborPipe.reloadPipeConnections(world, neighborRef);
                }
            }
        }
    }
}
