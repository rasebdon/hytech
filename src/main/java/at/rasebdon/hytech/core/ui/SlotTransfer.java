package at.rasebdon.hytech.core.ui;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/// Moving items on a page that cannot be dragged on.
///
/// A window and a custom page are different systems rather than layers: a window lives on the Bench
/// screen, and a custom page *replaces* that screen, so nothing drawn here can be picked up however
/// much it looks like a slot. What a page can do is report clicks -- `ItemSlotButton` fires
/// `Activating` like any other button -- so a move becomes two clicks: pick a slot up, then put it
/// down. Clicking the same slot again, or Cancel Move, drops the selection.
///
/// The engine still performs the move. `moveItemStackFromSlotToSlot` with filtering on is what
/// enforces the rules the rest of the mod already relies on: a machine's result slots carry a
/// `SlotFilter.DENY` on ADD, so a player can take dust out of a crusher but cannot stuff ore into
/// the slot the dust comes out of.
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
    /// What was picked up, for the hint line. Read when the selection is made rather than on every
    /// refresh, so the hint survives the source slot emptying out from under it.
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

    /// One click on a cell.
    ///
    /// Returns true when something happened and the page should redraw. A click on an empty slot
    /// with nothing in hand is not "something happened" -- redrawing then would spend an
    /// acknowledgment round trip to change nothing, and the page drops incoming clicks while an
    /// update is outstanding.
    public boolean click(@Nonnull String zone, int slot, int quantity,
                         @Nonnull Zones zones, @Nonnull Filter filter) {
        var container = zones.container(zone);
        if (container == null || slot < 0 || slot >= container.getCapacity()) return false;

        if (!isPending()) return pickUp(zone, (short) slot, container);

        // Clicking the held cell again puts it back down, which is the cancel every player tries
        // first.
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
        var from = zones.container(this.zone);
        var to = zones.container(toZone);

        // The block can be broken while its page is open, and the page outlives it by up to a
        // refresh. Dropping the selection is the only sane answer.
        if (from == null || to == null || this.slot >= from.getCapacity()) {
            clear();
            return true;
        }

        var stack = from.getItemStack(this.slot);
        if (ItemStack.isEmpty(stack)) {
            clear();
            return true;
        }

        // Refused rather than silently dropped: the selection stays in hand so the player can put
        // it somewhere that will take it, and the rejected click costs them nothing.
        if (!filter.accepts(toZone, toSlot, stack)) return false;

        int moving = Math.min(quantity, stack.getQuantity());

        // Filtering on: this is the same path a pipe and the player's own window take, so a
        // machine's result slots keep refusing insertions and its ingredient slots keep accepting
        // them without this having to know anything about machines.
        from.moveItemStackFromSlotToSlot(this.slot, moving, to, toSlot, true);

        // A partial move leaves the rest behind rather than keeping it in hand: the alternative is
        // a selection that silently points at a different item than the player thinks it does.
        clear();

        return true;
    }

    /// Resolves a zone name to the container behind it. Supplied by the page, because a page knows
    /// about its machine and about the player and this does not.
    @FunctionalInterface
    public interface Zones {
        @Nullable
        ItemContainer container(@Nonnull String zone);
    }

    /// Whether a stack may land in a cell.
    ///
    /// Separate from the container's own filters on purpose. A filter is the right tool for a rule
    /// the container enforces against everyone -- a machine's result slots refuse insertions from
    /// pipes and players alike. This is the rule that only applies to a *person clicking*: the
    /// machine will physically hold cobblestone in its ingredient slot, it just has no recipe for
    /// it, and letting someone jam one in by accident is worse than saying no.
    @FunctionalInterface
    public interface Filter {
        boolean accepts(@Nonnull String zone, int slot, @Nonnull ItemStack stack);
    }
}
