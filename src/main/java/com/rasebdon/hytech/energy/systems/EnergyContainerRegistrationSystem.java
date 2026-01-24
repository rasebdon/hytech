package com.rasebdon.hytech.energy.systems;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.core.systems.LogisticTransferSystem;
import com.rasebdon.hytech.energy.EnergyContainer;
import com.rasebdon.hytech.energy.EnergyModule;
import com.rasebdon.hytech.energy.components.BlockEnergyContainerComponent;
import com.rasebdon.hytech.energy.components.EnergyContainerComponent;
import com.rasebdon.hytech.energy.util.EnergyUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EnergyContainerRegistrationSystem extends RefSystem<ChunkStore> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final ComponentType<ChunkStore, BlockEnergyContainerComponent> singleBlockEnergyContainerComponentType;
    private final LogisticTransferSystem<EnergyContainer> energyTransferSystem;

    public EnergyContainerRegistrationSystem(
            ComponentType<ChunkStore, BlockEnergyContainerComponent> componentType,
            LogisticTransferSystem<EnergyContainer> energyTransferSystem) {
        this.energyTransferSystem = energyTransferSystem;
        this.singleBlockEnergyContainerComponentType = componentType;
    }

    @Override
    public void onEntityAdded(
            @NotNull Ref<ChunkStore> ref,
            @NotNull AddReason reason,
            @NotNull Store<ChunkStore> store,
            @NotNull CommandBuffer<ChunkStore> commandBuffer) {
        var energyContainer = store.getComponent(ref, this.singleBlockEnergyContainerComponentType);
        assert energyContainer != null;

        energyTransferSystem.addEnergyContainer(energyContainer);
        reloadTransferTargets(energyContainer, ref, store);
    }

    @Override
    public void onEntityRemove(
            @NotNull Ref<ChunkStore> ref,
            @NotNull RemoveReason reason,
            @NotNull Store<ChunkStore> store,
            @NotNull CommandBuffer<ChunkStore> commandBuffer) {
        var energyContainer = store.getComponent(ref, this.singleBlockEnergyContainerComponentType);
        assert energyContainer != null;

        energyTransferSystem.removeEnergyContainer(energyContainer);
        removeAsTransferTargetFromNeighbors(energyContainer, ref, store);
    }

    @Override
    public @Nullable Query<ChunkStore> getQuery() {
        return this.singleBlockEnergyContainerComponentType;
    }

    public void reloadTransferTargets(
            EnergyContainerComponent energyContainer,
            Ref<ChunkStore> blockRef,
            Store<ChunkStore> store) {
        energyContainer.clearTransferTargets();

        var world = store.getExternalData().getWorld();
        var blockLocation = EnergyUtils.getBlockTransform(blockRef, store);
        if (blockLocation == null) return;

        for (var worldSide : Vector3i.BLOCK_SIDES) {
            var localFace = EnergyUtils.getLocalFace(worldSide, blockLocation.rotation());

            var containerCanExtract = energyContainer.canExtractFromFace(localFace);
            var containerCanReceive = energyContainer.canExtractFromFace(localFace);

            if (!containerCanExtract && !containerCanReceive)
                continue;

            var neighborWorldPos = worldSide.clone().add(blockLocation.worldPos());
            var neighborRef = EnergyUtils.getBlockEntityRef(world, neighborWorldPos);
            if (neighborRef == null) continue;

            var neighborLoc = EnergyUtils.getBlockTransform(neighborRef, store);
            var neighborContainer = store.getComponent(neighborRef, EnergyModule.get().getBlockEnergyContainerComponentType());

            if (neighborContainer != null && neighborLoc != null) {
                var oppositeWorldDir = worldSide.clone().negate();
                var neighborLocalFace = EnergyUtils.getLocalFace(oppositeWorldDir, neighborLoc.rotation());

                var neighborCanReceive = neighborContainer.canReceiveFromFace(neighborLocalFace);

                if (containerCanExtract && neighborCanReceive) {
                    energyContainer.addTransferTarget(
                            neighborContainer,
                            localFace,
                            neighborLocalFace
                    );
                }

                var neighborCanExtract = neighborContainer.canExtractFromFace(neighborLocalFace);
                if (containerCanReceive && neighborCanExtract) {
                    neighborContainer.addTransferTarget(
                            energyContainer,
                            neighborLocalFace,
                            localFace
                    );
                }
            }
        }
    }

    public void removeAsTransferTargetFromNeighbors(
            EnergyContainerComponent containerComponent,
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
            var neighborContainer = store.getComponent(neighborRef, EnergyModule.get().getBlockEnergyContainerComponentType());

            if (neighborContainer != null && neighborLoc != null) {
                LOGGER.atInfo().log("%s removing %s as extract target", toString(), neighborContainer.toString());
                neighborContainer.removeTransferTarget(containerComponent);
            }
        }
    }

}