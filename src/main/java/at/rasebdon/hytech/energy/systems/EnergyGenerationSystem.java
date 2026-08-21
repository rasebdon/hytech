package at.rasebdon.hytech.energy.systems;

import at.rasebdon.hytech.core.util.HytechUtil;
import at.rasebdon.hytech.energy.components.EnergyBlockComponent;
import at.rasebdon.hytech.energy.components.EnergyGeneratorComponent;
import at.rasebdon.hytech.energy.components.FuelBurnerComponent;
import at.rasebdon.hytech.energy.util.FuelUtil;
import at.rasebdon.hytech.items.components.ItemBlockComponent;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/// Fills a generator block's energy container according to its generator type.
///
/// Rates are per tick, matching how energy transfer is denominated -- see
/// [at.rasebdon.hytech.core.containers.LogisticContainer#getTransferSpeed].
public class EnergyGenerationSystem extends EntityTickingSystem<ChunkStore> {

    private final ComponentType<ChunkStore, EnergyGeneratorComponent> generatorType;
    private final ComponentType<ChunkStore, EnergyBlockComponent> containerType;
    private final ComponentType<ChunkStore, FuelBurnerComponent> burnerType;
    private final ComponentType<ChunkStore, ItemBlockComponent> itemContainerType;
    private final Archetype<ChunkStore> archetype;

    public EnergyGenerationSystem(
            ComponentType<ChunkStore, EnergyGeneratorComponent> generatorType,
            ComponentType<ChunkStore, EnergyBlockComponent> containerType,
            ComponentType<ChunkStore, FuelBurnerComponent> burnerType,
            ComponentType<ChunkStore, ItemBlockComponent> itemContainerType) {
        this.generatorType = generatorType;
        this.containerType = containerType;
        this.burnerType = burnerType;
        this.itemContainerType = itemContainerType;
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
        var blockTransform = HytechUtil.getBlockTransform(blockRef, store);
        if (blockTransform == null) return;

        long currentRate = calculateCurrentRate(
                gen, archetypeChunk, index, store, new Vector3i(blockTransform.worldPos()), dt);

        gen.setCurrentRate(currentRate);

        if (currentRate > 0) {
            container.add(currentRate);
        }
    }

    private long calculateCurrentRate(
            EnergyGeneratorComponent gen,
            ArchetypeChunk<ChunkStore> archetypeChunk,
            int index,
            Store<ChunkStore> store,
            Vector3i pos,
            float dt
    ) {
        return switch (gen.getGeneratorType()) {
            case SOLAR -> generateSolar(gen, store);
            case WIND -> generateWind(gen, pos);
            case FUEL_SOLID -> generateSolidFuel(gen, archetypeChunk, index, dt);
            // Liquid fuel needs the fluid module, which does not exist yet. Returning 0
            // rather than falling through to the solid path keeps a mis-declared block inert
            // instead of silently burning items.
            case FUEL_LIQUID -> 0L;
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

    /// Burns solid fuel from the block's own item container.
    ///
    /// The fuel items live in `hytech:items:container` rather than on the burner component,
    /// so an item pipe can feed the generator exactly as it would feed a chest.
    private long generateSolidFuel(
            EnergyGeneratorComponent gen,
            ArchetypeChunk<ChunkStore> archetypeChunk,
            int index,
            float dt
    ) {
        var burner = archetypeChunk.getComponent(index, burnerType);
        if (burner == null) return 0L;

        if (!burner.isBurning() && !ignite(burner, fuelContainer(archetypeChunk, index))) {
            return 0L;
        }

        // Scaled by how much of the tick was actually fuelled, so the last partial tick of an
        // item pays out proportionally rather than in full.
        float burnt = burner.consume(dt);
        if (burnt <= 0f || dt <= 0f) return 0L;

        return Math.max(0L, Math.round(gen.getBaseRate() * (burnt / dt)));
    }

    private boolean ignite(FuelBurnerComponent burner, @Nullable ItemBlockComponent fuel) {
        if (fuel == null) return false;

        double quality = FuelUtil.consumeOne(fuel.getItemContainer());
        if (quality <= 0d) return false;

        burner.ignite(quality);
        return true;
    }

    @Nullable
    private ItemBlockComponent fuelContainer(ArchetypeChunk<ChunkStore> archetypeChunk, int index) {
        return archetypeChunk.getComponent(index, itemContainerType);
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return archetype;
    }
}
