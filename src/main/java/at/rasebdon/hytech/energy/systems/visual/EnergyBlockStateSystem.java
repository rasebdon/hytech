package at.rasebdon.hytech.energy.systems.visual;

import at.rasebdon.hytech.core.util.HytechUtil;
import at.rasebdon.hytech.energy.components.EnergyBlockComponent;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;

// TODO : Make ticking system and improve performance
public class EnergyBlockStateSystem extends EntityTickingSystem<ChunkStore> {
    private final ComponentType<ChunkStore, EnergyBlockComponent> componentType;

    public EnergyBlockStateSystem(
            ComponentType<ChunkStore, EnergyBlockComponent> componentType) {
        this.componentType = componentType;
    }

    @Override
    public void tick(float dt,
                     int index,
                     @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
                     @Nonnull Store<ChunkStore> store,
                     @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        var container = archetypeChunk.getComponent(index, this.componentType);

        if (container == null) return;

        var blockRef = archetypeChunk.getReferenceTo(index);
        var blockInfo = store.getComponent(blockRef, BlockModule.BlockStateInfo.getComponentType());

        if (blockInfo == null) return;

        var blockPosition = HytechUtil.getLocalBlockPosition(blockInfo);
        var chunk = store.getComponent(blockInfo.getChunkRef(), WorldChunk.getComponentType());
        if (chunk == null) return;

        var blockType = chunk.getBlockType(blockPosition);
        if (blockType == null) return;

        var blockState = container.getEnergyLevelBlockState();
        if (blockState == null) return;

        chunk.setBlockInteractionState(blockPosition, blockType, blockState);
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return componentType;
    }
}