package at.rasebdon.hytech.items.systems;

import at.rasebdon.hytech.core.util.HytechUtil;
import at.rasebdon.hytech.items.components.HytechItemContainerWrapper;
import at.rasebdon.hytech.items.utils.ItemUtils;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ItemBlockStateRegistrationSystem extends RefSystem<ChunkStore> {

    private final ItemComponentRegistrationSystem logisticRegistrationSystem;

    public ItemBlockStateRegistrationSystem(ItemComponentRegistrationSystem logisticRegistrationSystem) {
        this.logisticRegistrationSystem = logisticRegistrationSystem;
    }

    @Override
    public void onEntityAdded(
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull AddReason addReason,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        var blockTransform = HytechUtil.getBlockTransform(ref, store);
        if (blockTransform != null) {
            var world = store.getExternalData().getWorld();
            var itemContainer = ItemUtils.getLegacyItemContainer(world, blockTransform.worldPos());

            if (itemContainer != null) {
                var containerWrapper = new HytechItemContainerWrapper(itemContainer);
                logisticRegistrationSystem.registerLegacyContainer(containerWrapper, ref, store);
            }
        }
    }

    @Override
    public void onEntityRemove(
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull RemoveReason removeReason,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        var blockTransform = HytechUtil.getBlockTransform(ref, store);
        if (blockTransform != null) {
            var world = store.getExternalData().getWorld();
            var itemContainer = ItemUtils.getLegacyItemContainer(world, blockTransform.worldPos());

            if (itemContainer != null) {
                logisticRegistrationSystem.unregisterLegacyContainer(itemContainer, ref, store);
            }
        }
    }

    @Override
    public @Nullable Query<ChunkStore> getQuery() {
        return Query.any();
    }
}
