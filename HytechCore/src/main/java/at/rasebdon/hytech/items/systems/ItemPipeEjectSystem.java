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

/// Spits out items that have stopped moving, so a pipe run is transit and never storage.
///
/// [ItemTransferSystem] already refuses to pull unless the same pass can hand the items on,
/// which keeps them out of the pipes in the first place. That covers everything the pull can
/// see -- but not what happens afterwards: the destination can be broken, filled, or switched
/// off while a stack is mid-run, and a pipe loaded from an old world may already hold items
/// that were pulled under the previous rules. Those would sit in the network forever.
///
/// A stack is ejected at the pipe's own position rather than at the destination's, because the
/// pipe is where the items physically are and the buffer records no destination -- once the
/// target block is gone there is nothing left to aim at. In practice the two are adjacent.
public class ItemPipeEjectSystem extends EntityTickingSystem<ChunkStore> {

    /// How long items may sit in one pipe before they are ejected.
    ///
    /// Item passes run once a second and a pass hands on what it pulled, so a pipe is empty
    /// again by the end of the pass that filled it. Three passes of no progress therefore
    /// means the run is blocked, not busy -- and the margin keeps a flowing stream, which
    /// this system may well observe mid-pass, from being dumped on the floor.
    private static final float STUCK_SECONDS = 3f;

    private final ComponentType<ChunkStore, ItemPipeComponent> pipeType;
    private final Archetype<ChunkStore> archetype;

    /// Seconds each pipe has held items without emptying. Weakly keyed so an unloaded pipe's
    /// entry goes away with the component, and deliberately not persisted: after a restart
    /// the clock starts again and a still-blocked pipe simply ejects three seconds later.
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
