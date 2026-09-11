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

/// Keeps creative source blocks full and creative void blocks empty, for any resource type with
/// a scalar container. Slot-based containers (items) are skipped -- a chest already fills that role.
public final class CreativeSourceSystem extends TickingSystem<ChunkStore> {

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

            // Release the claim too, or a void keeps rejecting every other resource after the first.
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

    /// Points a typed container at the configured resource; false if it already holds another.
    private boolean claim(TypedScalarContainer<?> typed, String resourceType) {
        if (resourceType == null) return false;

        var current = typed.getResourceType();
        if (current != null) {
            return resourceType.equals(current);
        }

        // Every typed resource in the mod parameterizes on String; the wildcard hides that here.
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
