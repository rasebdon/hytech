package com.rasebdon.hytech.core.systems;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.core.components.LogisticBlockComponent;
import com.rasebdon.hytech.core.components.LogisticContainerComponent;
import com.rasebdon.hytech.core.components.LogisticPipeComponent;
import com.rasebdon.hytech.core.events.LogisticChangeType;
import com.rasebdon.hytech.core.events.LogisticContainerChangedEvent;
import com.rasebdon.hytech.core.events.LogisticContainerSideConfigChangedEvent;
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

    protected LogisticContainerRegistrationSystem(
            ComponentType<ChunkStore, ? extends LogisticBlockComponent<TContainer>> containerType,
            ComponentType<ChunkStore, ? extends LogisticPipeComponent<TContainer>> pipeType,
            IEventRegistry eventRegistry,
            Class<? extends LogisticContainerChangedEvent<TContainer>> containerChangedEventClass,
            Class<? extends LogisticContainerSideConfigChangedEvent<TContainer>> blockSideConfigChangedEvent,
            LogisticNetworkSystem<TContainer> networkRegistrationSystem
    ) {
        this.containerType = containerType;
        this.pipeType = pipeType;
        this.query = Query.or(containerType, pipeType);

        eventRegistry.register(blockSideConfigChangedEvent, this::onBlockSideConfigChanged);
        eventRegistry.register(containerChangedEventClass, networkRegistrationSystem::onContainerChanged);
    }

    private void onBlockSideConfigChanged(LogisticContainerSideConfigChangedEvent<TContainer> event) {
        var store = event.getStore();
        var ref = event.getBlockRef();
        var containerComponent = event.getContainerComponent();

        removeFromNeighbors(containerComponent, ref, store);
        rebuildTransferTargets(containerComponent, ref, store);

        EventBusUtil.dispatchIfListening(
                createLogisticContainerChangedEvent(ref, store, LogisticChangeType.CHANGED, containerComponent)
        );
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
        var containerComponent = getContainer(store, ref);
        if (containerComponent == null) return;

        removeFromNeighbors(containerComponent, ref, store);

        EventBusUtil.dispatchIfListening(
                createLogisticContainerChangedEvent(ref, store, LogisticChangeType.REMOVED, containerComponent)
        );
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

            var neighborContainer = getContainer(store, neighborRef);
            var neighborTransform = EnergyUtils.getBlockTransform(neighborRef, store);
            if (neighborContainer == null || neighborTransform == null) continue;

            var neighborFace = EnergyUtils.getLocalFace(
                    worldDir.clone().negate(),
                    neighborTransform.rotation()
            );

            if (canExtract && neighborContainer.canReceiveFromFace(neighborFace)) {
                container.tryAddTransferTarget(
                        neighborContainer, localFace, neighborFace
                );
            }

            if (canReceive && neighborContainer.canExtractFromFace(neighborFace)) {
                neighborContainer.tryAddTransferTarget(
                        container, neighborFace, localFace
                );

                EventBusUtil.dispatchIfListening(
                        createLogisticContainerChangedEvent(neighborRef, store,
                                LogisticChangeType.CHANGED, neighborContainer)
                );
            }
        }
    }

    protected void removeFromNeighbors(
            LogisticContainerComponent<TContainer> containerComponent,
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

            var neighborContainer = getContainer(store, neighborRef);
            if (neighborContainer == null) continue;

            neighborContainer.removeTransferTarget(containerComponent);

            EventBusUtil.dispatchIfListening(
                    createLogisticContainerChangedEvent(neighborRef, store,
                            LogisticChangeType.CHANGED, neighborContainer)
            );
        }
    }

    private @Nullable LogisticContainerComponent<TContainer> getContainer(
            Store<ChunkStore> store, Ref<ChunkStore> ref
    ) {
        var container = store.getComponent(ref, containerType);
        return container != null ? container : store.getComponent(ref, pipeType);
    }

    protected abstract LogisticContainerChangedEvent<TContainer> createLogisticContainerChangedEvent(
            Ref<ChunkStore> blockRef,
            Store<ChunkStore> store,
            LogisticChangeType changeType,
            LogisticContainerComponent<TContainer> component
    );
}
