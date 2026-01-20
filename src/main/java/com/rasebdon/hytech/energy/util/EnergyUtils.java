package com.rasebdon.hytech.energy.util;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class EnergyUtils {

    /**
     * Retrieves the Entity Reference associated with a specific block position.
     */
    @Nullable
    public static Ref<ChunkStore> getBlockEntityRef(@Nonnull World world, @Nonnull Vector3i pos) {
        var chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunk == null) return null;

        var chunkRef = chunk.getReference();
        var blockComponentChunk = world.getChunkStore().getStore().getComponent(chunkRef, BlockComponentChunk.getComponentType());
        if (blockComponentChunk == null) return null;

        int blockIndex = ChunkUtil.indexBlockInColumn(pos.x, pos.y, pos.z);
        return blockComponentChunk.getEntityReference(blockIndex);
    }

    /**
     * Helper to get a component directly from a block position.
     */
    @Nullable
    public static <T extends Component<ChunkStore>> T getComponentAtBlock(
            @Nonnull World world,
            @Nonnull Vector3i pos,
            @Nonnull ComponentType<ChunkStore, T> type) {

        Ref<ChunkStore> blockRef = getBlockEntityRef(world, pos);
        if (blockRef == null) return null;

        return blockRef.getStore().getComponent(blockRef, type);
    }

    /**
     * Sends a message to a player entity reference if the player component exists.
     */
    public static void sendPlayerMessage(@Nonnull Ref<EntityStore> playerRef, @Nonnull String text) {
        var player = playerRef.getStore().getComponent(playerRef, Player.getComponentType());
        if (player != null) {
            player.sendMessage(Message.raw(text));
        }
    }

    @Nullable
    public static BlockTransform getBlockTransform(@Nonnull Ref<ChunkStore> blockRef, @Nonnull Store<ChunkStore> store) {
        var info = store.getComponent(blockRef, BlockModule.BlockStateInfo.getComponentType());
        if (info == null) return null;

        var worldChunk = store.getComponent(info.getChunkRef(), WorldChunk.getComponentType());
        if (worldChunk == null) return null;

        int blockIndex = info.getIndex();

        // Local coordinates within the chunk
        int localX = ChunkUtil.xFromBlockInColumn(blockIndex);
        int localY = ChunkUtil.yFromBlockInColumn(blockIndex);
        int localZ = ChunkUtil.zFromBlockInColumn(blockIndex);

        // Transform to world coordinates
        int worldX = ChunkUtil.worldCoordFromLocalCoord(worldChunk.getX(), localX);
        int worldZ = ChunkUtil.worldCoordFromLocalCoord(worldChunk.getZ(), localZ);

        var rotation = worldChunk.getRotation(worldX, localY, worldZ);

        return new BlockTransform(
                new Vector3i(worldX, localY, worldZ),
                new Vector3i(localX, localY, localZ),
                rotation,
                worldChunk.getX(),
                worldChunk.getZ()
        );
    }

    public static BlockFace getLocalFace(Vector3i worldDirection, RotationTuple rotation) {
        // To go from World to Local, we apply the inverse rotations
        Rotation invYaw = Rotation.None.subtract(rotation.yaw());
        Rotation invPitch = Rotation.None.subtract(rotation.pitch());
        Rotation invRoll = Rotation.None.subtract(rotation.roll());

        // Use the Rotation class to rotate the vector by the inverse values
        Vector3i localVec = Rotation.rotate(worldDirection, invYaw, invPitch, invRoll);

        // Map the resulting vector to your BlockFace enum
        if (localVec.x == 0 && localVec.y == 1 && localVec.z == 0) return BlockFace.Up;
        if (localVec.x == 0 && localVec.y == -1 && localVec.z == 0) return BlockFace.Down;
        if (localVec.x == 0 && localVec.y == 0 && localVec.z == -1) return BlockFace.North;
        if (localVec.x == 0 && localVec.y == 0 && localVec.z == 1) return BlockFace.South;
        if (localVec.x == 1 && localVec.y == 0 && localVec.z == 0) return BlockFace.East;
        if (localVec.x == -1 && localVec.y == 0 && localVec.z == 0) return BlockFace.West;

        return BlockFace.None;
    }
}