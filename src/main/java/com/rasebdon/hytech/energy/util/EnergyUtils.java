package com.rasebdon.hytech.energy.util;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
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
}