package com.rasebdon.hytech.energy;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.energy.container.EnergyContainerComponent;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;

public class EnergyTransferSystem extends EntityTickingSystem<ChunkStore> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final ComponentType<ChunkStore, EnergyContainerComponent> energyContainerType;

    public EnergyTransferSystem(ComponentType<ChunkStore, EnergyContainerComponent> energyContainerType) {
        this.energyContainerType = energyContainerType;
    }

    @Override
    public void tick(float dt,
                     int index,
                     @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
                     @Nonnull Store<ChunkStore> store,
                     @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        var energyContainer = archetypeChunk.getComponent(index, this.energyContainerType);

        assert energyContainer != null;
        LOGGER.atInfo().log(energyContainer.toString());
    }

    @Override
    public @Nullable Query<ChunkStore> getQuery() {
        return energyContainerType;
    }
}
