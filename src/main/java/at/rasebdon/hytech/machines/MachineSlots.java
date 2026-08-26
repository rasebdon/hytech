package at.rasebdon.hytech.machines;

import at.rasebdon.hytech.items.components.ItemBlockComponent;
import com.hypixel.hytale.builtin.crafting.component.CraftingManager;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/// A machine's container, seen as an ingredient half and a result half.
///
/// Vanilla's container helpers -- `getSlotMaterialsToRemove`, `canAddItemStacks` -- work on a whole
/// container, and a machine's is deliberately one container so item pipes and the player's window
/// see a single thing. Scoping matters though: a crusher must not count the dust in its output as
/// an ingredient, and must not pile its results into the ore slot. So the slot-range arithmetic
/// lives here, once, and the matching itself still defers to [CraftingManager#matches] so a Hytech
/// machine reads a recipe exactly as a vanilla bench does.
public final class MachineSlots {

    private final ItemContainer container;
    private final short inputFrom;
    private final short inputTo;
    private final short outputFrom;
    private final short outputTo;

    private MachineSlots(ItemContainer container, short inputFrom, short inputTo,
                         short outputFrom, short outputTo) {
        this.container = container;
        this.inputFrom = inputFrom;
        this.inputTo = inputTo;
        this.outputFrom = outputFrom;
        this.outputTo = outputTo;
    }

    /// The split a machine's item component declares, or null when it declares none.
    ///
    /// Null rather than a whole-container fallback on purpose: a machine with no `InputSlots` /
    /// `OutputSlots` would otherwise crush its own output back into dust forever.
    @Nullable
    public static MachineSlots of(@Nonnull ItemBlockComponent items) {
        var container = items.getItemContainer();
        if (container == null) return null;

        short inputs = items.getInputSlots();
        short outputs = items.getOutputSlots();
        if (inputs <= 0 || outputs <= 0) return null;

        short capacity = container.getCapacity();
        if (capacity < inputs + outputs) return null;

        return new MachineSlots(container, (short) 0, inputs, inputs, (short) (inputs + outputs));
    }

    /// How many whole sets of `materials` the ingredient slots hold, capped at `limit`.
    ///
    /// Counting sets rather than answering yes/no is what makes a factory tier possible: the
    /// machine completes as many operations at once as it has both ingredients and room for.
    public int countInputSets(@Nonnull List<MaterialQuantity> materials, int limit) {
        if (materials.isEmpty() || limit <= 0) return 0;

        int sets = limit;

        for (var material : materials) {
            int required = Math.max(1, material.getQuantity());
            long available = 0L;

            for (short slot = this.inputFrom; slot < this.inputTo; slot++) {
                var stack = this.container.getItemStack(slot);
                if (ItemStack.isEmpty(stack) || !CraftingManager.matches(material, stack)) continue;

                available += stack.getQuantity();
            }

            sets = (int) Math.min(sets, available / required);
            if (sets <= 0) return 0;
        }

        return sets;
    }

    /// The largest number of `sets` of `outputs` the result slots can actually take, so a full
    /// machine stops instead of destroying what it made.
    ///
    /// Found by halving rather than by summing free space, because free space is not additive
    /// across outputs: two results both want the one empty slot, and counting a whole stack of
    /// room for each of them would promise space that is not there. Vanilla's bench narrows the
    /// same way in `advanceProcessing`.
    public int fittingOutputSets(@Nonnull List<ItemStack> outputs, int sets) {
        if (outputs.isEmpty() || sets <= 0) return 0;

        int low = 0;
        int high = sets;

        while (low < high) {
            int mid = (low + high + 1) / 2;

            if (fits(outputs, mid)) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }

        return low;
    }

    /// Whether `sets` of every output can be placed, allocating slot by slot so each output only
    /// gets the room the ones before it left behind.
    private boolean fits(List<ItemStack> outputs, int sets) {
        int slots = this.outputTo - this.outputFrom;

        // Room left in each slot, and what it holds. -1 room marks a slot still empty, which the
        // first output to claim it fills in.
        int[] room = new int[slots];
        String[] held = new String[slots];

        for (short slot = this.outputFrom; slot < this.outputTo; slot++) {
            int index = slot - this.outputFrom;
            var stack = this.container.getItemStack(slot);

            if (ItemStack.isEmpty(stack)) {
                room[index] = -1;
                continue;
            }

            held[index] = stack.getItemId();
            room[index] = Math.max(0, maxStack(stack) - stack.getQuantity());
        }

        for (var output : outputs) {
            int needed = Math.max(1, output.getQuantity()) * sets;
            int maxStack = Math.max(1, output.getItem().getMaxStack());

            // Part-filled stacks of the same item first, the way a container's own add does, so an
            // empty slot is only spent when there is no other home for the items.
            for (int index = 0; index < slots && needed > 0; index++) {
                if (room[index] == -1 || !output.getItemId().equals(held[index])) continue;

                int take = Math.min(needed, room[index]);
                room[index] -= take;
                needed -= take;
            }

            for (int index = 0; index < slots && needed > 0; index++) {
                if (room[index] != -1) continue;

                int take = Math.min(needed, maxStack);
                held[index] = output.getItemId();
                room[index] = maxStack - take;
                needed -= take;
            }

            if (needed > 0) return false;
        }

        return true;
    }

    /// Removes `sets` worth of `materials` from the ingredient slots.
    ///
    /// Unfiltered: the machine is the container's owner, and the filters exist to keep *other*
    /// things out rather than to police the machine's own bookkeeping.
    public void consumeInputs(@Nonnull List<MaterialQuantity> materials, int sets) {
        for (var material : materials) {
            int remaining = Math.max(1, material.getQuantity()) * sets;

            for (short slot = this.inputFrom; slot < this.inputTo && remaining > 0; slot++) {
                var stack = this.container.getItemStack(slot);
                if (ItemStack.isEmpty(stack) || !CraftingManager.matches(material, stack)) continue;

                int take = Math.min(remaining, stack.getQuantity());
                this.container.removeItemStackFromSlot(slot, take, false, false);
                remaining -= take;
            }
        }
    }

    /// Writes `sets` worth of `outputs` into the result slots, merging into part-filled stacks
    /// first the way a bench does.
    public void addOutputs(@Nonnull List<ItemStack> outputs, int sets) {
        for (var output : outputs) {
            int remaining = Math.max(1, output.getQuantity()) * sets;

            while (remaining > 0) {
                int before = remaining;

                for (short slot = this.outputFrom; slot < this.outputTo && remaining > 0; slot++) {
                    var transaction = this.container.addItemStackToSlot(
                            slot, output.withQuantity(remaining), false, false);
                    if (!transaction.succeeded()) continue;

                    var leftover = transaction.getRemainder();
                    remaining = ItemStack.isEmpty(leftover) ? 0 : leftover.getQuantity();
                }

                // Nothing moved anywhere: the output is full, and looping again would spin
                // forever. fittingOutputSets should have caught this, so say so.
                if (remaining == before) break;
            }
        }
    }

    private static int maxStack(ItemStack stack) {
        return Math.max(1, stack.getItem().getMaxStack());
    }
}
