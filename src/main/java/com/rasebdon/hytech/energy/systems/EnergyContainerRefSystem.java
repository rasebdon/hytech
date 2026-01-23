package com.rasebdon.hytech.energy.systems;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.energy.components.SingleBlockEnergyContainerComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EnergyContainerRefSystem extends RefSystem<ChunkStore> {
    private final ComponentType<ChunkStore, SingleBlockEnergyContainerComponent> singleBlockEnergyContainerComponentType;

    public EnergyContainerRefSystem(ComponentType<ChunkStore, SingleBlockEnergyContainerComponent> componentType) {
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

        energyContainer.reloadTransferTargets(ref, store, true);
    }

    @Override
    public void onEntityRemove(
            @NotNull Ref<ChunkStore> ref,
            @NotNull RemoveReason reason,
            @NotNull Store<ChunkStore> store,
            @NotNull CommandBuffer<ChunkStore> commandBuffer) {
        var energyContainer = store.getComponent(ref, this.singleBlockEnergyContainerComponentType);
        assert energyContainer != null;

        energyContainer.removeAsTransferTargetFromNeighbors(ref, store);
    }

    @Override
    public @Nullable Query<ChunkStore> getQuery() {
        return this.singleBlockEnergyContainerComponentType;
    }
}