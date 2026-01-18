package com.rasebdon.hytech.energy;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class EnergyModule
{
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    @Nullable
    private static EnergyModule INSTANCE;

    @Nonnull
    private final ComponentType<ChunkStore, EnergyContainerComponent> energyContainerComponentType;
    public ComponentType<ChunkStore, EnergyContainerComponent> getEnergyContainerComponentType() {
        return this.energyContainerComponentType;
    }

    private EnergyModule(@Nonnull ComponentRegistryProxy<ChunkStore> registry) {
        this.energyContainerComponentType = registry.registerComponent(
                EnergyContainerComponent.class, "hytech:energy:container", EnergyContainerComponent.CODEC);
        registry.registerSystem(new EnergyTransferSystem(this.energyContainerComponentType));
        LOGGER.atInfo().log("Registered: " + this.energyContainerComponentType);
    }

    public static void init(@Nonnull ComponentRegistryProxy<ChunkStore> registry) {
        if (INSTANCE != null) {
            throw new IllegalStateException("Energy Module already initialized.");
        } else {
            INSTANCE = new EnergyModule(registry);
        }
    }

    @Nonnull
    public static EnergyModule get() {
        if (INSTANCE == null) {
            throw new IllegalStateException("Energy Module not initialized.");
        } else {
            return INSTANCE;
        }
    }

    public static void reset() {
        INSTANCE = null;
    }

    public static class EnergyBlockChunkSystem extends RefSystem<ChunkStore> {


        @Override
        public void onEntityAdded(@NotNull Ref<ChunkStore> ref,
                                  @NotNull AddReason reason,
                                  @NotNull Store<ChunkStore> store,
                                  @NotNull CommandBuffer<ChunkStore> commandBuffer) {
            LOGGER.atInfo().log("Test");

            //var blockInfo = store.getComponent(ref, query);

//            var blockEntity = store.getComponent(entityRef, BlockEntity.getComponentType());
//            assert blockEntity != null;
//
//            var blockType = BlockType.getAssetMap().getAsset(blockEntity.getBlockTypeKey());
//            assert blockType != null;
//
//            LOGGER.atInfo().log("Block {} entity added", blockType.getId());
        }

        @Override
        public void onEntityRemove(@NotNull Ref<ChunkStore> entityRef,
                                   @NotNull RemoveReason reason,
                                   @NotNull Store<ChunkStore> store,
                                   @NotNull CommandBuffer<ChunkStore> commandBuffer) {

        }

        @Nonnull
        @Override
        public Query<ChunkStore> getQuery() {
            return Archetype.of();
        }
    }

    public static class EnergyBlockPlaceBlockEventSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
        private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

        public EnergyBlockPlaceBlockEventSystem() {
            super(PlaceBlockEvent.class);
        }

        public void handle(
                int index,
                @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> commandBuffer,
                @Nonnull PlaceBlockEvent event
        ) {
            var world = commandBuffer.getExternalData().getWorld();
            var targetBlock = event.getTargetBlock();

            // Use this method when block is already placed
            // var blockType = world.getBlockType(targetBlock);

            var blockItem = event.getItemInHand();
            assert blockItem != null;

            var blockType = BlockType.getAssetMap().getAsset(blockItem.getBlockKey());
            assert blockType != null;

            blockType.getData().getRawTags().forEach(
                    (k, v) -> LOGGER.atInfo().log(k + " " + String.join(", ", v))
            );

//            blockType.getItem().getData().get
//
//            // Player entity ref
//            var entityRef = archetypeChunk.getReferenceTo(index);
//            var displayName = store.getComponent(entityRef, DisplayNameComponent.getComponentType());
//
//
//            if (displayName != null)
//            {
//                assert displayName.getDisplayName() != null;
//                assert event.getItemInHand() != null;
//                blockType.getStateForBlock()
//
//                var message = Message.raw("%s placed %s at %d %d %d".formatted(displayName.getDisplayName().getRawText(),
//                        blockItem.getBlockKey(), targetBlock.x, targetBlock.y, targetBlock.z));
//                world.getPlayerRefs().forEach(p -> p.sendMessage(message));
//            }
//
//            Holder<EntityStore> entityStoreHolder = store.getRegistry().newHolder();
//            entityStoreHolder.addComponent();
//            store.addEntity(entityStoreHolder, AddReason.SPAWN);
        }

        @Nullable
        @Override
        public Query<EntityStore> getQuery() {
            return Archetype.empty();
        }
    }
}
