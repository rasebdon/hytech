package at.rasebdon.hytech.items;

import at.rasebdon.hytech.core.containers.LogisticContainer;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import javax.annotation.Nullable;

/// Bare [LogisticContainer], not [at.rasebdon.hytech.core.containers.ScalarContainer]: slot
/// capacity depends on what's already in the slots, so there's no scalar "remaining capacity".
public interface HytechItemContainer extends LogisticContainer {

    private static long movedQuantity(
            @Nullable com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction add,
            int requested) {
        if (add == null) return 0L;

        var query = add.getQuery();
        var remainder = add.getRemainder();

        int asked = ItemStack.isEmpty(query) ? requested : query.getQuantity();
        int left = ItemStack.isEmpty(remainder) ? 0 : remainder.getQuantity();

        return Math.max(0, asked - left);
    }

    @Nullable
    ItemContainer getItemContainer();

    @Override
    long getTransferSpeed();

    default int getSlotCount() {
        var container = getItemContainer();
        return container == null ? 0 : container.getCapacity();
    }

    default long getItemCount() {
        var container = getItemContainer();
        if (container == null) return 0L;

        long total = 0L;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            var stack = container.getItemStack(slot);
            if (!ItemStack.isEmpty(stack)) {
                total += stack.getQuantity();
            }
        }
        return total;
    }

    default int getUsedSlots() {
        var container = getItemContainer();
        if (container == null) return 0;

        int used = 0;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            if (!ItemStack.isEmpty(container.getItemStack(slot))) {
                used++;
            }
        }
        return used;
    }

    @Override
    default boolean isEmpty() {
        return getUsedSlots() == 0;
    }

    @Override
    default long getAvailable() {
        return getItemCount();
    }

    /// Unbounded on purpose: no scalar "remaining capacity" exists. Sum with
    /// [LogisticContainer#saturatingSum], not `+`.
    @Override
    default long getAcceptable() {
        return isFull() ? 0L : Long.MAX_VALUE;
    }

    /// Full only when every slot is at its item's max stack size, not merely occupied -- else a
    /// single-slot machine with a partial stack reads as full and starves.
    @Override
    default boolean isFull() {
        var container = getItemContainer();
        if (container == null) return false;

        short capacity = container.getCapacity();
        if (capacity <= 0) return false;

        for (short slot = 0; slot < capacity; slot++) {
            var stack = container.getItemStack(slot);
            if (ItemStack.isEmpty(stack)) return false;

            // Item.UNKNOWN's max stack can be 0; treat as closed, not infinitely deep.
            int maxStack = stack.getItem().getMaxStack();
            if (stack.getQuantity() < Math.max(1, maxStack)) return false;
        }

        return true;
    }

    /// A machine returns false for its input slots, so a pipe on an OUTPUT face can't carry
    /// unprocessed ore back out. Insertion needs no such hook; the container's ADD filters cover it.
    default boolean canExtractFrom(short slot) {
        return true;
    }

    @Override
    default long moveTo(@Nullable LogisticContainer target, long maxItems) {
        if (maxItems <= 0) return 0L;

        if (!(target instanceof HytechItemContainer itemTarget)) return 0L;

        var from = getItemContainer();
        var to = itemTarget.getItemContainer();
        if (from == null || to == null || from == to) return 0L;

        long moved = 0L;

        for (short slot = 0; slot < from.getCapacity() && moved < maxItems; slot++) {
            if (!canExtractFrom(slot)) continue;

            var stack = from.getItemStack(slot);
            if (ItemStack.isEmpty(stack)) continue;

            int wanted = (int) Math.min(stack.getQuantity(), maxItems - moved);
            if (wanted <= 0) break;

            var transaction = from.moveItemStackFromSlot(slot, wanted, to);
            if (!transaction.succeeded()) continue;

            moved += movedQuantity(transaction.getAddTransaction(), wanted);
        }

        return moved;
    }
}
