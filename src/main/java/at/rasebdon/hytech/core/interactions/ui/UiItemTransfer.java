package at.rasebdon.hytech.core.interactions.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/// Moves items between a player and a machine's container in response to UI events.
///
/// HyUI's item grid is a *rendered view plus events*, not a binding onto a server container:
/// a drop tells us what the client thinks was dropped, and the actual transaction is ours to
/// perform. So nothing here trusts the event's slot indices -- it re-resolves the stack
/// against the player's real inventory, which means a forged or stale event can at worst move
/// items the player genuinely has.
public final class UiItemTransfer {

    private UiItemTransfer() {
    }

    /// Moves up to `quantity` of `itemId` from the player into `target`.
    ///
    /// Returns how many actually moved. Anything the target could not accept is returned to
    /// the player, so a full machine cannot eat a stack.
    public static int intoContainer(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerRef,
            @Nullable String itemId,
            int quantity,
            @Nullable ItemContainer target) {

        if (target == null || itemId == null || itemId.isBlank() || quantity <= 0) return 0;

        var inventory = playerInventory(store, playerRef);
        if (inventory == null) return 0;

        // Take from the player first: if this fails there is nothing to give away, and no
        // state has changed.
        var taken = inventory.removeItemStack(new ItemStack(itemId, quantity), false, true);
        int removed = movedQuantity(taken, quantity);
        if (removed <= 0) return 0;

        var added = target.addItemStack(new ItemStack(itemId, removed), false, false, true);
        int accepted = movedQuantity(added, removed);

        // Hand back whatever would not fit rather than destroying it.
        int rejected = removed - accepted;
        if (rejected > 0) {
            inventory.addItemStack(new ItemStack(itemId, rejected), false, false, true);
        }

        return accepted;
    }

    /// Moves up to `quantity` from `source` slot `slot` back to the player.
    ///
    /// Returns how many moved. Anything the player cannot carry stays in the machine.
    public static int toPlayer(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerRef,
            @Nullable ItemContainer source,
            short slot,
            int quantity) {

        if (source == null || quantity <= 0) return 0;
        if (slot < 0 || slot >= source.getCapacity()) return 0;

        var stack = source.getItemStack(slot);
        if (ItemStack.isEmpty(stack)) return 0;

        var inventory = playerInventory(store, playerRef);
        if (inventory == null) return 0;

        int wanted = Math.min(quantity, stack.getQuantity());

        var transaction = source.moveItemStackFromSlot(slot, wanted, inventory);
        if (transaction == null || !transaction.succeeded()) return 0;

        return movedQuantity(transaction.getAddTransaction(), wanted);
    }

    @Nullable
    private static ItemContainer playerInventory(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerRef) {

        if (!playerRef.isValid()) return null;

        return InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
    }

    /// How much a transaction actually moved.
    ///
    /// A transaction reports what it was asked for and what it could not place, so the
    /// difference is the amount that landed. Both can be empty, which is why neither is read
    /// directly.
    private static int movedQuantity(
            @Nullable com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction transaction,
            int requested) {

        if (transaction == null) return 0;

        var query = transaction.getQuery();
        var remainder = transaction.getRemainder();

        int asked = ItemStack.isEmpty(query) ? requested : query.getQuantity();
        int left = ItemStack.isEmpty(remainder) ? 0 : remainder.getQuantity();

        return Math.max(0, asked - left);
    }
}
