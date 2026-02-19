package at.rasebdon.hytech.core.transport;

import at.rasebdon.hytech.core.components.LogisticComponent;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BlockFace;

import javax.annotation.Nullable;
import java.util.*;

public final class LogisticNeighborMap<TContainer> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final EnumMap<BlockFace, LogisticNeighbor<TContainer>> faceToNeighbor =
            new EnumMap<>(BlockFace.class);

    private final Map<TContainer, BlockFace> containerToFace =
            new HashMap<>();

    public void put(BlockFace face, TContainer container) {
        put(face, new LogisticNeighbor<>(container));
    }

    public void put(BlockFace face, LogisticComponent<TContainer> component) {
        put(face, new LogisticNeighbor<>(component));
    }

    private void put(BlockFace face, LogisticNeighbor<TContainer> neighbor) {
        if (faceToNeighbor.containsKey(face)) {
            LOGGER.atWarning().log("%s already has a registered neighbor container", face.name());
            return;
        }

        TContainer container = neighbor.getContainer();

        if (containerToFace.containsKey(container)) {
            LOGGER.atWarning().log("Container already registered on another face");
            return;
        }

        LOGGER.atInfo().log("%s registered neighbor container", face.name());

        faceToNeighbor.put(face, neighbor);
        containerToFace.put(container, face);
    }

    @Nullable
    public LogisticNeighbor<TContainer> getByFace(BlockFace face) {
        return faceToNeighbor.get(face);
    }

    @Nullable
    public BlockFace getByContainer(TContainer container) {
        return containerToFace.get(container);
    }

    public void remove(TContainer container) {
        var face = containerToFace.remove(container);
        if (face != null) {
            faceToNeighbor.remove(face);
        }
    }

    public void remove(LogisticNeighbor<TContainer> neighbor) {
        remove(neighbor.getContainer());
    }

    public void remove(LogisticComponent<TContainer> component) {
        remove(component.getContainer());
    }

    public Set<LogisticNeighbor<TContainer>> getAllNeighbors() {
        return new HashSet<>(faceToNeighbor.values());
    }
}


