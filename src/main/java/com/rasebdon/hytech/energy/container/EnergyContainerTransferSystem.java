package com.rasebdon.hytech.energy.container;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;
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
        assert energyContainer != null;

        var world = store.getExternalData().getWorld();
        var blockLocation = EnergyUtils.getBlockLocation(blockRef, store);
        assert blockLocation != null;

        for (var side : Vector3i.BLOCK_SIDES) {
            var neighborWorldPos = side.clone().add(blockLocation.worldPos());

            // Use the util to find the neighbor's energy container
            var energyContainerNeighbor = EnergyUtils.getComponentAtBlock(world, neighborWorldPos, energyContainerType);

            if (energyContainerNeighbor != null) {
                long toSend = Math.min(energyContainer.getMaxExtract(), energyContainerNeighbor.getMaxReceive());
                long accepted = energyContainerNeighbor.receiveEnergy(toSend, false);
                energyContainer.extractEnergy(accepted, false);
            }
        }
    }

    @Override
    public @Nullable Query<ChunkStore> getQuery() {
        return energyContainerType;
    }
}
