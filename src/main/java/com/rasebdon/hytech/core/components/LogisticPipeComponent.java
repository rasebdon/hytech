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
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.rasebdon.hytech.core.face.BlockFaceConfig;
import com.rasebdon.hytech.core.networks.LogisticNetwork;
import com.rasebdon.hytech.core.systems.LogisticTransferTarget;
import com.rasebdon.hytech.energy.util.EnergyUtils;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

public abstract class LogisticPipeComponent<
        TNetwork extends LogisticNetwork<TNetwork, TPipe, TContainer>,
        TPipe extends LogisticPipeComponent<TNetwork, TPipe, TContainer>,
        TContainer
        > extends LogisticContainerComponent<TContainer> {

    @SuppressWarnings("rawtypes")
    public static final BuilderCodec<LogisticPipeComponent> CODEC =
            BuilderCodec.abstractBuilder(LogisticPipeComponent.class, LogisticContainerComponent.CODEC)
                    .append(new KeyedCodec<>("NormalConnectionModelAsset", Codec.STRING),
                            (c, v) -> c.normalConnectionModelAsset = v,
                            c -> c.normalConnectionModelAsset).add()
                    .append(new KeyedCodec<>("PushConnectionModelAsset", Codec.STRING),
                            (c, v) -> c.pushConnectionModelAsset = v,
                            c -> c.pushConnectionModelAsset).add()
                    .append(new KeyedCodec<>("PullConnectionModelAsset", Codec.STRING),
                            (c, v) -> c.pullConnectionModelAsset = v,
                            c -> c.pullConnectionModelAsset).add()
                    .build();
    private final List<Ref<EntityStore>> pipeConnectionModels = new ArrayList<>();
    protected TNetwork network;
    private String normalConnectionModelAsset = "Pipe_Normal";
    private String pushConnectionModelAsset = "Pipe_Push";
    private String pullConnectionModelAsset = "Pipe_Pull";

    public LogisticPipeComponent(BlockFaceConfig blockFaceConfig) {
        super(blockFaceConfig);
    }

    public TNetwork getNetwork() {
        return network;
    }

    public void assignNetwork(TNetwork network) {
        this.network = network;
    }

    public void clearPipeConnections(@Nonnull World world) {
        world.execute(() -> clearPipeConnectionModels(world.getEntityStore().getStore()));
    }

    private void clearPipeConnectionModels(@Nonnull Store<EntityStore> store) {
        for (var ref : pipeConnectionModels) {
            if (ref != null && ref.isValid()) {
                store.removeEntity(ref, RemoveReason.REMOVE);
            }
        }
        pipeConnectionModels.clear();
    }

    public void reloadPipeConnectionModels(@Nonnull World world, Ref<ChunkStore> pipeRef) {
        world.execute(() -> {
            var entityStore = world.getEntityStore().getStore();
            clearPipeConnectionModels(entityStore);

            var asset = ModelAsset.getAssetMap().getAsset(normalConnectionModelAsset);
            if (asset == null) return;

            var transform = EnergyUtils.getBlockTransform(pipeRef, pipeRef.getStore());
            if (transform == null) return;

            var basePos = transform.worldPos().toVector3d();
            for (BlockFace face : getConnectedFaces()) {
                var render = PipeConnectionHelper.getRenderData(face);
                if (render == null) continue;

                addPipeConnectionModel(
                        entityStore,
                        Model.createStaticScaledModel(asset, 2),
                        basePos.clone().add(render.offset()),
                        render.rotation(),
                        face
                );
            }
        });
    }

    private void addPipeConnectionModel(
            @Nonnull Store<EntityStore> store,
            Model model,
            Vector3d worldPosition,
            Vector3f rotation,
            BlockFace face
    ) {
        Holder<EntityStore> holder = store.getRegistry().newHolder();

        holder.addComponent(TransformComponent.getComponentType(),
                new TransformComponent(worldPosition, rotation));

        PipeConnectionHelper.recalculateBoundingBoxForFace(model, face);

        holder.addComponent(ModelComponent.getComponentType(),
                new ModelComponent(model));
        holder.addComponent(NetworkId.getComponentType(),
                new NetworkId(store.getExternalData().takeNextNetworkId()));
        holder.addComponent(Nameplate.getComponentType(), new Nameplate(face.name()));
        holder.ensureComponent(UUIDComponent.getComponentType());

        pipeConnectionModels.add(store.addEntity(holder, AddReason.SPAWN));
    }

    protected EnumSet<BlockFace> getConnectedFaces() {
        return transferTargets.values().stream()
                .map(LogisticTransferTarget::from)
                .filter(f -> f != BlockFace.None)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(BlockFace.class)));
    }
}
