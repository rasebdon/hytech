package at.rasebdon.hytech.core.ui;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

/// What a machine puts on its page, written once per refresh.
///
/// The page document declares every section; a machine fills in the ones it has and this hides the
/// rest. So a battery, a fluid tank and the burner generator all render through the same document
/// and the same code, and a new resource type needs neither.
///
/// This is also the *only* writer on the page. Everything -- the side configurator, the player's
/// inventory, the machine's own readouts -- goes through [#write], because every value sent has to
/// land in the change signature. A page that updates when nothing moved eats its own button clicks:
/// `updateCustomPage` increments an outstanding-acknowledgment counter and `PageManager` drops
/// incoming Data events while that counter is non-zero.
public final class MachineView {

    /// Detail rows the document declares.
    private static final int DETAIL_ROWS = 6;

    /// Ingredient and result cells in the split view. Four each is generous for what the mod
    /// ships -- the widest is the smelter's two in, two out -- and a machine wanting more slots
    /// than that wants the undivided grid.
    private static final int SPLIT_CELLS = 4;

    /// Cells in the undivided view, for a fuel slot or an item buffer.
    private static final int FLAT_CELLS = 12;

    /// Player inventory cells: thirty-six storage, then nine hotbar.
    private static final int STORAGE_CELLS = 36;
    private static final int HOTBAR_CELLS = 9;

    /// Cell backgrounds. Duplicated from `Hytech.ui`, which paints the same values on open --
    /// the document owns the resting look, and this owns it from the first refresh onwards.
    private static final String CELL_IDLE = "#1b2530";
    private static final String CELL_IDLE_HOVER = "#2a3846";
    private static final String CELL_HELD = "#c9a050";
    private static final String CELL_HELD_HOVER = "#e0bb68";

    private final UICommandBuilder commands;

    /// Everything written this pass, so the page can skip an update that would change nothing.
    /// Cheap and exact: the values are the strings and numbers already being sent.
    private final StringBuilder signature = new StringBuilder();

    /// Which container and slot each drawn cell stands for, so a click on `#OutSlot2` can be
    /// resolved without the page having to re-derive a machine's ingredient/result split.
    private final Map<String, SlotRef> cells = new HashMap<>();

    /// Which of the machine's slots take ingredients.
    ///
    /// What lets a click-transfer refuse junk: a container filter already stops items entering a
    /// *result* slot, but nothing stops a player putting cobblestone in a crusher, and a machine
    /// sitting full of something it cannot process looks broken.
    private final Set<Integer> ingredientSlots = new HashSet<>();

    @Nullable
    private final SlotTransfer transfer;

    /// The cell painted as held last pass, so exactly two style writes repaint the selection
    /// instead of four per cell across a hundred of them.
    @Nullable
    private final String previouslyHeld;

    @Nullable
    private String held;

    /// The machine's own test for an item it cannot use, kept so the page can refuse a transfer
    /// into an ingredient slot rather than only colouring the summary.
    @Nullable
    private Predicate<ItemStack> incompatible;

    private boolean primaryShown;
    private boolean secondaryShown;
    private boolean slotsShown;
    private boolean splitShown;
    private boolean flatShown;
    private boolean progressShown;
    private boolean inventoryShown;
    private int detailsUsed;

    MachineView(@Nonnull UICommandBuilder commands,
                @Nullable SlotTransfer transfer,
                @Nullable String previouslyHeld) {
        this.commands = commands;
        this.transfer = transfer;
        this.previouslyHeld = previouslyHeld;
    }

