package com.rasebdon.hytech.energy.container;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.energy.util.EnergyUtils;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;

public class EnergyContainerTransferSystem extends EntityTickingSystem<ChunkStore> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final ComponentType<ChunkStore, EnergyContainerComponent> energyContainerType;

    public EnergyContainerTransferSystem(ComponentType<ChunkStore, EnergyContainerComponent> energyContainerType) {
        this.energyContainerType = energyContainerType;
    }

    @Override
    public void tick(float dt,
                     int index,
                     @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
                     @Nonnull Store<ChunkStore> store,
                     @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        var blockRef = archetypeChunk.getReferenceTo(index);

        var energyContainer = store.getComponent(blockRef, this.energyContainerType);
        if (energyContainer != null) {
            LOGGER.atInfo().log(energyContainer.toString());
        }

        var worldPos = EnergyUtils.getBlockLocation(blockRef, store);
        if (worldPos != null) {
            LOGGER.atInfo().log(worldPos.toString());
        }

        // TODO : Transfer
    }

    @Override
    public @Nullable Query<ChunkStore> getQuery() {
        return energyContainerType;
    }
}
