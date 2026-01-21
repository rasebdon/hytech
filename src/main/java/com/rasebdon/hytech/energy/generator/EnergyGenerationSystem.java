package com.rasebdon.hytech.energy.generator;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.energy.container.EnergyContainerComponent;
import javax.annotation.Nonnull;

public class EnergyGenerationSystem extends EntityTickingSystem<ChunkStore> {
    private final ComponentType<ChunkStore, EnergyGeneratorComponent> generatorType;
    private final ComponentType<ChunkStore, EnergyContainerComponent> containerType;

    public EnergyGenerationSystem(
            ComponentType<ChunkStore, EnergyGeneratorComponent> generatorType,
            ComponentType<ChunkStore, EnergyContainerComponent> containerType) {
        this.generatorType = generatorType;
        this.containerType = containerType;
    }

    @Override
    public void tick(float dt,
                     int index,
                     @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
                     @Nonnull Store<ChunkStore> store,
                     @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        Ref<ChunkStore> ref = archetypeChunk.getReferenceTo(index);


        var generator = archetypeChunk.getComponent(index, this.generatorType);
        var container = archetypeChunk.getComponent(index, this.containerType);

        if (generator != null && container != null) {
            container.receiveEnergy(generator.getGenerationRate(), false);
        }
    }

    @Override
    public Query<ChunkStore> getQuery() {
        // This ensures the system only ticks for entities that have BOTH components
        return Archetype.of(generatorType, containerType);
    }
}