    /// "12 items" / "Empty" / "8 items (unusable)".
    @Nonnull
    private static String summarise(@Nonnull ItemContainer container,
                                    @Nullable Predicate<ItemStack> incompatible) {
        int total = 0;
        int unusable = 0;

        for (short slot = 0; slot < container.getCapacity(); slot++) {
            var stack = container.getItemStack(slot);
            if (ItemStack.isEmpty(stack)) continue;

            total += stack.getQuantity();

            if (incompatible != null && incompatible.test(stack)) {
                unusable += stack.getQuantity();
            }
        }

        if (total == 0) return "Empty";

        // Naming the unusable portion is the point: otherwise a full machine that is not running
        // looks broken rather than mis-loaded.
        if (unusable == total) return total + " items (unusable)";
        if (unusable > 0) return String.format("%d items (%d unusable)", total, unusable);

        return total + " items";
    }

    // -------------------------------------------------------------------------------------------
    // Primitives
    // -------------------------------------------------------------------------------------------

    /// Writes one value and records it in the change signature.
    ///
    /// Public because the side configurator writes through this view rather than touching the
    /// command builder: a value written around the signature is a value that can go stale on the
    /// page without the refresh ever noticing.
    public void write(@Nonnull String selector, @Nonnull String value) {
        this.commands.set(selector, value);
        this.signature.append(selector).append('=').append(value).append(';');
    }

    public void write(@Nonnull String selector, float value) {
        this.commands.set(selector, value);
        this.signature.append(selector).append('=').append(value).append(';');
    }

    public void write(@Nonnull String selector, boolean value) {
        this.commands.set(selector, value);
        this.signature.append(selector).append('=').append(value).append(';');
    }

    /// A signature of this pass, for change detection.
    @Nonnull
    String signature() {
        return this.signature.toString();
    }

    /// What each cell drawn this pass stands for.
    @Nonnull
    Map<String, SlotRef> cells() {
        return this.cells;
    }

    /// Slots of the machine's own container that take ingredients.
    @Nonnull
    Set<Integer> ingredientSlots() {
        return this.ingredientSlots;
    }

    /// The cell painted as held this pass, to be handed back next time.
    @Nullable
    String held() {
        return this.held;
    }

    /// What this machine will not accept, or null when it accepts anything.
    @Nullable
    Predicate<ItemStack> incompatible() {
        return this.incompatible;
    }

    public void title(@Nonnull String text) {
        write("#TitleLabel.Text", text);
    }

    // -------------------------------------------------------------------------------------------
    // Readouts
    // -------------------------------------------------------------------------------------------

    /// The machine's headline number: what it holds, and how full.
    public void primary(@Nonnull String heading, @Nonnull String value, float ratio,
                        @Nonnull String caption) {
        this.primaryShown = true;

        write("#PrimaryHeading.Text", heading);
        write("#PrimaryValue.Text", value);
        write("#PrimaryBar.Value", clamp(ratio));
        write("#PrimaryCaption.Text", caption);
    }

    /// A second bar: burn progress, sunlight, altitude. Omit and the section disappears.
    public void secondary(@Nonnull String heading, float ratio, @Nonnull String caption) {
        this.secondaryShown = true;

        write("#SecondaryHeading.Text", heading);
        write("#SecondaryBar.Value", clamp(ratio));
        write("#SecondaryCaption.Text", caption);
    }

    /// One label/value line. Extra calls beyond what the document declares are ignored rather than
    /// throwing, since a machine adding a seventh stat should not take its page down.
    public void detail(@Nonnull String label, @Nonnull String value) {
        if (this.detailsUsed >= DETAIL_ROWS) return;

        int row = this.detailsUsed++;

        write("#Detail" + row + "Label.Text", label);
        write("#Detail" + row + "Value.Text", value);
    }

    /// Whether a Configure Sides button makes sense for this block.
    public void configurable(boolean canConfigure) {
        write("#ConfigureButton.Visible", canConfigure);
    }

    // -------------------------------------------------------------------------------------------
    // Contents
    // -------------------------------------------------------------------------------------------

