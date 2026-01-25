package com.rasebdon.hytech.core.components;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.rasebdon.hytech.core.networks.LogisticNetwork;
import com.rasebdon.hytech.energy.util.EnergyUtils;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public abstract class LogisticPipeComponent<TContainer extends ILogisticContainer> extends LogisticContainerComponent<TContainer> {

    private final List<Ref<EntityStore>> pipeConnectionModels;
    protected LogisticNetwork<TContainer> network;

    protected LogisticPipeComponent() {
        super();
        this.pipeConnectionModels = new ArrayList<>();
    }

    public LogisticNetwork<TContainer> getNetwork() {
        return network;
    }

    public void setNetwork(LogisticNetwork<TContainer> network) {
        this.network = network;
    }

    public void reloadPipeConnectionModels(@Nonnull World world, Ref<ChunkStore> pipeRef) {
        world.execute(() -> {
            var entityStore = world.getEntityStore().getStore();
            clearPipeConnectionModels(entityStore);

            // Get models depending on side
            var assets = ModelAsset.getAssetMap();

            var pipeEnergyEast = assets.getAsset("Pipe_Energy_East");
            assert pipeEnergyEast != null;

            var transform = EnergyUtils.getBlockTransform(pipeRef, pipeRef.getStore());
            assert transform != null;

            var position = transform.worldPos().toVector3d();
            var rotation = new Vector3f(
                    transform.rotation().pitch().getDegrees(),
                    transform.rotation().yaw().getDegrees(),
                    transform.rotation().roll().getDegrees()
            );

            var model = Model.createStaticScaledModel(pipeEnergyEast, 1);
            addPipeConnectionModels(entityStore, model, position, rotation);
        });
    }

    private void addPipeConnectionModels(@Nonnull Store<EntityStore> store,
                                         Model model,
                                         Vector3d position,
                                         Vector3f rotation) {
        assert model.getBoundingBox() != null;

        Holder<EntityStore> holder = store.getRegistry().newHolder();

        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(position, rotation));
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
        holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(model.getBoundingBox()));

        holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));
        holder.addComponent(Interactions.getComponentType(), new Interactions());

        holder.ensureComponent(UUIDComponent.getComponentType());
        holder.ensureComponent(Interactable.getComponentType());
//        holder.addComponent(EntityStore.REGISTRY.getNonSerializedComponentType(), NonSerialized.get());

        // TODO : Add LogisticPipeConnectionComponent with reference to this component so that we can reload connections
        // when the connection is clicked with a wrench

        var entity = store.addEntity(holder, AddReason.SPAWN);
        pipeConnectionModels.add(entity);
    }

    private void clearPipeConnectionModels(@Nonnull Store<EntityStore> store) {
        for (var pipePart : pipeConnectionModels) {
            if (pipePart != null && pipePart.isValid()) {
                store.removeEntity(pipePart, RemoveReason.REMOVE);
            }
        }

        pipeConnectionModels.clear();
    }
}
