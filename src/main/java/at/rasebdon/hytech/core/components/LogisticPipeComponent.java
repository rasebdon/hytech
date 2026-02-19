package at.rasebdon.hytech.core.components;

import at.rasebdon.hytech.core.networks.LogisticNetwork;
import at.rasebdon.hytech.core.transport.BlockFaceConfig;
import at.rasebdon.hytech.core.transport.BlockFaceConfigType;
import at.rasebdon.hytech.core.transport.LogisticNeighbor;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class LogisticPipeComponent<TContainer> extends LogisticComponent<TContainer> {

    @SuppressWarnings("rawtypes")
    public static final BuilderCodec<LogisticPipeComponent> CODEC =
            BuilderCodec.abstractBuilder(LogisticPipeComponent.class, LogisticComponent.CODEC)
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
    private final List<Ref<EntityStore>> modelRefs = new ArrayList<>();
    @Nullable
    protected LogisticNetwork<TContainer> network;
    private boolean needsRenderReload;

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
    public void reloadContainer() {
        super.reloadContainer();
        if (this.network != null) {
            this.network.rebuildTargets();
        }
        this.needsRenderReload = true;
    }

    private String getModelAssetName(BlockFaceConfigType faceConfigType) {
        return this.connectionModelAssetNames.get(faceConfigType);
    }

    private void setConnectionModelAssetName(BlockFaceConfigType configType, String modelAssetName) {
        connectionModelAssetNames.put(configType, modelAssetName);
    }

    @Nullable
    public ModelAsset getConnectionModelAsset(BlockFace face) {
        var neighbor = this.getNeighbor(face);

        if (neighbor == null) return null;

        if (neighbor.getLogisticContainer() instanceof LogisticPipeComponent<TContainer>) {
            return ModelAsset.getAssetMap().getAsset(connectionModelAssetNames.get(BlockFaceConfigType.BOTH));
        }

        if (this.canPullFrom(neighbor)) {
            return ModelAsset.getAssetMap().getAsset(connectionModelAssetNames.get(BlockFaceConfigType.INPUT));
        }

        if (this.canPushTo(neighbor)) {
            return ModelAsset.getAssetMap().getAsset(connectionModelAssetNames.get(BlockFaceConfigType.OUTPUT));
        }

        if (this.isConnectedTo(neighbor)) {
            return ModelAsset.getAssetMap().getAsset(connectionModelAssetNames.get(BlockFaceConfigType.BOTH));
        }

        return null;
    }

    public List<Ref<EntityStore>> getModelRefs() {
        return this.modelRefs;
    }

    public boolean canPullFrom(LogisticNeighbor<TContainer> target) {
        return hasInputTowards(target)
                && target.allowsOutputTowards(this.getContainer());
    }

    public boolean canPushTo(LogisticNeighbor<TContainer> target) {
        return hasOutputTowards(target)
                && target.allowsInputTowards(this.getContainer());
    }

    public boolean canOutputTo(LogisticNeighbor<TContainer> target) {
        return hasOutputFaceTowards(target.getContainer())
                && target.allowsInputTowards(this.getContainer());
    }

    public boolean isConnectedTo(LogisticNeighbor<TContainer> neighbor) {
        return (hasOutputTowards(neighbor) && neighbor.allowsInputTowards(this.getContainer()))
                || (hasInputTowards(neighbor) && neighbor.allowsOutputTowards(this.getContainer()));
    }

    private boolean hasInputTowards(LogisticNeighbor<TContainer> target) {
        return getFaceConfigTowards(target.getContainer()) == BlockFaceConfigType.INPUT;
    }

    private boolean hasOutputTowards(LogisticNeighbor<TContainer> target) {
        return getFaceConfigTowards(target.getContainer()) == BlockFaceConfigType.OUTPUT;
    }

    public boolean needsRenderReload() {
        return needsRenderReload;
    }

    public void resetNeedsRenderReload() {
        this.needsRenderReload = false;
    }
}
