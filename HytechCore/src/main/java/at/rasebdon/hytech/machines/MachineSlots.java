package at.rasebdon.hytech.machines;

import at.rasebdon.hytech.items.components.ItemBlockComponent;
import com.hypixel.hytale.builtin.crafting.component.CraftingManager;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/// A machine's single container, seen as an ingredient half and a result half, since a crusher
/// must not count its own dust output as an ingredient. Matching still defers to
/// [CraftingManager#matches].
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

    // Null, not a whole-container fallback: without InputSlots/OutputSlots a machine would
    // otherwise crush its own output back into dust forever.
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

    /// Binary search rather than summing free space: two outputs can both want the same empty
    /// slot, so free space isn't additive across them.
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

    private boolean fits(List<ItemStack> outputs, int sets) {
        int slots = this.outputTo - this.outputFrom;

        // -1 room marks a slot still empty; the first output to claim it fills it in.
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

            // Part-filled stacks of the same item first, so an empty slot is a last resort.
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

    public void addOutputs(@Nonnull List<ItemStack> outputs, int sets) {
        for (var output : outputs) {
            int remaining = Math.max(1, output.getQuantity()) * sets;

            while (remaining > 0) {
                int before = remaining;

                for (short slot = this.outputFrom; slot < this.outputTo && remaining > 0; slot++) {
                    // withQuantity returns null only for quantity zero, which the loop guards against.
                    var stack = output.withQuantity(remaining);
                    if (stack == null) break;

                    var transaction = this.container.addItemStackToSlot(
                            slot, stack, false, false);
                    if (!transaction.succeeded()) continue;

                    var leftover = transaction.getRemainder();
                    remaining = ItemStack.isEmpty(leftover) ? 0 : leftover.getQuantity();
                }

                // Nothing moved: output full, would otherwise spin forever.
                if (remaining == before) break;
            }
        }
    }

    private static int maxStack(ItemStack stack) {
        return Math.max(1, stack.getItem().getMaxStack());
    }
}
