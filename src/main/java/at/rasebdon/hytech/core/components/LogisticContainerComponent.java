package at.rasebdon.hytech.core.components;

import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticContainerChangedEvent;
import at.rasebdon.hytech.core.transport.BlockFaceConfig;
import at.rasebdon.hytech.core.transport.BlockFaceConfigType;
import at.rasebdon.hytech.core.transport.LogisticNeighborMap;
import at.rasebdon.hytech.core.util.EventBusUtil;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nullable;
import java.util.Set;


public abstract class LogisticContainerComponent<TContainer> implements IContainerHolder<TContainer>, Component<ChunkStore> {
    @SuppressWarnings("rawtypes")
    public static final BuilderCodec<LogisticContainerComponent> CODEC =
            BuilderCodec.abstractBuilder(LogisticContainerComponent.class)
                    .append(new KeyedCodec<>("BlockFaceConfig", BlockFaceConfig.CODEC),
                            (c, v) -> c.blockFaceConfig = v,
                            (c) -> c.blockFaceConfig)
                    .documentation("Side configuration for Logistic Container Block").add()
                    .build();

    protected final LogisticNeighborMap<TContainer> neighbors;
    protected BlockFaceConfig blockFaceConfig;

    protected LogisticContainerComponent(BlockFaceConfig blockFaceConfig) {
        this();
        this.blockFaceConfig = blockFaceConfig.clone();
    }

    protected LogisticContainerComponent() {
        this.blockFaceConfig = new BlockFaceConfig();
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
        return this.blockFaceConfig.getType(face);
    }

    public boolean hasInputFaceTowards(LogisticContainerComponent<TContainer> neighbor) {
        return this.blockFaceConfig.isInput(getNeighborFace(neighbor));
    }

    public boolean hasOutputFaceTowards(LogisticContainerComponent<TContainer> neighbor) {
        return this.blockFaceConfig.isOutput(getNeighborFace(neighbor));
    }

    public void cycleBlockFaceConfig(BlockFace face) {
        blockFaceConfig.cycleFace(face);

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
