package at.rasebdon.hytech.core.systems;

import at.rasebdon.hytech.core.HytechCoreModule;
import at.rasebdon.hytech.core.components.CreativeSourceComponent;
import at.rasebdon.hytech.core.components.LogisticBlockComponent;
import at.rasebdon.hytech.core.containers.ScalarContainer;
import at.rasebdon.hytech.core.containers.TypedScalarContainer;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jspecify.annotations.NonNull;

/// Keeps creative source blocks full and creative void blocks empty.
///
/// One system for every resource type: it queries on [CreativeSourceComponent] and then asks
/// the same block for whichever logistic container it happens to carry, so it works for
/// energy, heat, fluid and gas without knowing any of them. That is what makes a brand new
/// resource type testable the moment its module is registered.
///
/// Slot-based containers (items) are skipped -- "fill with items" has no single answer, and a
/// chest already does the job.
public final class CreativeSourceSystem extends TickingSystem<ChunkStore> {

    /// Twice a second. Fast enough that a source keeps a network saturated, slow enough that it
    /// is not doing this work every tick for a block that exists only for testing.
    private static final float UPDATE_INTERVAL_SECONDS = 0.5f;

    private final ComponentType<ChunkStore, CreativeSourceComponent> creativeType;

    private float updateTime;

    public CreativeSourceSystem(ComponentType<ChunkStore, CreativeSourceComponent> creativeType) {
        this.creativeType = creativeType;
    }

    @Override
    public void tick(float dt, int systemIndex, @NonNull Store<ChunkStore> store) {
        if (this.updateTime < UPDATE_INTERVAL_SECONDS) {
            this.updateTime += dt;
            return;
        }

        this.updateTime = 0f;

        store.forEachChunk(this.creativeType, (chunk, _) -> {
            for (int i = 0; i < chunk.size(); i++) {
                apply(chunk, i);
            }
        });
    }

    private void apply(ArchetypeChunk<ChunkStore> chunk, int index) {
        var creative = chunk.getComponent(index, this.creativeType);
        if (creative == null) return;

        var container = containerOf(chunk, index);
        if (container == null) return;

        if (creative.isVoiding()) {
            container.reduce(container.getAmount());

            // Draining alone leaves a typed tank still claiming whatever it last held, and a
            // claimed tank rejects everything else -- so a void would silently accept one
            // resource forever and refuse the second thing you tested. Release the claim.
            if (container instanceof TypedScalarContainer<?> typed) {
                typed.setResourceType(null);
            }

            return;
        }

        if (container instanceof TypedScalarContainer<?> typed) {
            if (!claim(typed, creative.getResourceType())) return;
        }

        container.add(container.getRemainingCapacity());
    }

    /// Points a typed container at the configured resource, if it is free to be pointed.
    ///
    /// Returns false when the tank already holds something else, so a mis-set source cannot
    /// quietly convert one resource into another.
    private boolean claim(TypedScalarContainer<?> typed, String resourceType) {
        if (resourceType == null) return false;

        var current = typed.getResourceType();
        if (current != null) {
            return resourceType.equals(current);
        }

        // The container's own parameter is String for every typed resource in the mod; this is
        // the one place that has to state it, because the wildcard hides it.
        @SuppressWarnings("unchecked")
        var stringTyped = (TypedScalarContainer<String>) typed;
        stringTyped.setResourceType(resourceType);

        return true;
    }

    /// Whichever scalar container this block carries, across every registered resource type.
    private ScalarContainer containerOf(ArchetypeChunk<ChunkStore> chunk, int index) {
        for (var blockType : HytechCoreModule.get().getBlockComponents()) {
            LogisticBlockComponent<?> component = chunk.getComponent(index, blockType);
            if (component == null) continue;

            if (component.getContainer() instanceof ScalarContainer scalar) {
                return scalar;
            }
        }

        return null;
    }
}
