package com.rasebdon.hytech.core.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.core.face.BlockFaceConfig;
import com.rasebdon.hytech.core.face.BlockFaceConfigOverride;
import com.rasebdon.hytech.core.systems.LogisticTransferTarget;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class LogisticContainerComponent<TContainer> implements Component<ChunkStore> {

    @SuppressWarnings("rawtypes")
    public static final BuilderCodec<LogisticContainerComponent> CODEC =
            BuilderCodec.abstractBuilder(LogisticContainerComponent.class)
                    .append(new KeyedCodec<>("TransferPriority", Codec.INTEGER),
                            (c, v) -> c.transferPriority = v,
                            (c) -> c.transferPriority)
                    .addValidator(Validators.greaterThanOrEqual(0))
                    .documentation("Priority for energy transfer, lower means resource is extracted first").add()
                    .append(new KeyedCodec<>("BlackFaceConfig", BlockFaceConfigOverride.CODEC),
                            (c, v) -> c.staticBlockFaceConfigOverride = v,
                            (c) -> c.staticBlockFaceConfigOverride)
                    .documentation("Side configuration for Input/Output sides").add()
                    .build();
    protected final Map<TContainer, LogisticTransferTarget<TContainer>> transferTargets;
    protected int transferPriority;
    protected BlockFaceConfig currentBlockFaceConfig;
    protected BlockFaceConfigOverride staticBlockFaceConfigOverride;

    protected LogisticContainerComponent() {
        this.transferTargets = new HashMap<>();
    }

    public List<LogisticTransferTarget<TContainer>> getTransferTargets() {
        return transferTargets.values().stream().toList();
    }

    public void addTransferTarget(TContainer target, BlockFace from, BlockFace to) {
        this.transferTargets.put(target, new LogisticTransferTarget<>(target, from, to));
    }

    public void removeTransferTarget(TContainer target) {
        this.transferTargets.remove(target);
    }

    public void clearTransferTargets() {
        this.transferTargets.clear();
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

    public abstract TContainer getContainer();

    @Nullable
    public abstract Component<ChunkStore> clone();
}
