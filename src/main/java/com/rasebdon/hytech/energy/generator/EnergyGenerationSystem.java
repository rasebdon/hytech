package com.rasebdon.hytech.energy.generator;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.energy.components.EnergyGeneratorComponent;
import com.rasebdon.hytech.energy.components.SingleBlockEnergyContainerComponent;

import javax.annotation.Nonnull;

public class EnergyGenerationSystem extends EntityTickingSystem<ChunkStore> {
    private final ComponentType<ChunkStore, EnergyGeneratorComponent> generatorType;
    private final ComponentType<ChunkStore, SingleBlockEnergyContainerComponent> containerType;
    private final Archetype<ChunkStore> archetype;

    public EnergyGenerationSystem(
            ComponentType<ChunkStore, EnergyGeneratorComponent> generatorType,
            ComponentType<ChunkStore, SingleBlockEnergyContainerComponent> containerType) {
        this.generatorType = generatorType;
        this.containerType = containerType;
        this.archetype = Archetype.of(generatorType, containerType);
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
            container.addEnergy(generator.getGenerationRate());
        }
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return archetype;
    }
}