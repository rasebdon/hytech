package at.rasebdon.hytech.items;

import at.rasebdon.hytech.core.containers.LogisticContainer;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import javax.annotation.Nullable;

/// The odd one out among logistic containers.
///
/// Every other resource -- energy, heat, fluid, gas -- is a scalar and gets
/// [at.rasebdon.hytech.core.containers.ScalarContainer] for free. Items are not: capacity is
/// bounded by slots, and what fits depends on what is already in them, so there is no
/// meaningful "remaining capacity". This implements the bare [LogisticContainer] contract
/// instead and delegates the actual moving to the vanilla [ItemContainer], which already
/// handles slot selection, stack merging and filters.
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

    /// Maximum number of items this container will move per transfer pass.
    @Override
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

    @Override
    default boolean isEmpty() {
        return getUsedSlots() == 0;
    }

    @Override
    default long getAvailable() {
        return getItemCount();
    }

    /// Unbounded on purpose: a free slot takes a whole stack, and a partly filled one takes
    /// an unknown amount of its own item, so there is no scalar answer. The transfer system
    /// sums these with [LogisticContainer#saturatingSum] and clamps by the source instead.
    @Override
    default long getAcceptable() {
        return isFull() ? 0L : Long.MAX_VALUE;
    }

    /// Full in the sense that matters for routing: no free slot left. Partially filled
    /// stacks may still accept more of their own item, which the move call discovers.
    @Override
    default boolean isFull() {
        int slots = getSlotCount();
        return slots > 0 && getUsedSlots() >= slots;
    }

    /// Moves up to `maxItems` items into `target`, returning how many actually moved.
    /// Stack merging and destination slot choice are the vanilla container's job.
    @Override
    default long moveTo(@Nullable LogisticContainer target, long maxItems) {
        if (maxItems <= 0) return 0L;

        // A network only ever holds one container family, so a mismatch is a wiring bug.
        if (!(target instanceof HytechItemContainer itemTarget)) return 0L;

        var from = getItemContainer();
        var to = itemTarget.getItemContainer();
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
