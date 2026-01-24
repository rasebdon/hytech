package com.rasebdon.hytech.energy.systems;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.core.components.LogisticContainerComponent;
import com.rasebdon.hytech.core.events.LogisticChangeType;
import com.rasebdon.hytech.energy.IEnergyContainer;
import com.rasebdon.hytech.energy.components.BlockEnergyContainerComponent;
import com.rasebdon.hytech.energy.events.EnergyContainerChangedEvent;
import com.rasebdon.hytech.energy.util.EnergyUtils;
import com.rasebdon.hytech.energy.util.EventBusUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EnergyContainerRegistrationSystem extends RefSystem<ChunkStore> {
    private final ComponentType<ChunkStore, BlockEnergyContainerComponent> blockEnergyContainerComponentType;

    public EnergyContainerRegistrationSystem(
            ComponentType<ChunkStore, BlockEnergyContainerComponent> componentType, IEventRegistry eventRegistry) {
        this.blockEnergyContainerComponentType = componentType;
        eventRegistry.register(EnergyContainerChangedEvent.class, this::handleEnergyContainerChangedEvent);
    }

    private void handleEnergyContainerChangedEvent(EnergyContainerChangedEvent event) {
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
        var energyContainer = store.getComponent(ref, this.blockEnergyContainerComponentType);
        assert energyContainer != null;

        reloadTransferTargets(energyContainer, ref, store);
        EventBusUtil.dispatchIfListening(new EnergyContainerChangedEvent(ref, store,
                LogisticChangeType.ADDED, energyContainer));
    }

    @Override
    public void onEntityRemove(
            @NotNull Ref<ChunkStore> ref,
            @NotNull RemoveReason reason,
            @NotNull Store<ChunkStore> store,
            @NotNull CommandBuffer<ChunkStore> commandBuffer) {
        var containerComponent = store.getComponent(ref, this.blockEnergyContainerComponentType);
        assert containerComponent != null;

        removeAsTransferTargetFromNeighbors(containerComponent, ref, store);
        EventBusUtil.dispatchIfListening(new EnergyContainerChangedEvent(ref, store,
                LogisticChangeType.REMOVED, containerComponent));
    }


    @Override
    public @Nullable Query<ChunkStore> getQuery() {
        return this.blockEnergyContainerComponentType;
    }

    public void reloadTransferTargets(
            LogisticContainerComponent<IEnergyContainer> containerComponent,
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
            var neighborContainerComponent = store.getComponent(neighborRef, this.blockEnergyContainerComponentType);

            if (neighborContainerComponent != null && neighborLoc != null) {
                var oppositeWorldDir = worldSide.clone().negate();
                var neighborLocalFace = EnergyUtils.getLocalFace(oppositeWorldDir, neighborLoc.rotation());

                var neighborCanReceive = neighborContainerComponent.canReceiveFromFace(neighborLocalFace);

                if (containerCanExtract && neighborCanReceive) {
                    containerComponent.tryAddTransferTarget(
                            neighborContainerComponent,
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
                }
            }
        }
    }

    public void removeAsTransferTargetFromNeighbors(
            LogisticContainerComponent<IEnergyContainer> containerComponent,
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
            var neighborContainer = store.getComponent(neighborRef, this.blockEnergyContainerComponentType);

            if (neighborContainer != null && neighborLoc != null) {
                neighborContainer.removeTransferTarget(containerComponent.getContainer());
            }
        }
    }
}