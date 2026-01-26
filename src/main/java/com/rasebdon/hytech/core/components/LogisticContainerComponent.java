package com.rasebdon.hytech.core.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.core.face.BlockFaceConfig;
import com.rasebdon.hytech.core.face.BlockFaceConfigOverride;
import com.rasebdon.hytech.core.face.BlockFaceConfigType;
import com.rasebdon.hytech.core.systems.LogisticTransferTarget;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public abstract class LogisticContainerComponent<TContainer> implements ILogisticContainerHolder<TContainer>, Component<ChunkStore> {
    @SuppressWarnings("rawtypes")
    public static final BuilderCodec<LogisticContainerComponent> CODEC =
            BuilderCodec.abstractBuilder(LogisticContainerComponent.class)
                    .append(new KeyedCodec<>("TransferPriority", Codec.INTEGER),
                            (c, v) -> c.transferPriority = v,
                            (c) -> c.transferPriority)
                    .addValidator(Validators.greaterThanOrEqual(0))
                    .documentation("Priority for energy transfer, lower means resource is extracted first").add()
                    .append(new KeyedCodec<>("BlockFaceConfigOverride", BlockFaceConfigOverride.CODEC),
                            (c, v) -> c.staticBlockFaceConfigOverride = v,
                            (c) -> c.staticBlockFaceConfigOverride)
                    .documentation("Side configuration for Input/Output sides").add()
                    .append(new KeyedCodec<>("BlockFaceConfigBitmap", Codec.INTEGER),
                            (c, v) -> c.currentBlockFaceConfig = new BlockFaceConfig(v),
                            (c) -> c.currentBlockFaceConfig.getBitmap())
                    .documentation("Side configuration for Input/Output sides").add()
                    .build();
    protected static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    protected final Map<TContainer, LogisticTransferTarget<TContainer>> transferTargets;
    protected int transferPriority;
    protected BlockFaceConfig currentBlockFaceConfig;
    protected BlockFaceConfigOverride staticBlockFaceConfigOverride;

    protected LogisticContainerComponent(BlockFaceConfig blockFaceConfig, int transferPriority) {
        this();

        this.currentBlockFaceConfig = blockFaceConfig.clone();
        this.transferPriority = transferPriority;
    }

    protected LogisticContainerComponent() {
        this.currentBlockFaceConfig = new BlockFaceConfig();
        this.transferTargets = new HashMap<>();
    }

    public List<LogisticTransferTarget<TContainer>> getTransferTargets() {
        return transferTargets.values().stream().toList();
    }

    public void tryAddTransferTarget(TContainer target, BlockFace from, BlockFace to) {
        if (this.transferTargets.containsKey(target)) return;

        LOGGER.atInfo().log("%s (%s) adding transfer target %s (%s)", toString(), from.name(),
                target.toString(), to.name());
        this.transferTargets.put(target, new LogisticTransferTarget<>(getContainer(), target, from, to));
    }

    public void removeTransferTarget(TContainer target) {
        LOGGER.atInfo().log("%s removed transfer target %s", toString(), target.toString());
        this.transferTargets.remove(target);
    }

    public void clearTransferTargets() {
        this.transferTargets.clear();
    }

    public BlockFaceConfigType getConfigForFace(BlockFace face) {
        return this.currentBlockFaceConfig.getFaceConfigType(face);
    }

    public boolean canReceiveFromFace(BlockFace face) {
        return this.currentBlockFaceConfig.canReceiveFromFace(face);
    }

    public boolean canExtractFromFace(BlockFace face) {
        return this.currentBlockFaceConfig.canExtractFromFace(face);
    }

    public int getTransferPriority() {
        return transferPriority;
    }

    @Nullable
    public abstract Component<ChunkStore> clone();
}
