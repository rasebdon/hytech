package at.rasebdon.hytech.core.util;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.util.MessageUtil;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class HytechUtil {

    @Nullable
    public static Ref<ChunkStore> getBlockEntityRef(@Nonnull World world, @Nonnull Vector3i pos) {
        return BlockModule.getBlockEntity(world, pos.x, pos.y, pos.z);
    }

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


    /// Translated display name of the block at `pos`, or a dash when there is nothing there.
    ///
    /// Pages want a name they can always print; the nullable variant is for callers that need to
    /// distinguish "air" from "a block with no name".
    @Nonnull
    public static String getBlockDisplayName(@Nonnull World world, @Nonnull Vector3i pos) {
        var name = getBlockDisplayNameOrNull(world, pos);

        return name == null ? "-" : name;
    }

    @Nullable
    public static String getBlockDisplayNameOrNull(@Nonnull World world, @Nonnull Vector3i pos) {
        var blockType = getBlockType(world, pos);
        if (blockType == null) return null;

        var item = blockType.getItem();
        if (item == null) return null;

        var properties = item.getTranslationProperties();
        if (properties == null || properties.getName() == null) return null;

        return MessageUtil.toAnsiString(Message.translation(properties.getName())).toString();
    }

    public static void sendPlayerMessage(@Nonnull Ref<EntityStore> entityRef, @Nonnull String text) {
        var playerRef = entityRef.getStore().getComponent(entityRef, PlayerRef.getComponentType());
        if (playerRef != null) {
            playerRef.sendMessage(Message.raw(text));
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
        int worldX = ChunkUtil.worldCoordFromLocalCoord(worldChunk.getX(), localPosition.x);
        int worldZ = ChunkUtil.worldCoordFromLocalCoord(worldChunk.getZ(), localPosition.z);

        var rotation = worldChunk.getRotation(worldX, localPosition.y, worldZ);

        return new BlockTransform(
                new Vector3i(worldX, localPosition.y, worldZ),
                localPosition,
                rotation,
                worldChunk.getX(),
                worldChunk.getZ()
        );
    }
}
