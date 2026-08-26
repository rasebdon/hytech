package at.rasebdon.hytech.items.utils;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.joml.Vector3d;
import org.joml.Vector3ic;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/// Puts the contents of a container on the ground.
///
/// Two paths need this and neither can hand the items anywhere else: a pipe whose run has
/// stopped moving ([at.rasebdon.hytech.items.systems.ItemPipeEjectSystem]) and a pipe the
/// player has just broken. Both end with items that exist but have no container to live in,
/// and losing them silently is the one outcome that is not acceptable.
public final class ItemEjector {

    private ItemEjector() {
    }

    /// Empties `container` onto the ground at the centre of `blockPos`.
    public static void ejectAt(
            @Nullable ItemContainer container,
            Store<ChunkStore> store,
            Vector3ic blockPos
    ) {
        eject(container, store,
                new Vector3d(blockPos.x() + 0.5, blockPos.y() + 0.5, blockPos.z() + 0.5));
    }

    /// Cleared before the drops are spawned: clearing and dropping must not both leave the
    /// items in play. Losing a stack to a failed spawn is better than a half-cleared
    /// container handing out a fresh copy on the next pass.
    public static void eject(
            @Nullable ItemContainer container,
            Store<ChunkStore> store,
            Vector3d position
    ) {
        if (container == null) return;

        var stacks = new ArrayList<ItemStack>(container.getCapacity());

        for (short slot = 0; slot < container.getCapacity(); slot++) {
            var stack = container.getItemStack(slot);
            if (!ItemStack.isEmpty(stack) && stack.isValid()) {
                stacks.add(stack);
            }
        }

        if (stacks.isEmpty()) return;

        container.clear();
        spawnDrops(store, stacks, position);
    }

    private static void spawnDrops(Store<ChunkStore> store, List<ItemStack> stacks, Vector3d position) {
        var entityStore = store.getExternalData().getWorld().getEntityStore().getStore();

        var holders = ItemComponent.generateItemDrops(entityStore, stacks, position, Rotation3f.IDENTITY);
        if (holders.length == 0) return;

        entityStore.addEntities(holders, AddReason.SPAWN);
    }
}