    /// The machine's own slots.
    ///
    /// A machine that declares an ingredient half and a result half gets the split view: inputs,
    /// an arrow, outputs. Everything else -- the burner's fuel slot, an item buffer -- gets one
    /// undivided grid, which is what `inputSlots` and `outputSlots` of zero mean.
    public void slots(@Nonnull String heading,
                      @Nullable ItemContainer container,
                      int inputSlots,
                      int outputSlots,
                      @Nullable Predicate<ItemStack> incompatible) {

        this.incompatible = incompatible;

        write("#SlotsHeading.Text", heading);

        if (container == null) {
            // The panel stays: a battery with the middle column missing leaves a hole in the
            // layout, and "no item storage" is a more useful thing to say than nothing at all.
            write("#SlotsSummary.Text", "This block has no item storage.");
            hideCells("#InSlot", 0, SPLIT_CELLS);
            hideCells("#OutSlot", 0, SPLIT_CELLS);
            hideCells("#FlatSlot", 0, FLAT_CELLS);
            return;
        }

        this.slotsShown = true;

        write("#SlotsSummary.Text", summarise(container, incompatible));

        boolean split = inputSlots > 0 && outputSlots > 0
                && container.getCapacity() >= inputSlots + outputSlots;

        if (split) {
            this.splitShown = true;

            int inputs = Math.min(inputSlots, SPLIT_CELLS);
            int outputs = Math.min(outputSlots, SPLIT_CELLS);

            drawCells(container, "#InSlot", 0, inputs, 0, SlotTransfer.ZONE_MACHINE, true);
            hideCells("#InSlot", inputs, SPLIT_CELLS);

            drawCells(container, "#OutSlot", inputSlots, outputs, 0, SlotTransfer.ZONE_MACHINE, false);
            hideCells("#OutSlot", outputs, SPLIT_CELLS);

            hideCells("#FlatSlot", 0, FLAT_CELLS);
        } else {
            this.flatShown = true;

            int shown = Math.min(container.getCapacity(), FLAT_CELLS);

            // Undivided: every slot takes what goes in, so every cell is an ingredient cell.
            // That is what makes the burner refuse a non-fuel through the same path.
            drawCells(container, "#FlatSlot", 0, shown, 0, SlotTransfer.ZONE_MACHINE, true);
            hideCells("#FlatSlot", shown, FLAT_CELLS);

            hideCells("#InSlot", 0, SPLIT_CELLS);
            hideCells("#OutSlot", 0, SPLIT_CELLS);
        }
    }

    /// How far through the current operation the machine is, drawn between the two halves.
    public void progress(float ratio, @Nonnull String caption) {
        this.progressShown = true;

        write("#ProgressBar.Value", clamp(ratio));
        write("#ProgressCaption.Text", caption);
    }

    /// The player's own inventory along the bottom of the page.
    ///
    /// Storage first, then the hotbar, in one flat run of cells -- the same order the document
    /// declares them, so the panel needs no layout knowledge here.
    public void inventory(@Nullable ItemContainer storage, @Nullable ItemContainer hotbar) {
        this.inventoryShown = storage != null || hotbar != null;

        int stored = storage == null ? 0 : Math.min(storage.getCapacity(), STORAGE_CELLS);
        if (storage != null) {
            drawCells(storage, "#Inv", 0, stored, 0, SlotTransfer.ZONE_STORAGE, false);
        }
        hideCells("#Inv", stored, STORAGE_CELLS);

        int quick = hotbar == null ? 0 : Math.min(hotbar.getCapacity(), HOTBAR_CELLS);
        if (hotbar != null) {
            drawCells(hotbar, "#Inv", 0, quick, STORAGE_CELLS, SlotTransfer.ZONE_HOTBAR, false);
        }
        hideCells("#Inv", STORAGE_CELLS + quick, STORAGE_CELLS + HOTBAR_CELLS);

        write("#TransferHint.Text", this.transfer == null ? "" : this.transfer.hint());
        write("#CancelTransferButton.Visible", this.transfer != null && this.transfer.isPending());
    }

