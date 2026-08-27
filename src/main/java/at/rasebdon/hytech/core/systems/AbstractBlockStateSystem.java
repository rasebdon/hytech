package at.rasebdon.hytech.core.systems;

import at.rasebdon.hytech.core.util.HytechUtil;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nullable;

/// Drives a block's visual state from one of its components.
///
/// The resolve-and-write plumbing -- component to `BlockStateInfo` to `WorldChunk` to
/// `setBlockInteractionState` -- is the same whether the state comes from a charge level or a
/// burn flag, so only [#resolveState] differs per use.
///
/// Subclassed rather than parameterised with a resolver function because `ComponentRegistry`
/// keys systems by class: two instances of one generic class would collide, exactly as they
/// do for [PipeConnectionStateSystem].
///
/// `setBlockInteractionState` writes with settings 198, whose bit 2 makes `WorldChunk.setBlock`
/// skip block-entity recreation -- so the block's components survive the swap.
public abstract class AbstractBlockStateSystem<TComponent extends Component<ChunkStore>>
        extends TickingSystem<ChunkStore> {

    private final ComponentType<ChunkStore, TComponent> componentType;
    private final float updateIntervalSeconds;
    private float updateTime;

    protected AbstractBlockStateSystem(
            ComponentType<ChunkStore, TComponent> componentType,
            float updateIntervalSeconds) {
        this.componentType = componentType;
        this.updateIntervalSeconds = updateIntervalSeconds;
        this.updateTime = 0f;
    }

    /// The state this block should be showing, or null to leave it alone.
    ///
    /// Returning null means "no opinion", not "reset" -- there is no way to clear a state, so
    /// a system that needs an off position must name it (the burner has an explicit `Idle`
    /// state for this, as the pipes have a mask for every topology).
    @Nullable
    protected abstract String resolveState(@NonNull TComponent component);

    @Override
    public void tick(float dt, int systemIndex, @NonNull Store<ChunkStore> store) {
        if (this.updateTime < this.updateIntervalSeconds) {
            this.updateTime += dt;
            return;
        }

        this.updateTime = 0f;

        store.forEachChunk(this.componentType, (chunk, _) -> {
            for (int i = 0; i < chunk.size(); i++) {
                updateBlock(store, chunk, i);
            }
        });
    }

    private void updateBlock(
            Store<ChunkStore> store,
            ArchetypeChunk<ChunkStore> archetypeChunk,
            int index) {

        var component = archetypeChunk.getComponent(index, this.componentType);
        if (component == null) return;

        var state = resolveState(component);
        if (state == null) return;

        var blockRef = archetypeChunk.getReferenceTo(index);

        var located = HytechUtil.locate(store, blockRef);
        if (located == null) return;

        var chunk = located.chunk();
        var blockPosition = located.localPos();

        var blockType = chunk.getBlockType(blockPosition);
        if (blockType == null) return;

        chunk.setBlockInteractionState(blockPosition, blockType, state);
    }
}
