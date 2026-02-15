package at.rasebdon.hytech.energy.systems;

import at.rasebdon.hytech.core.util.HytechUtil;
import at.rasebdon.hytech.energy.components.EnergyBlockComponent;
import at.rasebdon.hytech.energy.components.EnergyGeneratorComponent;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;

public class EnergyGenerationSystem extends EntityTickingSystem<ChunkStore> {
    private final ComponentType<ChunkStore, EnergyGeneratorComponent> generatorType;
    private final ComponentType<ChunkStore, EnergyBlockComponent> containerType;
    private final Archetype<ChunkStore> archetype;

    public EnergyGenerationSystem(
            ComponentType<ChunkStore, EnergyGeneratorComponent> generatorType,
            ComponentType<ChunkStore, EnergyBlockComponent> containerType) {
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

        EnergyGeneratorComponent gen = archetypeChunk.getComponent(index, generatorType);
        EnergyBlockComponent container = archetypeChunk.getComponent(index, containerType);
        if (gen == null || container == null) return;

        var blockRef = archetypeChunk.getReferenceTo(index);
        var blockPosition = HytechUtil.getBlockTransform(blockRef, store);
        assert blockPosition != null;

        long amount = calculateOutput(gen, store, blockPosition.worldPos().clone(), dt);
        if (amount > 0) {
            container.addEnergy(amount);
        }
    }

    private long calculateOutput(
            EnergyGeneratorComponent gen,
            Store<ChunkStore> store,
            Vector3i pos,
            float dt
    ) {
        return switch (gen.getGeneratorType()) {
            case SOLAR -> generateSolar(gen, store);
            case WIND -> generateWind(gen, pos);
            case FUEL_SOLID -> generateFuel(gen, store, true, dt);
            case FUEL_LIQUID -> generateFuel(gen, store, false, dt);
        };
    }

    private long generateSolar(
            EnergyGeneratorComponent gen,
            Store<ChunkStore> store
    ) {

        var time = store.getExternalData().getWorld().getEntityStore().getStore()
                .getResource(WorldTimeResource.getResourceType());
        var efficiency = time.getSunlightFactor();

        var energy = Math.round(gen.getBaseRate() * efficiency);
        return Math.max(0L, energy);
    }

    private long generateWind(
            EnergyGeneratorComponent gen,
            Vector3i pos
    ) {
        int height = pos.y;

        int minHeight = 64;
        int maxHeight = 160;

        if (height <= minHeight) {
            return 0;
        }

        float heightFactor = Math.min(
                1.0f,
                (height - minHeight) / (float) (maxHeight - minHeight)
        );

        float energy = gen.getBaseRate() * heightFactor;
        return Math.max(0L, (long) energy);
    }

    private long generateFuel(
            EnergyGeneratorComponent gen,
            Store<ChunkStore> store,
            boolean solid,
            float dt
    ) {
        return 0;

//        FuelComponent fuel = store.getComponent(FuelComponent.class);
//        if (fuel == null) return 0;
//
//        if (!fuel.isBurning()) {
//            boolean consumed = solid
//                    ? fuel.consumeSolidFuel()
//                    : fuel.consumeLiquidFuel();
//
//            if (!consumed) return 0;
//        }
//
//        fuel.tickBurn(dt);
//
//        float energy = gen.getBaseRate() * dt;
//        return Math.max(0L, (long) energy);
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return archetype;
    }
}