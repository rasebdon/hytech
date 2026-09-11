package at.rasebdon.hytech.core.util;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import org.joml.Vector3i;

import javax.annotation.Nonnull;

public record BlockTransform(
        @Nonnull Vector3i worldPos,
        @Nonnull Vector3i localPos,
        @Nonnull RotationTuple rotation,
        int chunkX,
        int chunkZ
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
