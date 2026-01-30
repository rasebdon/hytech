package com.rasebdon.hytech.core.transport;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BlockFace;
import com.rasebdon.hytech.core.components.LogisticContainerComponent;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class LogisticNeighborMap<TContainer> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final EnumMap<BlockFace, LogisticContainerComponent<TContainer>> faceToNeighbor =
            new EnumMap<>(BlockFace.class);

    private final Map<LogisticContainerComponent<TContainer>, BlockFace> neighborToFace =
            new HashMap<>();

    public void put(
            BlockFace face,
            LogisticContainerComponent<TContainer> neighbor
    ) {
        if (this.faceToNeighbor.containsKey(face)) {
            LOGGER.atWarning().log("%s already has a registered neighbor container", face.name());
            return;
        }

        LOGGER.atInfo().log("%s registered neighbor container", face.name());
        faceToNeighbor.put(face, neighbor);
        neighborToFace.put(neighbor, face);
    }

    @Nullable
    public LogisticContainerComponent<TContainer> getByFace(BlockFace face) {
        return faceToNeighbor.get(face);
    }

    @Nullable
    public BlockFace getByNeighbor(LogisticContainerComponent<TContainer> neighbor
    ) {
        return neighborToFace.get(neighbor);
    }

    public void removeByFace(BlockFace face) {
        var neighbor = faceToNeighbor.remove(face);
        if (neighbor != null) {
            neighborToFace.remove(neighbor);
        }
    }

    public void removeByNeighbor(
            LogisticContainerComponent<TContainer> neighbor
    ) {
        var face = neighborToFace.remove(neighbor);
        if (face != null) {
            faceToNeighbor.remove(face);
        }
    }

    public Set<LogisticContainerComponent<TContainer>> getAllNeighbors() {
        return neighborToFace.keySet();
    }

    public void clear() {
        this.faceToNeighbor.clear();
        this.neighborToFace.clear();
    }
}

