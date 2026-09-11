package at.rasebdon.hytech.core.systems;

import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.util.HytechUtil;
import at.rasebdon.hytech.core.util.PipeConnectionMask;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;
import org.jspecify.annotations.NonNull;

import java.util.*;

/// Renders pipe connections by swapping the block to the state variant matching its connection
/// mask. Writes with settings 198 so `WorldChunk.setBlock` leaves the block entity alone.
///
/// Single shared instance across every resource module, since `ComponentRegistry` allows one
/// instance per system class.
public final class PipeConnectionStateSystem extends TickingSystem<ChunkStore> {

    private static final float UPDATE_INTERVAL_SECONDS = 0.25f;

    private final Set<ComponentType<ChunkStore, ? extends LogisticPipeComponent<?>>> pipeTypes = new HashSet<>();

    /// Marker entities per pipe, tracked here rather than on the component so the
    /// component stays pure data.
    private final Map<LogisticPipeComponent<?>, List<Ref<EntityStore>>> faceMarkers = new HashMap<>();

    private float updateTime;

    public PipeConnectionStateSystem() {
        this.updateTime = 0f;
    }

    public void registerPipeType(@NonNull ComponentType<ChunkStore, ? extends LogisticPipeComponent<?>> type) {
        this.pipeTypes.add(type);
    }

    Set<ComponentType<ChunkStore, ? extends LogisticPipeComponent<?>>> getPipeTypes() {
        return this.pipeTypes;
    }

    /// Hands a pipe's markers to the caller so they can be torn down when the block goes.
    List<Ref<EntityStore>> takeMarkers(@NonNull LogisticPipeComponent<?> pipe) {
        return this.faceMarkers.remove(pipe);
    }

    @Override
    public void tick(float dt, int systemIndex, @NonNull Store<ChunkStore> store) {
        if (this.updateTime < UPDATE_INTERVAL_SECONDS) {
            this.updateTime += dt;
            return;
        }

        this.updateTime = 0f;

        for (var pipeType : pipeTypes) {
            store.forEachChunk(pipeType, (chunk, _) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    updatePipe(store, pipeType, chunk, i);
                }
            });
        }
    }

    // Deprecated with no replacement offered; this is still the call that does the job.
    @SuppressWarnings("deprecation")
    private void updatePipe(
            Store<ChunkStore> store,
            ComponentType<ChunkStore, ? extends LogisticPipeComponent<?>> pipeType,
            ArchetypeChunk<ChunkStore> archetypeChunk,
            int index) {
        var pipe = archetypeChunk.getComponent(index, pipeType);
        if (pipe == null || !pipe.needsRenderReload()) return;

        var blockRef = archetypeChunk.getReferenceTo(index);

        var located = HytechUtil.locate(store, blockRef);
        if (located == null) return;

        var chunk = located.chunk();
        var blockPosition = located.localPos();

        var blockType = chunk.getBlockType(blockPosition);
        if (blockType == null) return;

        var transform = HytechUtil.getBlockTransform(blockRef, store);
        if (transform == null) return;

        applyState(store, chunk, pipe, blockPosition, blockType, transform.worldPos());

        // Clear the flag only once the write went through, so an unresolvable pipe retries.
        pipe.resetNeedsRenderReload();
    }

    @SuppressWarnings("deprecation")
    private <TContainer> void applyState(
            Store<ChunkStore> store,
            WorldChunk chunk,
            LogisticPipeComponent<TContainer> pipe,
            Vector3i blockPosition,
            com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType blockType,
            Vector3i worldPos) {

        // Arms on configured faces are drawn by marker entities, left out of the block model.
        var mask = PipeConnectionMask.renderMaskOf(pipe);

        chunk.setBlockInteractionState(
                blockPosition,
                blockType,
                PipeConnectionMask.stateName(mask));

        updateFaceMarkers(store, pipe, worldPos);
    }

    private <TContainer> void updateFaceMarkers(
            Store<ChunkStore> store,
            LogisticPipeComponent<TContainer> pipe,
            Vector3i worldPos) {

        var world = store.getExternalData().getWorld();

        var entityStore = world.getEntityStore().getStore();
        var markers = faceMarkers.computeIfAbsent(pipe, _ -> new ArrayList<>());
        var position = new Vector3i(worldPos);

        // Entity mutation has to run on the world thread.
        world.execute(() -> {
            PipeFaceMarkers.despawn(markers, entityStore);
            markers.addAll(PipeFaceMarkers.spawn(pipe, position, entityStore));
        });
    }
}
