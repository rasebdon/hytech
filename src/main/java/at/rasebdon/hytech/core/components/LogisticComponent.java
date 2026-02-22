package at.rasebdon.hytech.core.components;

import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticComponentChangedEvent;
import at.rasebdon.hytech.core.transport.BlockFaceConfig;
import at.rasebdon.hytech.core.transport.BlockFaceConfigType;
import at.rasebdon.hytech.core.transport.LogisticNeighbor;
import at.rasebdon.hytech.core.transport.LogisticNeighborMap;
import at.rasebdon.hytech.core.util.EventBusUtil;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nullable;
import java.util.Set;


public abstract class LogisticComponent<TContainer> implements ContainerHolder<TContainer>, Component<ChunkStore> {
    @SuppressWarnings("rawtypes")
    public static final BuilderCodec<LogisticComponent> CODEC =
            BuilderCodec.abstractBuilder(LogisticComponent.class)
                    .append(new KeyedCodec<>("BlockFaceConfig", BlockFaceConfig.CODEC),
                            (c, v) -> c.blockFaceConfig = v,
                            (c) -> c.blockFaceConfig)
                    .documentation("Side configuration for Logistic Container Block").add()
                    .build();

    protected final LogisticNeighborMap<TContainer> neighbors;
    protected BlockFaceConfig blockFaceConfig;

    protected LogisticComponent(BlockFaceConfig blockFaceConfig) {
        this();
        this.blockFaceConfig = blockFaceConfig.clone();
    }

    protected LogisticComponent() {
        this.blockFaceConfig = new BlockFaceConfig();
        this.neighbors = new LogisticNeighborMap<>();
    }

    @Nullable
    public TContainer getNeighborContainer(BlockFace face) {
        var neighbor = neighbors.getByFace(face);
        if (neighbor != null) {
            return neighbor.getContainer();
        }
        return null;
    }

    @Nullable
    public LogisticNeighbor<TContainer> getNeighbor(BlockFace face) {
        return neighbors.getByFace(face);
    }

    @Nullable
    public LogisticComponent<TContainer> getNeighborLogisticContainer(BlockFace face) {
        var neighbor = neighbors.getByFace(face);
        if (neighbor != null) {
            return neighbor.getLogisticContainer();
        }
        return null;
    }

    @Nullable
    public BlockFace getNeighborFace(LogisticNeighbor<TContainer> neighbor) {
        return getNeighborFace(neighbor.getContainer());
    }

    @Nullable
    public BlockFace getNeighborFace(TContainer neighbor) {
        return neighbors.getByContainer(neighbor);
    }

    public Set<LogisticNeighbor<TContainer>> getNeighbors() {
        return neighbors.getAllNeighbors();
    }

    public void addExternalNeighbor(BlockFace face, TContainer container) {
        neighbors.put(face, container);
        reloadContainer();
    }

    public void removeExternalNeighbor(TContainer container) {
        neighbors.remove(container);
        reloadContainer();
    }

    public void addNeighbor(BlockFace localFace, BlockFace neighborFace, LogisticComponent<TContainer> neighbor) {
        this.neighbors.put(localFace, neighbor);
        neighbor.neighbors.put(neighborFace, this);

        this.reloadContainer();
        neighbor.reloadContainer();
    }

    public void removeNeighbor(LogisticComponent<TContainer> neighbor) {
        this.neighbors.remove(neighbor);
        neighbor.neighbors.remove(this);

        this.reloadContainer();
        neighbor.reloadContainer();
    }

    public void clearNeighbors() {
        var allNeighbors = this.neighbors.getAllNeighbors();
        for (var neighbor : allNeighbors) {
            this.neighbors.remove(neighbor);

            var neighborLogistic = neighbor.getLogisticContainer();
            if (neighborLogistic != null) {
                neighborLogistic.neighbors.remove(this);
                neighborLogistic.reloadContainer();
            }
        }

        this.reloadContainer();
    }

    public BlockFaceConfigType getFaceConfigTowards(TContainer neighbor) {
        return this.getFaceConfigTowards(getNeighborFace(neighbor));
    }

    public BlockFaceConfigType getFaceConfigTowards(BlockFace face) {
        return this.blockFaceConfig.getType(face);
    }

    public boolean hasInputFaceTowards(TContainer neighbor) {
        return this.blockFaceConfig.isInput(getNeighborFace(neighbor));
    }

    public boolean hasOutputFaceTowards(TContainer neighbor) {
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
        var neighbor = getNeighborLogisticContainer(face);
        if (neighbor != null) {
            neighbor.reloadContainer();
        }
    }

    @Nullable
    public abstract Component<ChunkStore> clone();

    protected abstract LogisticComponentChangedEvent<TContainer> createContainerChangedEvent(
            LogisticChangeType type, LogisticComponent<TContainer> component);

    public void dispatchChangeEvent(LogisticChangeType logisticChangeType) {
        EventBusUtil.dispatchIfListening(
                createContainerChangedEvent(logisticChangeType, this)
        );
    }
}