    /// Draws `count` cells, `prefix`+`firstCell` onwards, from `firstSlot` of `container`.
    ///
    /// Values only -- the cells exist in the document and are hidden when unused. Clearing and
    /// re-appending children would restructure the page on every refresh, and a page that keeps
    /// sending structural updates is a page whose button clicks get dropped.
    private void drawCells(@Nonnull ItemContainer container, @Nonnull String prefix,
                           int firstSlot, int count, int firstCell, @Nonnull String zone,
                           boolean ingredient) {

        for (int index = 0; index < count; index++) {
            int slot = firstSlot + index;
            if (slot >= container.getCapacity()) break;

            String cell = prefix + (firstCell + index);

            this.cells.put(cell, new SlotRef(zone, slot));
            if (ingredient) this.ingredientSlots.add(slot);

            var stack = container.getItemStack((short) slot);
            boolean filled = !ItemStack.isEmpty(stack);

            write(cell + ".Visible", true);
            write(cell + " #Icon.Visible", filled);

            if (filled) {
                write(cell + " #Icon.ItemId", stack.getItemId());

                // Drawn as a label rather than by the slot: a page-hosted ItemSlot rejects
                // `.Quantity` with a "CustomUI Set command error", which is also why vanilla's own
                // DroppedItemSlot carries a label of its own.
                write(cell + " #Count.Text",
                        stack.getQuantity() > 1 ? String.valueOf(stack.getQuantity()) : "");
            } else {
                write(cell + " #Count.Text", "");
            }

            if (this.transfer != null && this.transfer.isSelected(zone, slot)) {
                this.held = cell;
            }
        }

    }

    private void hideCells(@Nonnull String prefix, int from, int to) {
        for (int cell = from; cell < to; cell++) {
            write(prefix + cell + ".Visible", false);
        }
    }

    /// Hides everything the machine did not fill in, and repaints the held cell. Called after the
    /// machine has had its say.
    void finish() {
        write("#PrimarySection.Visible", this.primaryShown);
        write("#SecondarySection.Visible", this.secondaryShown);
        write("#SplitSlots.Visible", this.splitShown);
        write("#FlatSlots.Visible", this.flatShown);
        write("#ProgressColumn.Visible", this.progressShown);
        write("#DetailSection.Visible", this.detailsUsed > 0);

        // The contents column and the inventory stand or fall together. A battery has nothing you
        // could move an item into, so showing the player's inventory beneath it would be offering
        // a control that cannot do anything -- and the remaining columns flex to take the space.
        write("#ProcessPanel.Visible", this.slotsShown);
        write("#InventorySection.Visible", this.slotsShown && this.inventoryShown);

        for (int row = this.detailsUsed; row < DETAIL_ROWS; row++) {
            write("#Detail" + row + ".Visible", false);
        }

        repaintSelection();
    }

    // -------------------------------------------------------------------------------------------
    // Finish
    // -------------------------------------------------------------------------------------------

    /// Two style writes rather than four per cell.
    ///
    /// A page carrying the player's inventory draws over fifty cells; repainting every one of them
    /// every second to say "still not selected" would triple the update for no visible difference.
    /// Only the cell that gained the highlight and the one that lost it need touching.
    private void repaintSelection() {
        if (Objects.equals(this.previouslyHeld, this.held)) return;

        if (this.previouslyHeld != null) {
            write(this.previouslyHeld + ".Style.Default.Background", CELL_IDLE);
            write(this.previouslyHeld + ".Style.Hovered.Background", CELL_IDLE_HOVER);
        }

        if (this.held != null) {
            write(this.held + ".Style.Default.Background", CELL_HELD);
            write(this.held + ".Style.Hovered.Background", CELL_HELD_HOVER);
        }
    }

    /// One drawn cell: the zone its container belongs to and the slot within it.
    public record SlotRef(@Nonnull String zone, int slot) {
    }

    private static float clamp(float ratio) {
        return Math.max(0f, Math.min(1f, ratio));
    }
}
