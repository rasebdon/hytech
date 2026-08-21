package at.rasebdon.hytech.core.systems;

import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.util.HytechUtil;
import at.rasebdon.hytech.core.util.PipeConnectionMask;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;
import org.jspecify.annotations.NonNull;

import java.util.*;

/// Renders pipe connections by swapping the block to the state variant matching its
/// connection mask, the same way [at.rasebdon.hytech.energy.systems.visual.EnergyBlockStateSystem]
/// drives charge levels.
///
/// This replaces spawning a model entity per connected face. `setBlockInteractionState`
/// writes with settings 198, whose bit 2 tells `WorldChunk.setBlock` to leave the block
/// entity alone -- so the pipe's own component, its face configs and any stored contents
/// survive the swap. Cost is one palette write plus a few bytes in a batched per-section
/// packet, and nothing at all once the topology settles.
///
/// The only entities left are the push/pull markers on explicitly configured faces, which
/// this system keeps in step using the same dirty flag.
///
/// `ComponentRegistry` allows one instance per system class, so this is a single shared
/// system that every resource module registers its pipe component type into, rather than
/// one instance per module.
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

    private void updatePipe(
            Store<ChunkStore> store,
            ComponentType<ChunkStore, ? extends LogisticPipeComponent<?>> pipeType,
            ArchetypeChunk<ChunkStore> archetypeChunk,
            int index) {
        var pipe = archetypeChunk.getComponent(index, pipeType);
        if (pipe == null || !pipe.needsRenderReload()) return;

        var blockRef = archetypeChunk.getReferenceTo(index);
        var blockInfo = store.getComponent(blockRef, BlockModule.BlockStateInfo.getComponentType());
        if (blockInfo == null) return;

        var blockPosition = HytechUtil.getLocalBlockPosition(blockInfo);
        var chunk = store.getComponent(blockInfo.getChunkRef(), WorldChunk.getComponentType());
        if (chunk == null) return;

        var blockType = chunk.getBlockType(blockPosition);
        if (blockType == null) return;

        var transform = HytechUtil.getBlockTransform(blockRef, store);
        if (transform == null) return;

        applyState(store, chunk, pipe, blockPosition, blockType, transform.worldPos());

        // Only clear the flag once the write went through, so a pipe whose chunk or block
        // type was not resolvable yet is retried on the next pass.
        pipe.resetNeedsRenderReload();
    }

    /// Generic so the pipe's container type is captured once, which the mask and marker
    /// helpers both need.
    private <TContainer> void applyState(
            Store<ChunkStore> store,
            WorldChunk chunk,
            LogisticPipeComponent<TContainer> pipe,
            Vector3i blockPosition,
            com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType blockType,
            Vector3i worldPos) {

        // Arms on configured faces are drawn by marker entities, so the block model leaves
        // them out entirely.
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
