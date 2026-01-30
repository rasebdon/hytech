package com.rasebdon.hytech.core.util;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import org.jetbrains.annotations.NotNull;

public record BlockTransform(
        Vector3i worldPos,  // Global coordinates (e.g., 1500, 64, -200)
        Vector3i localPos,  // Position relative to the chunk (0-31)
        RotationTuple rotation,
        int chunkX,         // Chunk X coordinate
        int chunkZ          // Chunk Z coordinate
) {
    @Override
    @NotNull
    public String toString() {
        return String.format("World[%d, %d, %d] | Local[%d, %d, %d] | Chunk[%d, %d]",
                worldPos.x, worldPos.y, worldPos.z,
                localPos.x, localPos.y, localPos.z,
                chunkX, chunkZ);
    }
}
