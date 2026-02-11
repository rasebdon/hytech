package at.rasebdon.hytech.core.systems;

import at.rasebdon.hytech.core.components.LogisticContainerComponent;
import at.rasebdon.hytech.core.components.LogisticEntityProxyComponent;
import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.util.HytechUtil;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class PipeRenderModule {

    public static <TContainer> void registerPipe(
            @Nonnull ComponentRegistryProxy<ChunkStore> chunkStoreRegistry,
            @Nonnull ComponentType<ChunkStore, ? extends LogisticPipeComponent<TContainer>> pipeType) {
        chunkStoreRegistry.registerSystem(new PipeRenderTickingSystem<>(pipeType));
        chunkStoreRegistry.registerSystem(new PipeRenderRefSystem<>(pipeType));
    }

    private static <TContainer> void redrawPipe(LogisticPipeComponent<TContainer> pipeComponent,
                                                Ref<ChunkStore> blockRef,
                                                Store<ChunkStore> chunkStore) {

        var world = chunkStore.getExternalData().getWorld();
        var entityStore = world.getEntityStore().getStore();

        world.execute(() -> redrawPipeConnectionModels(blockRef, pipeComponent, entityStore));
    }

    private static void removePipeConnectionModels(
            @Nonnull List<Ref<EntityStore>> modelRefList,
            @Nonnull Store<EntityStore> store) {
        var toRemove = new ArrayList<Ref<EntityStore>>();
        for (var ref : modelRefList) {
            if (ref != null && ref.isValid()) {
                store.removeEntity(ref, RemoveReason.REMOVE);
                toRemove.add(ref);
            }
        }
        modelRefList.removeAll(toRemove);
    }

    private static <TContainer> void redrawPipeConnectionModels(
            @Nonnull Ref<ChunkStore> pipeRef,
            @Nonnull LogisticPipeComponent<TContainer> pipeComponent,
            @Nonnull Store<EntityStore> entityStore) {
        var modelRefList = pipeComponent.getModelRefs();

        removePipeConnectionModels(modelRefList, entityStore);

        var transform = HytechUtil.getBlockTransform(pipeRef, pipeRef.getStore());
        if (transform == null) return;

        var basePos = transform.worldPos().toVector3d();
        for (var neighbor : pipeComponent.getNeighbors()) {
            if (!pipeComponent.isConnectedTo(neighbor)) continue;

            var face = pipeComponent.getNeighborFace(neighbor);

            var render = PipeRenderHelper.getRenderData(face);
            if (render == null) continue;

            var modelAsset = pipeComponent.getConnectionModelAsset(face);
            if (modelAsset == null) continue;

            var model = Model.createStaticScaledModel(modelAsset, 2);

            var modelRef = addPipeConnectionModel(
                    entityStore,
                    model,
                    basePos.clone().add(render.offset()),
                    render.rotation(),
                    face,
                    pipeComponent);

            modelRefList.add(modelRef);
        }

        pipeComponent.resetNeedsRenderReload();
    }

    private static Ref<EntityStore> addPipeConnectionModel(
            @Nonnull Store<EntityStore> store,
            Model model,
            Vector3d worldPosition,
            Vector3f rotation,
            BlockFace face,
            LogisticContainerComponent<?> containerComponent
    ) {
        Holder<EntityStore> holder = store.getRegistry().newHolder();

        holder.addComponent(TransformComponent.getComponentType(),
                new TransformComponent(worldPosition, rotation));

        PipeRenderHelper.recalculateBoundingBoxForFace(model, face);

        holder.addComponent(ModelComponent.getComponentType(),
                new ModelComponent(model));
        holder.addComponent(NetworkId.getComponentType(),
                new NetworkId(store.getExternalData().takeNextNetworkId()));
        holder.addComponent(
                LogisticEntityProxyComponent.getComponentType(),
                new LogisticEntityProxyComponent(containerComponent, face)
        );

        holder.ensureComponent(UUIDComponent.getComponentType());

        return store.addEntity(holder, AddReason.SPAWN);
    }

    public static class PipeRenderTickingSystem<TContainer> extends TickingSystem<ChunkStore> {

        private final ComponentType<ChunkStore, ? extends LogisticPipeComponent<TContainer>> pipeType;

        public PipeRenderTickingSystem(ComponentType<ChunkStore, ? extends LogisticPipeComponent<TContainer>> pipeType) {
            this.pipeType = pipeType;
        }

        @Override
        public void tick(float dt, int systemIndex, @NotNull Store<ChunkStore> chunkStore) {
            chunkStore.forEachChunk(
                    pipeType,
                    this::redrawPipeConnectionsChunk
            );

        }

        private void redrawPipeConnectionsChunk(ArchetypeChunk<ChunkStore> chunkStoreArchetypeChunk,
                                                CommandBuffer<ChunkStore> commandBuffer) {
            for (int i = 0; i < chunkStoreArchetypeChunk.size(); i++) {
                var pipeComponent = chunkStoreArchetypeChunk.getComponent(i, pipeType);

                if (pipeComponent != null && pipeComponent.needsRenderReload()) {
                    redrawPipe(pipeComponent, chunkStoreArchetypeChunk.getReferenceTo(i), commandBuffer.getStore());
                }
            }
        }
    }

    public static class PipeRenderRefSystem<TContainer> extends RefSystem<ChunkStore> {

        private final ComponentType<ChunkStore, ? extends LogisticPipeComponent<TContainer>> pipeType;

        public PipeRenderRefSystem(ComponentType<ChunkStore, ? extends LogisticPipeComponent<TContainer>> pipeType) {
            this.pipeType = pipeType;
        }

        @Override
        public void onEntityAdded(@NotNull Ref<ChunkStore> ref,
                                  @NotNull AddReason addReason,
                                  @NotNull Store<ChunkStore> store,
                                  @NotNull CommandBuffer<ChunkStore> commandBuffer) {
            // Handled by ticking system
        }

        @Override
        public void onEntityRemove(@NotNull Ref<ChunkStore> ref,
                                   @NotNull RemoveReason removeReason,
                                   @NotNull Store<ChunkStore> store,
                                   @NotNull CommandBuffer<ChunkStore> commandBuffer) {
            var pipeComponent = store.getComponent(ref, pipeType);

            if (pipeComponent != null) {
                var world = store.getExternalData().getWorld();
                var entityStore = world.getEntityStore().getStore();

                world.execute(() -> removePipeConnectionModels(pipeComponent.getModelRefs(), entityStore));
            }
        }

        @Override
        public @Nullable Query<ChunkStore> getQuery() {
            return pipeType;
        }
    }
}
