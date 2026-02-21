package at.rasebdon.hytech.core.util;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class HytechUtil {

    /**
     * Retrieves the Entity Reference associated with a specific block position.
     */
    @Nullable
    public static Ref<ChunkStore> getBlockEntityRef(@Nonnull World world, @Nonnull Vector3i pos) {
        return BlockModule.getBlockEntity(world, pos.x, pos.y, pos.z);
    }

    /**
     * Helper to get a component directly from a block position.
     */
    @Nullable
    public static <T extends Component<ChunkStore>> T getBlockComponent(
            @Nonnull World world,
            @Nonnull Vector3i pos,
            @Nonnull ComponentType<ChunkStore, T> type) {
        return BlockModule.getComponent(type, world, pos.x, pos.y, pos.z);
    }

    @Nullable
    public static BlockType getBlockType(@Nonnull World world, @Nonnull Vector3i pos) {
        var blockRef = HytechUtil.getBlockEntityRef(world, pos);
        if (blockRef == null) return null;

        var store = world.getChunkStore().getStore();
        var blockInfo = store.getComponent(blockRef, BlockModule.BlockStateInfo.getComponentType());

        if (blockInfo == null) return null;

        var blockPosition = HytechUtil.getLocalBlockPosition(blockInfo);
        var chunk = store.getComponent(blockInfo.getChunkRef(), WorldChunk.getComponentType());
        if (chunk == null) return null;

        return chunk.getBlockType(blockPosition);
    }


    public static void sendPlayerMessage(@Nonnull Ref<EntityStore> playerRef, @Nonnull String text) {
        var player = playerRef.getStore().getComponent(playerRef, Player.getComponentType());
        if (player != null) {
            player.sendMessage(Message.raw(text));
        }
    }

    public static Vector3i getLocalBlockPosition(@Nonnull BlockModule.BlockStateInfo blockStateInfo) {
        int blockIndex = blockStateInfo.getIndex();

        int localX = ChunkUtil.xFromBlockInColumn(blockIndex);
        int localY = ChunkUtil.yFromBlockInColumn(blockIndex);
        int localZ = ChunkUtil.zFromBlockInColumn(blockIndex);

        return new Vector3i(localX, localY, localZ);
    }

    @Nullable
    public static BlockTransform getBlockTransform(@Nonnull Ref<ChunkStore> blockRef, @Nonnull Store<ChunkStore> store) {
        var info = store.getComponent(blockRef, BlockModule.BlockStateInfo.getComponentType());
        if (info == null) return null;

        var worldChunk = store.getComponent(info.getChunkRef(), WorldChunk.getComponentType());
        if (worldChunk == null) return null;

        var localPosition = getLocalBlockPosition(info);

        // Transform to world coordinates
        int worldX = ChunkUtil.worldCoordFromLocalCoord(worldChunk.getX(), localPosition.getX());
        int worldZ = ChunkUtil.worldCoordFromLocalCoord(worldChunk.getZ(), localPosition.getZ());

        var rotation = worldChunk.getRotation(worldX, localPosition.getY(), worldZ);

        return new BlockTransform(
                new Vector3i(worldX, localPosition.getY(), worldZ),
                localPosition,
                rotation,
                worldChunk.getX(),
                worldChunk.getZ()
        );
    }
}
