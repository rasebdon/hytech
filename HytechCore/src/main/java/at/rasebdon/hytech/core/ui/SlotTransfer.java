package at.rasebdon.hytech.core.ui;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/// Moving items on a page that cannot be dragged on: a click picks a slot up, a second click puts
/// it down. `moveItemStackFromSlotToSlot` with filtering on still performs the actual move, so a
/// machine's result slots keep refusing insertions exactly as they do for a pipe or a window.
public final class SlotTransfer {

    /// The machine's own container.
    public static final String ZONE_MACHINE = "m";

    /// The player's main inventory.
    public static final String ZONE_STORAGE = "s";

    /// The player's hotbar.
    public static final String ZONE_HOTBAR = "h";

    /// Move the whole stack. Any smaller number moves exactly that many.
    public static final int WHOLE_STACK = Integer.MAX_VALUE;
    /// Null when nothing is picked up.
    @Nullable
    private String zone;
    private short slot;
    /// What was picked up, for the hint line; captured at selection so it survives the slot emptying.
    @Nullable
    private String held;

    @Nonnull
    private static String describe(@Nonnull ItemStack stack) {
        int quantity = stack.getQuantity();

        return quantity > 1 ? quantity + "x " + stack.getItemId() : stack.getItemId();
    }

    public boolean isPending() {
        return this.zone != null;
    }

    /// Whether this cell is the one currently picked up, so the page can highlight it.
    public boolean isSelected(@Nonnull String zone, int slot) {
        return this.zone != null && this.zone.equals(zone) && this.slot == slot;
    }

    public void clear() {
        this.zone = null;
        this.slot = 0;
        this.held = null;
    }

    /// The line shown beside the inventory: what is in hand, or nothing.
    @Nonnull
    public String hint() {
        if (this.held == null) return "";

        return "Moving " + this.held + " -- click a slot to place it, or Cancel Move.";
    }

    /// Returns true when something happened and the page should redraw.
    public boolean click(@Nonnull String zone, int slot, int quantity,
                         @Nonnull Zones zones, @Nonnull Filter filter) {
        var container = zones.container(zone);
        if (container == null || slot < 0 || slot >= container.getCapacity()) return false;

        if (!isPending()) return pickUp(zone, (short) slot, container);

        if (isSelected(zone, slot)) {
            clear();
            return true;
        }

        return place(zone, (short) slot, quantity, zones, filter);
    }

    private boolean pickUp(@Nonnull String zone, short slot, @Nonnull ItemContainer container) {
        var stack = container.getItemStack(slot);
        if (ItemStack.isEmpty(stack)) return false;

        this.zone = zone;
        this.slot = slot;
        this.held = describe(stack);

        return true;
    }

    private boolean place(@Nonnull String toZone, short toSlot, int quantity,
                          @Nonnull Zones zones, @Nonnull Filter filter) {
        var fromZone = this.zone;
        var from = fromZone == null ? null : zones.container(fromZone);
        var to = zones.container(toZone);

        // The block can be broken while its page is open; dropping the selection is the only sane answer.
        if (from == null || to == null || this.slot >= from.getCapacity()) {
            clear();
            return true;
        }

        var stack = from.getItemStack(this.slot);
        if (ItemStack.isEmpty(stack)) {
            clear();
            return true;
        }

        // Refused, not dropped: the selection stays in hand so the player can retry elsewhere.
        if (!filter.accepts(toZone, toSlot, stack)) return false;

        int moving = Math.min(quantity, stack.getQuantity());

        from.moveItemStackFromSlotToSlot(this.slot, moving, to, toSlot, true);

        clear();

        return true;
    }

    /// Resolves a zone name to the container behind it. Supplied by the page, which knows about
    /// its machine and the player.
    @FunctionalInterface
    public interface Zones {
        @Nullable
        ItemContainer container(@Nonnull String zone);
    }

    /// Whether a stack may land in a cell — separate from the container's own filters, which
    /// apply to everyone; this is the rule for a person clicking specifically.
    @FunctionalInterface
    public interface Filter {
        boolean accepts(@Nonnull String zone, int slot, @Nonnull ItemStack stack);
    }
}
