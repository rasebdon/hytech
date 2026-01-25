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
import com.rasebdon.hytech.core.networks.LogisticNetwork;
import com.rasebdon.hytech.core.systems.LogisticTransferTarget;
import com.rasebdon.hytech.energy.util.EnergyUtils;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

public abstract class LogisticPipeComponent<TContainer extends ILogisticContainer>
        extends LogisticContainerComponent<TContainer> {

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
    private static final Vector3d UP_AABB_MIN =
            new Vector3d(-0.0575, 0.3175, -0.058);
    private static final Vector3d UP_AABB_MAX =
            new Vector3d(0.058, 0.495, 0.058);
    private static final EnumMap<BlockFace, PipeFaceRenderData> PIPE_FACE_DATA;

    static {
        PIPE_FACE_DATA = new EnumMap<>(BlockFace.class);

        PIPE_FACE_DATA.put(BlockFace.Up,
                new PipeFaceRenderData(new Vector3d(0.5, 0.0, 0.5), new Vector3f()));
        PIPE_FACE_DATA.put(BlockFace.Down,
                new PipeFaceRenderData(new Vector3d(0.5, 1.0, 0.5),
                        new Vector3f(0f, 0f, (float) Math.toRadians(180))));
        PIPE_FACE_DATA.put(BlockFace.East,
                new PipeFaceRenderData(new Vector3d(0.0, 0.5, 0.5),
                        new Vector3f(0f, 0f, (float) Math.toRadians(-90))));
        PIPE_FACE_DATA.put(BlockFace.West,
                new PipeFaceRenderData(new Vector3d(1.0, 0.5, 0.5),
                        new Vector3f(0f, 0f, (float) Math.toRadians(90))));
        PIPE_FACE_DATA.put(BlockFace.North,
                new PipeFaceRenderData(new Vector3d(0.5, 0.5, 1.0),
                        new Vector3f((float) Math.toRadians(-90), 0f, 0f)));
        PIPE_FACE_DATA.put(BlockFace.South,
                new PipeFaceRenderData(new Vector3d(0.5, 0.5, 0.0),
                        new Vector3f((float) Math.toRadians(90), 0f, 0f)));
    }

    private final List<Ref<EntityStore>> pipeConnectionModels = new ArrayList<>();
    protected LogisticNetwork<TContainer> network;
    private String normalConnectionModelAsset = "Pipe_Normal";
    private String pushConnectionModelAsset = "Pipe_Push";
    private String pullConnectionModelAsset = "Pipe_Pull";

    private static void buildFaceAabb(
            BlockFace face,
            Vector3d outMin,
            Vector3d outMax
    ) {
        // Canonical UP box
        double minX = UP_AABB_MIN.x;
        double minY = UP_AABB_MIN.y;
        double minZ = UP_AABB_MIN.z;
        double maxX = UP_AABB_MAX.x;
        double maxY = UP_AABB_MAX.y;
        double maxZ = UP_AABB_MAX.z;

        switch (face) {
            case Up -> {
                outMin.assign(minX, minY, minZ);
                outMax.assign(maxX, maxY, maxZ);
            }

            case Down -> {
                outMin.assign(minX, -maxY, minZ);
                outMax.assign(maxX, -minY, maxZ);
            }

            case South -> {
                outMin.assign(minX, minZ, minY);
                outMax.assign(maxX, maxZ, maxY);
            }

            case North -> {
                outMin.assign(minX, minZ, -maxY);
                outMax.assign(maxX, maxZ, -minY);
            }

            case East -> {
                outMin.assign(minY, minZ, minX);
                outMax.assign(maxY, maxZ, maxX);
            }

            case West -> {
                outMin.assign(-maxY, minZ, minX);
                outMax.assign(-minY, maxZ, maxX);
            }

            default -> throw new IllegalStateException(face.name());
        }
    }

    public LogisticNetwork<TContainer> getNetwork() {
        return network;
    }

    public void setNetwork(LogisticNetwork<TContainer> network) {
        this.network = network;
    }

    public void clearPipeConnections(@Nonnull World world) {
        world.execute(() -> clearPipeConnectionModels(world.getEntityStore().getStore()));
    }

    /* ----------------------------- Lifecycle ----------------------------- */

    private void clearPipeConnectionModels(@Nonnull Store<EntityStore> store) {
        for (var ref : pipeConnectionModels) {
            if (ref != null && ref.isValid()) {
                store.removeEntity(ref, RemoveReason.REMOVE);
            }
        }
        pipeConnectionModels.clear();
    }

    public void reloadPipeConnections(@Nonnull World world, Ref<ChunkStore> pipeRef) {
        world.execute(() -> {
            var entityStore = world.getEntityStore().getStore();
            clearPipeConnectionModels(entityStore);

            var asset = ModelAsset.getAssetMap().getAsset(normalConnectionModelAsset);
            if (asset == null) return;

            var transform = EnergyUtils.getBlockTransform(pipeRef, pipeRef.getStore());
            if (transform == null) return;

            var basePos = transform.worldPos().toVector3d();
            for (BlockFace face : getConnectedFaces()) {
                var render = PIPE_FACE_DATA.get(face);
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

        assert model.getBoundingBox() != null;
        Vector3d min = new Vector3d();
        Vector3d max = new Vector3d();

        buildFaceAabb(face, min, max);
        min.scale(2);
        max.scale(2);
        model.getBoundingBox().setMinMax(min, max);

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

    /* ----------------------------- Helpers ----------------------------- */

    public record PipeFaceRenderData(Vector3d offset, Vector3f rotation) {
    }
}
