package com.rasebdon.hytech.core.systems;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.event.IEventRegistry;
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
import com.rasebdon.hytech.core.components.LogisticPipeComponent;
import com.rasebdon.hytech.core.events.LogisticContainerChangedEvent;
import com.rasebdon.hytech.core.util.HytechUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public abstract class PipeRenderSystem<TContainer> {

    public PipeRenderSystem(IEventRegistry eventRegistry,
                            Class<? extends LogisticContainerChangedEvent<TContainer>> containerChangedEventClass) {
        eventRegistry.register(containerChangedEventClass, this::onContainerChanged);
    }

    public void onContainerChanged(LogisticContainerChangedEvent<TContainer> event) {
        if (!(event.getComponent() instanceof LogisticPipeComponent<TContainer> pipeComponent)) {
            return;
        }

        var world = event.getStore().getExternalData().getWorld();
        var entityStore = world.getEntityStore().getStore();

        world.execute(() -> {
            if (event.isAdded() || event.isChanged()) {
                redrawPipeConnectionModels(event.getBlockRef(), pipeComponent, entityStore);

            } else if (event.isRemoved()) {
                removePipeConnectionModels(pipeComponent.getModelRefs(), entityStore);
            }
        });
    }

    private void removePipeConnectionModels(
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

    private void redrawPipeConnectionModels(
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
                    render.rotation(), face);

            modelRefList.add(modelRef);
        }
    }

    private Ref<EntityStore> addPipeConnectionModel(
            @Nonnull Store<EntityStore> store,
            Model model,
            Vector3d worldPosition,
            Vector3f rotation,
            BlockFace face
    ) {
        Holder<EntityStore> holder = store.getRegistry().newHolder();

        holder.addComponent(TransformComponent.getComponentType(),
                new TransformComponent(worldPosition, rotation));

        PipeRenderHelper.recalculateBoundingBoxForFace(model, face);

        holder.addComponent(ModelComponent.getComponentType(),
                new ModelComponent(model));
        holder.addComponent(NetworkId.getComponentType(),
                new NetworkId(store.getExternalData().takeNextNetworkId()));
        holder.ensureComponent(UUIDComponent.getComponentType());

        return store.addEntity(holder, AddReason.SPAWN);
    }
}
