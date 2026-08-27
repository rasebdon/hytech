package at.rasebdon.hytech.core.util;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.MessageUtil;
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

        var located = locate(store, blockInfo);
        if (located == null) return null;

        return located.chunk().getBlockType(located.localPos());
    }

    /// Item id of the block at `pos`, or null when there is nothing there.
    ///
    /// What an `ItemSlot` wants: the side configurator draws each neighbour as its own icon, and an
    /// icon is addressed by item id. Null for air, for an unloaded chunk, and for a block with no
    /// item form at all -- every one of which is drawn as an empty well rather than a broken icon.
    @Nullable
    public static String getBlockItemIdOrNull(@Nonnull World world, @Nonnull Vector3i pos) {
        var blockType = getBlockType(world, pos);
        if (blockType == null) return null;

        var item = blockType.getItem();

        return item == null ? null : item.getId();
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

    /// Resolves a block entity to its column and position, or null when either has gone.
    ///
    /// 0.6.0 moved block entities off the column and onto a 32-cube `ChunkSection`: a
    /// `BlockStateInfo` now carries a *section* reference, and its index decodes to coordinates
    /// inside that cube rather than inside the column. So the y has to be lifted back out by the
    /// section's own y before `WorldChunk` will accept it -- the old
    /// `ChunkUtil.yFromBlockInColumn` did that lifting implicitly and no longer exists.
    @Nullable
    public static BlockLocation locate(@Nonnull ComponentAccessor<ChunkStore> accessor,
                                       @Nonnull BlockModule.BlockStateInfo blockStateInfo) {

        var section = accessor.getComponent(blockStateInfo.getSectionRef(),
                ChunkSection.getComponentType());
        if (section == null) return null;

        var chunk = accessor.getComponent(section.getChunkColumnReference(),
                WorldChunk.getComponentType());
        if (chunk == null) return null;

        int index = blockStateInfo.getIndex();

        var localPos = new Vector3i(
                ChunkUtil.xFromIndex(index),
                ChunkUtil.worldCoordFromLocalCoord(section.getY(), ChunkUtil.yFromIndex(index)),
                ChunkUtil.zFromIndex(index));

        return new BlockLocation(chunk, localPos);
    }

    /// Whether this entity is crouching.
    ///
    /// `MovementStatesComponent` is the same source vanilla's `Condition` interaction reads for
    /// its `Crouching` key, so a crouch modifier here behaves the way one declared in an asset
    /// would.
    public static boolean isCrouching(@Nonnull World world, @Nonnull Ref<EntityStore> entityRef) {
        var movement = world.getEntityStore().getStore()
                .getComponent(entityRef, MovementStatesComponent.getComponentType());
        if (movement == null) return false;

        var states = movement.getMovementStates();

        return states != null && (states.crouching || states.forcedCrouching);
    }

    public static void sendPlayerMessage(@Nonnull Ref<EntityStore> entityRef, @Nonnull String text) {
        var playerRef = entityRef.getStore().getComponent(entityRef, PlayerRef.getComponentType());
        if (playerRef != null) {
            playerRef.sendMessage(Message.raw(text));
        }
    }

    /// The chunk column and position for a block entity reference.
    @Nullable
    public static BlockLocation locate(@Nonnull ComponentAccessor<ChunkStore> accessor,
                                       @Nonnull Ref<ChunkStore> blockRef) {
        var info = accessor.getComponent(blockRef, BlockModule.BlockStateInfo.getComponentType());

        return info == null ? null : locate(accessor, info);
    }

    @Nullable
    public static BlockTransform getBlockTransform(@Nonnull Ref<ChunkStore> blockRef, @Nonnull Store<ChunkStore> store) {
        var located = locate(store, blockRef);
        if (located == null) return null;

        var worldChunk = located.chunk();
        var localPosition = located.localPos();

        // Transform to world coordinates
        int worldX = ChunkUtil.worldCoordFromLocalCoord(worldChunk.getX(), localPosition.x);
        int worldZ = ChunkUtil.worldCoordFromLocalCoord(worldChunk.getZ(), localPosition.z);

        // 0.6.0 removed BlockAccessor.getRotation; only the index survives, so the tuple is
        // looked up rather than handed over. RotationTuple.get is an unguarded array access and
        // this runs inside a ticking system, so an index from an unloaded or malformed block
        // falls back to no rotation instead of taking the tick down.
        int rotationIndex = worldChunk.getRotationIndex(worldX, localPosition.y, worldZ);
        var rotation = rotationIndex >= 0 && rotationIndex < RotationTuple.VALUES.length
                ? RotationTuple.get(rotationIndex)
                : RotationTuple.NONE;

        return new BlockTransform(
                new Vector3i(worldX, localPosition.y, worldZ),
                localPosition,
                rotation,
                worldChunk.getX(),
                worldChunk.getZ()
        );
    }

    /// A block entity's chunk column and where it sits in it.
    ///
    /// `localPos` is the space `WorldChunk`'s own block accessors take: x and z local to the
    /// column, y absolute. Not the same as a world position, and not what
    /// `BlockStateInfo.fillWorldPos` produces -- hence a helper of our own.
    ///
    /// @param chunk    the column the block belongs to
    /// @param localPos chunk-local x and z, world y
    public record BlockLocation(@Nonnull WorldChunk chunk, @Nonnull Vector3i localPos) {
    }
}
