package com.rasebdon.hytech.core.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.core.events.LogisticChangeType;
import com.rasebdon.hytech.core.events.LogisticContainerChangedEvent;
import com.rasebdon.hytech.core.transport.BlockFaceConfig;
import com.rasebdon.hytech.core.transport.BlockFaceConfigOverride;
import com.rasebdon.hytech.core.transport.BlockFaceConfigType;
import com.rasebdon.hytech.core.transport.LogisticNeighborMap;
import com.rasebdon.hytech.core.util.EventBusUtil;

import javax.annotation.Nullable;
import java.util.Set;


public abstract class LogisticContainerComponent<TContainer> implements IContainerHolder<TContainer>, Component<ChunkStore> {
    @SuppressWarnings("rawtypes")
    public static final BuilderCodec<LogisticContainerComponent> CODEC =
            BuilderCodec.abstractBuilder(LogisticContainerComponent.class)
                    .append(new KeyedCodec<>("BlockFaceConfigOverride", BlockFaceConfigOverride.CODEC),
                            (c, v) -> c.staticBlockFaceConfigOverride = v,
                            (c) -> c.staticBlockFaceConfigOverride)
                    .documentation("Side configuration for Input/Output sides").add()
                    .append(new KeyedCodec<>("BlockFaceConfigBitmap", Codec.INTEGER),
                            (c, v) -> c.currentBlockFaceConfig = new BlockFaceConfig(v),
                            (c) -> c.currentBlockFaceConfig.getBitmap())
                    .documentation("Side configuration for Input/Output sides").add()
                    .build();
    protected final LogisticNeighborMap<TContainer> neighbors;
    protected BlockFaceConfig currentBlockFaceConfig;
    protected BlockFaceConfigOverride staticBlockFaceConfigOverride;

    protected LogisticContainerComponent(BlockFaceConfig blockFaceConfig) {
        this();
        this.currentBlockFaceConfig = blockFaceConfig.clone();
    }

    protected LogisticContainerComponent() {
        this.currentBlockFaceConfig = new BlockFaceConfig();
        this.neighbors = new LogisticNeighborMap<>();
    }

    @Nullable
    public LogisticContainerComponent<TContainer> getNeighborContainer(BlockFace face) {
        return neighbors.getByFace(face);
    }

    @Nullable
    public BlockFace getNeighborFace(LogisticContainerComponent<TContainer> neighbor) {
        return neighbors.getByNeighbor(neighbor);
    }

    public Set<LogisticContainerComponent<TContainer>> getNeighbors() {
        return neighbors.getAllNeighbors();
    }

    public void addNeighbor(BlockFace localFace, BlockFace neighborFace, LogisticContainerComponent<TContainer> neighbor) {
        this.neighbors.put(localFace, neighbor);
        neighbor.neighbors.put(neighborFace, this);

        this.reloadContainer();
        neighbor.reloadContainer();
    }

    public void removeNeighbor(LogisticContainerComponent<TContainer> neighbor) {
        this.neighbors.removeByNeighbor(neighbor);
        neighbor.neighbors.removeByNeighbor(this);

        this.reloadContainer();
        neighbor.reloadContainer();
    }

    public void clearNeighbors() {
        var allNeighbors = this.neighbors.getAllNeighbors();
        for (var neighbor : allNeighbors) {
            this.neighbors.removeByNeighbor(neighbor);
            neighbor.neighbors.removeByNeighbor(this);
            neighbor.reloadContainer();
        }

        this.reloadContainer();
    }

    public BlockFaceConfigType getFaceConfigTowards(LogisticContainerComponent<TContainer> neighbor) {
        return this.getFaceConfigTowards(getNeighborFace(neighbor));
    }

    public BlockFaceConfigType getFaceConfigTowards(BlockFace face) {
        return this.currentBlockFaceConfig.getFaceConfigType(face);
    }

    public boolean hasInputFaceTowards(LogisticContainerComponent<TContainer> neighbor) {
        return this.currentBlockFaceConfig.isInput(getNeighborFace(neighbor));
    }

    public boolean hasOutputFaceTowards(LogisticContainerComponent<TContainer> neighbor) {
        return this.currentBlockFaceConfig.isOutput(getNeighborFace(neighbor));
    }

    public void cycleBlockFaceConfig(BlockFace face) {
        this.currentBlockFaceConfig.cycleFaceConfigType(face);
        this.reloadContainer();
        this.reloadNeighborContainer(face);
    }

    public void reloadContainer() {
        dispatchChangeEvent(LogisticChangeType.CHANGED);
    }

    private void reloadNeighborContainer(BlockFace face) {
        var neighbor = getNeighborContainer(face);
        if (neighbor != null) {
            neighbor.reloadContainer();
        }
    }

    @Nullable
    public abstract Component<ChunkStore> clone();

    protected abstract LogisticContainerChangedEvent<TContainer> createContainerChangedEvent(
            LogisticChangeType type, LogisticContainerComponent<TContainer> component);

    public void dispatchChangeEvent(LogisticChangeType logisticChangeType) {
        EventBusUtil.dispatchIfListening(
                createContainerChangedEvent(logisticChangeType, this)
        );
    }
}
