package com.rasebdon.hytech.core.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.rasebdon.hytech.core.networks.LogisticNetwork;
import com.rasebdon.hytech.core.systems.LogisticTransferTarget;
import com.rasebdon.hytech.energy.util.EnergyUtils;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

public abstract class LogisticPipeComponent<TContainer extends ILogisticContainer> extends LogisticContainerComponent<TContainer> {

    private final List<Ref<EntityStore>> pipeConnectionModels;
    protected LogisticNetwork<TContainer> network;

    @SuppressWarnings("rawtypes")
    public static final BuilderCodec<LogisticPipeComponent> CODEC =
            BuilderCodec.abstractBuilder(LogisticPipeComponent.class, LogisticContainerComponent.CODEC)
                    .append(new KeyedCodec<>("NormalConnectionModelAsset", Codec.STRING),
                            (c, v) -> c.normalConnectionModelAsset = v,
                            (c) -> c.normalConnectionModelAsset)
                    .documentation("Asset model for pipe mode 'Normal'").add()
                    .append(new KeyedCodec<>("PushConnectionModelAsset", Codec.STRING),
                            (c, v) -> c.pushConnectionModelAsset = v,
                            (c) -> c.pushConnectionModelAsset)
                    .documentation("Asset model for pipe mode 'Push'").add()
                    .append(new KeyedCodec<>("PullConnectionModelAsset", Codec.STRING),
                            (c, v) -> c.pullConnectionModelAsset = v,
                            (c) -> c.pullConnectionModelAsset)
                    .documentation("Asset model for pipe mode 'Pull'").add()
                    .build();

    private String normalConnectionModelAsset;
    private String pushConnectionModelAsset;
    private String pullConnectionModelAsset;

    protected LogisticPipeComponent() {
        super();

        this.normalConnectionModelAsset = "Pipe_Normal";
        this.pushConnectionModelAsset = "Pipe_Push";
        this.pullConnectionModelAsset = "Pipe_Pull";
        this.pipeConnectionModels = new ArrayList<>();
    }

    public LogisticNetwork<TContainer> getNetwork() {
        return network;
    }

    public void setNetwork(LogisticNetwork<TContainer> network) {
        this.network = network;
    }

    private static final EnumMap<BlockFace, PipeFaceRenderData> PIPE_FACE_DATA =
            new EnumMap<>(BlockFace.class);

    static {
        PIPE_FACE_DATA.put(BlockFace.Up,
                new PipeFaceRenderData(
                        new Vector3d(0.5, 0.0, 0.5),
                        new Vector3f(0f, 0f, 0f)));

        PIPE_FACE_DATA.put(BlockFace.Down,
                new PipeFaceRenderData(
                        new Vector3d(0.5, 1.0, 0.5),
                        new Vector3f(0f, 0f, (float) Math.toRadians(180))));

        PIPE_FACE_DATA.put(BlockFace.East,
                new PipeFaceRenderData(
                        new Vector3d(0.0, 0.5, 0.5),
                        new Vector3f(0f, 0f, (float) Math.toRadians(-90))));

        PIPE_FACE_DATA.put(BlockFace.West,
                new PipeFaceRenderData(
                        new Vector3d(1.0, 0.5, 0.5),
                        new Vector3f(0f, 0f, (float) Math.toRadians(90))));

        PIPE_FACE_DATA.put(BlockFace.North,
                new PipeFaceRenderData(
                        new Vector3d(0.5, 0.5, 1.0),
                        new Vector3f((float) Math.toRadians(-90), 0f, 0f)));

        PIPE_FACE_DATA.put(BlockFace.South,
                new PipeFaceRenderData(
                        new Vector3d(0.5, 0.5, 0.0),
                        new Vector3f((float) Math.toRadians(90), 0f, 0f)));
    }

    public void clearPipeConnections(@Nonnull World world) {
        world.execute(() -> {
            var entityStore = world.getEntityStore().getStore();
            clearPipeConnectionModels(entityStore);
        });
    }

    private void clearPipeConnectionModels(@Nonnull Store<EntityStore> store) {
        for (var pipePart : pipeConnectionModels) {
            if (pipePart != null && pipePart.isValid()) {
                store.removeEntity(pipePart, RemoveReason.REMOVE);
            }
        }

        pipeConnectionModels.clear();
    }

    public void reloadPipeConnections(@Nonnull World world, Ref<ChunkStore> pipeRef) {
        world.execute(() -> {
            var entityStore = world.getEntityStore().getStore();
            clearPipeConnectionModels(entityStore);

            var assets = ModelAsset.getAssetMap();
            var pipeConnectionModel = assets.getAsset(normalConnectionModelAsset);
            if (pipeConnectionModel == null) return;

            var transform = EnergyUtils.getBlockTransform(pipeRef, pipeRef.getStore());
            if (transform == null) return;

            var worldPosition = transform.worldPos().toVector3d();
            var model = Model.createStaticScaledModel(pipeConnectionModel, 2);

            for (BlockFace face : getConnectedFaces()) {
                PipeFaceRenderData data = PIPE_FACE_DATA.get(face);
                if (data == null) continue;

                addPipeConnectionModel(
                        entityStore,
                        model,
                        worldPosition.clone().add(data.offset()),
                        data.rotation()
                );
            }
        });
    }

    private void addPipeConnectionModel(@Nonnull Store<EntityStore> store,
                                        Model model,
                                        Vector3d position,
                                        Vector3f rotation) {
        assert model.getBoundingBox() != null;

        Holder<EntityStore> holder = store.getRegistry().newHolder();

        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(position, rotation));
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));

        // holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(model.getBoundingBox()));

        holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));
        //holder.addComponent(Interactions.getComponentType(), new Interactions());

        holder.ensureComponent(UUIDComponent.getComponentType());
        holder.ensureComponent(Interactable.getComponentType());
//        holder.addComponent(EntityStore.REGISTRY.getNonSerializedComponentType(), NonSerialized.get());

        // TODO : Add LogisticPipeConnectionComponent with reference to this component so that we can reload connections
        // when the connection is clicked with a wrench

        var entity = store.addEntity(holder, AddReason.SPAWN);
        pipeConnectionModels.add(entity);
    }

    protected EnumSet<BlockFace> getConnectedFaces() {
        return transferTargets.values().stream()
                .map(LogisticTransferTarget::from) // or `.to()` depending on your visual rule
                .filter(face -> face != BlockFace.None)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(BlockFace.class)));
    }

    public record PipeFaceRenderData(Vector3d offset, Vector3f rotation) {
    }

}
