package at.rasebdon.hytech.core.util;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;

import javax.annotation.Nonnull;

public record BlockTransform(
        @Nonnull Vector3i worldPos,  // Global coordinates (e.g., 1500, 64, -200)
        @Nonnull Vector3i localPos,  // Position relative to the chunk (0-31)
        @Nonnull RotationTuple rotation,
        int chunkX,         // Chunk X coordinate
        int chunkZ          // Chunk Z coordinate
) {
    @Override
    @Nonnull
    public String toString() {
        return String.format("World[%d, %d, %d] | Local[%d, %d, %d] | Chunk[%d, %d]",
                worldPos.x, worldPos.y, worldPos.z,
                localPos.x, localPos.y, localPos.z,
                chunkX, chunkZ);
    }
}
