package com.rasebdon.hytech.energy.generator;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
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

        var generator = archetypeChunk.getComponent(index, this.generatorType);
        var container = archetypeChunk.getComponent(index, this.containerType);

        if (generator != null && container != null) {
            // Add energy to the container based on the generator's rate
            container.receiveEnergy(generator.getGenerationRate(), false);
        }
    }

    @Override
    public Query<ChunkStore> getQuery() {
        // This ensures the system only ticks for entities that have BOTH components
        return Archetype.of(generatorType, containerType);
    }
}