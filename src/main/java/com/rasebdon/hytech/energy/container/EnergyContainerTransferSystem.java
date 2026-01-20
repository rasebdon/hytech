package com.rasebdon.hytech.energy.container;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockFace;
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
        if (energyContainer == null) return;

        var world = store.getExternalData().getWorld();
        var blockLocation = EnergyUtils.getBlockTransform(blockRef, store);
        if (blockLocation == null) return;

        for (var worldSide : Vector3i.BLOCK_SIDES) {
            // 1. Get local face of current block
            BlockFace localFace = EnergyUtils.getLocalFace(worldSide, blockLocation.rotation());

            // Ensure this face is allowed to extract (Logic inside your component)
            if (!energyContainer.canExtract(localFace)) continue;

            var neighborWorldPos = worldSide.clone().add(blockLocation.worldPos());
            var neighborRef = EnergyUtils.getBlockEntityRef(world, neighborWorldPos);
            if (neighborRef == null) continue;

            var neighborLoc = EnergyUtils.getBlockTransform(neighborRef, store);
            var neighborContainer = store.getComponent(neighborRef, energyContainerType);

            if (neighborContainer != null && neighborLoc != null) {
                // 2. Get local face of neighbor (hit from opposite world direction)
                Vector3i oppositeWorldDir = worldSide.clone().negate();
                BlockFace neighborLocalFace = EnergyUtils.getLocalFace(oppositeWorldDir, neighborLoc.rotation());

                // 3. Priority and Sidedness Check
                if (neighborContainer.canReceive(neighborLocalFace)) {
                    // Only push if neighbor priority is higher, or if this container is full/near-full
                    // Prio: 0 = Cable, 1 = Battery/Producer, 2 = Consumer
                    if (neighborContainer.getPriority() >= energyContainer.getPriority() || energyContainer.isFull()) {

                        long toSend = Math.min(energyContainer.getMaxExtract(), neighborContainer.getMaxReceive());

                        // 4. Perform the transfer using the translated local faces
                        long accepted = neighborContainer.receiveEnergy(neighborLocalFace, toSend, false);
                        energyContainer.extractEnergy(localFace, accepted, false);
                    }
                }
            }
        }
    }

    @Override
    public @Nullable Query<ChunkStore> getQuery() {
        return energyContainerType;
    }
}
