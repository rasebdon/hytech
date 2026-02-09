package at.rasebdon.hytech.energy.systems.visual;

import at.rasebdon.hytech.core.util.HytechUtil;
import at.rasebdon.hytech.energy.components.EnergyBlockComponent;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jspecify.annotations.NonNull;

public class EnergyBlockStateSystem extends TickingSystem<ChunkStore> {
    private static final float UPDATE_INTERVAL_SECONDS = 1f;
    private final ComponentType<ChunkStore, EnergyBlockComponent> componentType;
    private float updateTime;

    public EnergyBlockStateSystem(
            ComponentType<ChunkStore, EnergyBlockComponent> componentType) {
        this.componentType = componentType;
        this.updateTime = 0f;
    }

    @Override
    public void tick(float dt, int systemIndex, @NonNull Store<ChunkStore> store) {
        if (this.updateTime < UPDATE_INTERVAL_SECONDS) {
            this.updateTime += dt;
            return;
        }

        this.updateTime = 0f;

        store.forEachChunk(componentType, (chunk, buffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                updateBlock(store, chunk, i);
            }
        });
    }

    void updateBlock(
            Store<ChunkStore> store,
            ArchetypeChunk<ChunkStore> archetypeChunk,
            int index) {
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
}