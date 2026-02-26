package at.rasebdon.hytech.core.transport;

import at.rasebdon.hytech.core.components.ContainerHolder;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BlockFace;

import javax.annotation.Nullable;
import java.util.*;

public final class LogisticNeighborMap<TContainer> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final EnumMap<BlockFace, LogisticNeighbor<TContainer>> faceToNeighbor =
            new EnumMap<>(BlockFace.class);

    private final Map<ContainerHolder<TContainer>, BlockFace> holderToFace =
            new HashMap<>();

    public void put(BlockFace face, ContainerHolder<TContainer> container) {
        put(face, new LogisticNeighbor<>(container));
    }

    private void put(BlockFace face, LogisticNeighbor<TContainer> neighbor) {
        if (faceToNeighbor.containsKey(face)) {
            LOGGER.atWarning().log("%s already has a registered neighbor container", face.name());
            return;
        }

        var container = neighbor.getHolder();

        if (holderToFace.containsKey(container)) {
            LOGGER.atWarning().log("Container already registered on another face");
            return;
        }

        LOGGER.atInfo().log("%s registered neighbor container", face.name());

        faceToNeighbor.put(face, neighbor);
        holderToFace.put(container, face);
    }

    @Nullable
    public LogisticNeighbor<TContainer> getByFace(BlockFace face) {
        return faceToNeighbor.get(face);
    }

    @Nullable
    public BlockFace getByContainer(ContainerHolder<TContainer> holder) {
        return holderToFace.get(holder);
    }

    public void remove(ContainerHolder<TContainer> holder) {
        var face = holderToFace.remove(holder);
        if (face != null) {
            faceToNeighbor.remove(face);
        }
    }

    public void remove(LogisticNeighbor<TContainer> neighbor) {
        remove(neighbor.getHolder());
    }

    public Set<LogisticNeighbor<TContainer>> getAllNeighbors() {
        return new HashSet<>(faceToNeighbor.values());
    }
}


