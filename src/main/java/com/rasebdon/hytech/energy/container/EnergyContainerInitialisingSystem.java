package com.rasebdon.hytech.energy.container;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EnergyContainerInitialisingSystem extends RefSystem<ChunkStore> {
    private final ComponentType<ChunkStore, EnergyContainerComponent> energyContainerComponentType;

    public EnergyContainerInitialisingSystem(ComponentType<ChunkStore, EnergyContainerComponent> componentType) {
        this.energyContainerComponentType = componentType;
    }

    @Override
    public void onEntityAdded(
            @NotNull Ref<ChunkStore> ref,
            @NotNull AddReason reason,
            @NotNull Store<ChunkStore> store,
            @NotNull CommandBuffer<ChunkStore> commandBuffer) {
        var energyContainer = store.getComponent(ref, this.energyContainerComponentType);
        assert energyContainer != null;

        energyContainer.reloadTransferTargets(ref, store, true);
    }

    @Override
    public void onEntityRemove(
            @NotNull Ref<ChunkStore> ref,
            @NotNull RemoveReason reason,
            @NotNull Store<ChunkStore> store,
            @NotNull CommandBuffer<ChunkStore> commandBuffer) {
        var energyContainer = store.getComponent(ref, this.energyContainerComponentType);
        assert energyContainer != null;

        energyContainer.removeAsTransferTargetFromNeighbors(ref, store);
    }

    @Override
    public @Nullable Query<ChunkStore> getQuery() {
        return this.energyContainerComponentType;
    }
}