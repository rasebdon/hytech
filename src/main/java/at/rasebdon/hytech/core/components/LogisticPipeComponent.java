package at.rasebdon.hytech.core.components;

import at.rasebdon.hytech.core.networks.LogisticNetwork;
import at.rasebdon.hytech.core.transport.BlockFaceConfig;
import at.rasebdon.hytech.core.transport.BlockFaceConfigType;
import at.rasebdon.hytech.core.transport.LogisticNeighbor;
import at.rasebdon.hytech.core.util.PipeConnectionMask;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;

import javax.annotation.Nullable;
import java.util.HashMap;
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
    /// Fallback model names for the push/pull markers placed on explicitly configured
    /// faces. Plain connections are part of the block's own model, so they need none.
    public static final Map<BlockFaceConfigType, String> DEFAULT_CONNECTION_MODEL_ASSETS = Map.of(
            BlockFaceConfigType.BOTH, "Pipe_Normal",
            BlockFaceConfigType.OUTPUT, "Pipe_Push",
            BlockFaceConfigType.INPUT, "Pipe_Pull"
    );

    protected final Map<BlockFaceConfigType, String> connectionModelAssetNames = new HashMap<>();
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
    public void reload() {
        super.reload();
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

    /// Model used for the marker on a face with the given explicit configuration.
    @Nullable
    public ModelAsset getConnectionModelAssetFor(BlockFaceConfigType configType) {
        var name = connectionModelAssetNames.get(configType);
        return name == null ? null : ModelAsset.getAssetMap().getAsset(name);
    }

    public boolean canPullFrom(LogisticNeighbor<TContainer> target) {
        return this.hasInputTowards(target.getHolder())
                && target.allowsOutputTowards(this);
    }

    public boolean canPushTo(LogisticNeighbor<TContainer> target) {
        return this.hasOutputTowards(target.getHolder())
                && target.allowsInputTowards(this);
    }

    public boolean canOutputTo(LogisticNeighbor<TContainer> target) {
        return hasOutputOrBothTowards(target.getHolder())
                && target.allowsInputTowards(this);
    }

    public boolean isConnectedTo(LogisticNeighbor<TContainer> neighbor) {
        var neighborHolder = neighbor.getHolder();
        return (this.hasOutputOrBothTowards(neighborHolder) && neighbor.allowsInputTowards(this))
                || (this.hasInputOrBothTowards(neighborHolder) && neighbor.allowsOutputTowards(this));
    }

    /// Size of this pipe's centre hub in model units, used to hit-test the arms.
    ///
    /// This is geometry, not configuration: it must match the source models the generator
    /// builds each type's variants from (see PIPE_TYPES in scripts/generate-pipe-assets.py).
    /// Deliberately not codec backed -- a persisted copy would go stale on blocks placed
    /// before a geometry change and silently mis-aim the wrench.
    public int getHubSize() {
        return PipeConnectionMask.DEFAULT_HUB_UNITS;
    }

    public boolean needsRenderReload() {
        return needsRenderReload;
    }

    public void resetNeedsRenderReload() {
        this.needsRenderReload = false;
    }
}
