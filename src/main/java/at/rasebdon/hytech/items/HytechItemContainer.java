package at.rasebdon.hytech.items;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import javax.annotation.Nullable;

/// Item-side counterpart to [at.rasebdon.hytech.energy.HytechEnergyContainer].
///
/// Energy is a scalar, so its container exposes an amount and a capacity. Items are not:
/// capacity is bounded by slots, and what fits depends on what is already in them. So the
/// shared vocabulary here is slot-based, and the actual moving of items is delegated to
/// the vanilla [ItemContainer], which already handles slot selection, stack merging and
/// filters.
public interface HytechItemContainer {

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

    /// Maximum number of items this container will move per transfer tick.
    long getTransferSpeed();

    /* ---------------- Derived values ---------------- */

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

    default boolean isEmpty() {
        return getUsedSlots() == 0;
    }

    /// Full in the sense that matters for routing: no free slot left. Partially filled
    /// stacks may still accept more of their own item, which the move call discovers.
    default boolean isFull() {
        int slots = getSlotCount();
        return slots > 0 && getUsedSlots() >= slots;
    }

    /// Moves up to `maxItems` items into `target`, returning how many actually moved.
    /// Stack merging and destination slot choice are the vanilla container's job.
    default long moveTo(@Nullable HytechItemContainer target, long maxItems) {
        if (target == null || maxItems <= 0) return 0L;

        var from = getItemContainer();
        var to = target.getItemContainer();
        if (from == null || to == null || from == to) return 0L;

        long moved = 0L;

        for (short slot = 0; slot < from.getCapacity() && moved < maxItems; slot++) {
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
