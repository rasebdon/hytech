package at.rasebdon.hytech.core.components;

import at.rasebdon.hytech.core.transport.LogisticNeighbor;
import at.rasebdon.hytech.core.transport.LogisticNeighborMap;
import com.hypixel.hytale.protocol.BlockFace;

import javax.annotation.Nullable;
import java.util.Set;

public abstract class ContainerHolder<TContainer> {
    protected final LogisticNeighborMap<TContainer> neighbors;

    protected ContainerHolder() {
        this.neighbors = new LogisticNeighborMap<>();
    }

    public abstract TContainer getContainer();

    public abstract boolean isAvailable();

    public abstract void reload();

    @Nullable
    public LogisticNeighbor<TContainer> getNeighbor(BlockFace face) {
        return neighbors.getByFace(face);
    }

    @Nullable
    public BlockFace getNeighborFace(ContainerHolder<TContainer> holder) {
        return neighbors.getByContainer(holder);
    }

    public Set<LogisticNeighbor<TContainer>> getNeighbors() {
        return neighbors.getAllNeighbors();
    }

    public void addNeighbor(BlockFace localFace, BlockFace neighborFace, ContainerHolder<TContainer> neighbor) {
        this.neighbors.put(localFace, neighbor);
        neighbor.neighbors.put(neighborFace, this);

        this.reload();
        neighbor.reload();
    }

    public void removeNeighbor(ContainerHolder<TContainer> neighbor) {
        this.neighbors.remove(neighbor);
        neighbor.neighbors.remove(this);

        this.reload();
        neighbor.reload();
    }

    public void clearNeighbors() {
        var allNeighbors = this.neighbors.getAllNeighbors();
        for (var neighbor : allNeighbors) {
            this.neighbors.remove(neighbor);

            var neighborHolder = neighbor.getHolder();
            if (neighborHolder != null) {
                neighborHolder.neighbors.remove(this);
                neighborHolder.reload();
            }
        }

        this.reload();
    }
}
