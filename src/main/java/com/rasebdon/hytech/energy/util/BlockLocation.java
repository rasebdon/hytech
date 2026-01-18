package com.rasebdon.hytech.energy.util;

import com.hypixel.hytale.math.vector.Vector3i;
import org.jetbrains.annotations.NotNull;

public record BlockLocation(
        Vector3i worldPos,  // Global coordinates (e.g., 1500, 64, -200)
        Vector3i localPos,  // Position relative to the chunk (0-31)
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
