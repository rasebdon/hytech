package com.rasebdon.hytech.core.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.rasebdon.hytech.core.face.BlockFaceConfig;
import com.rasebdon.hytech.core.face.BlockFaceConfigType;
import com.rasebdon.hytech.core.networks.LogisticNetwork;
import com.rasebdon.hytech.core.systems.LogisticTransferTarget;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class LogisticPipeComponent<TContainer> extends LogisticContainerComponent<TContainer> {

    @SuppressWarnings("rawtypes")
    public static final BuilderCodec<LogisticPipeComponent> CODEC =
            BuilderCodec.abstractBuilder(LogisticPipeComponent.class, LogisticContainerComponent.CODEC)
                    .append(new KeyedCodec<>("NormalConnectionModelAsset", Codec.STRING),
                            (c, v) -> c.setConnectionModelAssetName(BlockFaceConfigType.BOTH, v),
                            c -> c.getModelAssetName(BlockFaceConfigType.BOTH)).add()
                    .append(new KeyedCodec<>("PushConnectionModelAsset", Codec.STRING),
                            (c, v) -> c.setConnectionModelAssetName(BlockFaceConfigType.OUTPUT, v),
                            c -> c.getModelAssetName(BlockFaceConfigType.OUTPUT)).add()
                    .append(new KeyedCodec<>("PullConnectionModelAsset", Codec.STRING),
                            (c, v) -> c.setConnectionModelAssetName(BlockFaceConfigType.INPUT, v),
                            c -> c.getModelAssetName(BlockFaceConfigType.INPUT)).add()
                    .build();
    // Render Vars
    protected final Map<BlockFaceConfigType, String> connectionModelAssetNames = new HashMap<>();
    @Nullable
    protected LogisticNetwork<TContainer> network;
    private final List<Ref<EntityStore>> modelRefs = new ArrayList<>();

    public LogisticPipeComponent(BlockFaceConfig blockFaceConfig, Map<BlockFaceConfigType, String> connectionModelAssetNames) {
        super(blockFaceConfig);
        this.connectionModelAssetNames.putAll(connectionModelAssetNames);
    }

    @Nullable
    public LogisticNetwork<TContainer> getNetwork() {
        return network;
    }

    public void assignNetwork(LogisticNetwork<TContainer> network) {
        this.network = network;
    }

    @Override
    public void tryAddTransferTarget(ILogisticContainerHolder<TContainer> target, BlockFace from, BlockFace to) {
        super.tryAddTransferTarget(target, from, to);
        tryRebuildNetworkOnTargetAdd(target);
    }

    @Override
    public void removeTransferTarget(ILogisticContainerHolder<TContainer> target) {
        super.removeTransferTarget(target);
        tryRebuildNetworkOnTargetAdd(target);
    }

    public boolean canPullFrom(BlockFace face) {
        return this.currentBlockFaceConfig.getFaceConfigType(face) == BlockFaceConfigType.INPUT;
    }

    public boolean canPushTo(BlockFace face) {
        return this.currentBlockFaceConfig.canExtractFromFace(face);
    }

    private void tryRebuildNetworkOnTargetAdd(ILogisticContainerHolder<TContainer> target) {
        if (this.network != null && target instanceof LogisticBlockComponent<TContainer>) {
            this.network.rebuildTargets();
        }
    }

    private String getModelAssetName(BlockFaceConfigType faceConfigType) {
        return this.connectionModelAssetNames.get(faceConfigType);
    }

    private void setConnectionModelAssetName(BlockFaceConfigType configType, String modelAssetName) {
        connectionModelAssetNames.put(configType, modelAssetName);
    }

    @Nullable
    public ModelAsset getConnectionModelAsset(LogisticTransferTarget<TContainer> target) {
        if (target.target() instanceof LogisticPipeComponent<TContainer>) {
            return ModelAsset.getAssetMap().getAsset(connectionModelAssetNames.get(BlockFaceConfigType.BOTH));
        }

        var blockFaceConfigType = currentBlockFaceConfig.getFaceConfigType(target.from());
        return ModelAsset.getAssetMap().getAsset(connectionModelAssetNames.get(blockFaceConfigType));
    }

    public List<Ref<EntityStore>> getModelRefs() {
        return this.modelRefs;
    }
}
