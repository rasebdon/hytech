package at.rasebdon.hytech.items.systems;

import at.rasebdon.hytech.core.util.HytechUtil;
import at.rasebdon.hytech.items.components.ItemPipeComponent;
import at.rasebdon.hytech.items.utils.ItemEjector;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.WeakHashMap;

/// Drops items stuck in a pipe (destination broken/filled mid-run, or an old-world pipe loaded
/// under previous rules) so a run never becomes permanent storage. Ejects at the pipe's own
/// position since the buffer records no destination.
public class ItemPipeEjectSystem extends EntityTickingSystem<ChunkStore> {

    // A pass empties a pipe within a second, so 3 idle passes means blocked, not just busy.
    private static final float STUCK_SECONDS = 3f;

    private final ComponentType<ChunkStore, ItemPipeComponent> pipeType;
    private final Archetype<ChunkStore> archetype;

    // Weakly keyed so an unloaded pipe's entry disappears with the component.
    private final Map<ItemPipeComponent, Float> blockedFor = new WeakHashMap<>();

    public ItemPipeEjectSystem(ComponentType<ChunkStore, ItemPipeComponent> pipeType) {
        this.pipeType = pipeType;
        this.archetype = Archetype.of(pipeType);
    }

    @Override
    public void tick(float dt,
                     int index,
                     @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
                     @Nonnull Store<ChunkStore> store,
                     @Nonnull CommandBuffer<ChunkStore> commandBuffer) {

        var pipe = archetypeChunk.getComponent(index, pipeType);
        if (pipe == null) return;

        var container = pipe.getItemContainer();
        if (container == null || pipe.isEmpty()) {
            blockedFor.remove(pipe);
            return;
        }

        float held = blockedFor.getOrDefault(pipe, 0f) + dt;
        if (held < STUCK_SECONDS) {
            blockedFor.put(pipe, held);
            return;
        }

        blockedFor.remove(pipe);

        var blockRef = archetypeChunk.getReferenceTo(index);
        var transform = HytechUtil.getBlockTransform(blockRef, store);
        if (transform == null) return;

        ItemEjector.ejectAt(container, store, transform.worldPos());
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return archetype;
    }
}
