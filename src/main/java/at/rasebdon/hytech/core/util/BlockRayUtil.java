package at.rasebdon.hytech.core.util;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/// Ray tracing against the shapes blocks actually have.
///
/// `TargetUtil.getTargetBlock` walks whole cells and only tests the block id, so anything
/// sharing a cell with a thin block -- a pipe, say -- counts as a hit across the entire
/// 1x1x1 cell. That is fine for placing and breaking, but it means a pipe standing next to
/// the face you are aiming at swallows the ray. This walks the same ray but asks each
/// candidate's hitbox whether the point is really inside it.
public final class BlockRayUtil {

    /// Step along the ray, in blocks. Small enough not to skip a pipe arm, coarse enough
    /// that a full reach is only a few hundred samples for one player per pass.
    private static final double STEP = 0.02;

    private BlockRayUtil() {
    }

    /// First block whose hitbox the ray actually enters, or null within `maxDistance`.
    @Nullable
    public static Hit trace(
            @Nonnull World world,
            @Nonnull Vector3dc origin,
            @Nonnull Vector3dc direction,
            double maxDistance) {

        var point = new Vector3d();
        var block = new Vector3i();

        for (double travelled = 0; travelled <= maxDistance; travelled += STEP) {
            point.set(direction).mul(travelled).add(origin);
            block.set((int) Math.floor(point.x), (int) Math.floor(point.y), (int) Math.floor(point.z));

            if (containsPoint(world, block, point)) {
                return new Hit(new Vector3i(block), new Vector3d(point));
            }
        }

        return null;
    }

    private static boolean containsPoint(World world, Vector3i block, Vector3d point) {
        // Deliberately the in-memory read: World.getBlockType goes through getChunk, which
        // can load a chunk, and loading mutates the store. Called from a system tick that
        // throws "Store is currently processing". An unloaded chunk is not something the
        // player can be looking at anyway, so treating it as a miss is correct.
        var chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(block.x, block.z));
        if (chunk == null) return false;

        var blockType = chunk.getBlockType(block.x, block.y, block.z);
        if (blockType == null || blockType.isUnknown()) return false;

        // Air is a BlockType too, and its hitbox is the unit cube, so without this every
        // ray "hits" the first empty cell it enters -- which is the one around the eye.
        if (blockType.getMaterial() == BlockMaterial.Empty) return false;

        var hitbox = BlockBoundingBoxes.getAssetMap().getAsset(blockType.getHitboxTypeIndex());
        if (hitbox == null) return false;

        // Rotation 0: the blocks this is used against are authored without variant
        // rotation, and a rotated hitbox would only matter for shaped rotated blocks.
        return hitbox.get(0).containsPosition(
                point.x - block.x, point.y - block.y, point.z - block.z);
    }

    public record Hit(@Nonnull Vector3i block, @Nonnull Vector3d point) {
    }
}
