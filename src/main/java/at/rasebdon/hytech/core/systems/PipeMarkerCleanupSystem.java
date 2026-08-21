package at.rasebdon.hytech.core.systems;

import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// Tears down a pipe's push/pull markers when the block goes away.
///
/// [PipeConnectionStateSystem] is a ticking system and so never observes removal; without
/// this the marker entities would outlive their pipe. Like its companion, this is a single
/// shared instance because the registry allows one system per class.
public final class PipeMarkerCleanupSystem extends RefSystem<ChunkStore> {

    private final PipeConnectionStateSystem stateSystem;
    private Query<ChunkStore> query;

    public PipeMarkerCleanupSystem(PipeConnectionStateSystem stateSystem) {
        this.stateSystem = stateSystem;
        this.query = Query.and();
    }

    public void registerPipeType(@NotNull ComponentType<ChunkStore, ? extends LogisticPipeComponent<?>> type) {
        this.query = Query.or(this.query, type);
    }

    @Override
    public void onEntityAdded(
            @NotNull Ref<ChunkStore> ref,
            @NotNull AddReason addReason,
            @NotNull Store<ChunkStore> store,
            @NotNull CommandBuffer<ChunkStore> commandBuffer) {
        // Handled by the ticking pass.
    }

    @Override
    public void onEntityRemove(
            @NotNull Ref<ChunkStore> ref,
            @NotNull RemoveReason removeReason,
            @NotNull Store<ChunkStore> store,
            @NotNull CommandBuffer<ChunkStore> commandBuffer) {

        for (var pipeType : stateSystem.getPipeTypes()) {
            var pipe = store.getComponent(ref, pipeType);
            if (pipe == null) continue;

            var markers = stateSystem.takeMarkers(pipe);
            if (markers == null || markers.isEmpty()) continue;

            var world = store.getExternalData().getWorld();

            var entityStore = world.getEntityStore().getStore();
            world.execute(() -> PipeFaceMarkers.despawn(markers, entityStore));
        }
    }

    @Override
    public @Nullable Query<ChunkStore> getQuery() {
        return this.query;
    }
}
