package at.rasebdon.hytech.core.ui;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

/// What a machine puts on its page, written once per refresh.
///
/// The page document declares every section; a machine fills in the ones it has and this hides the
/// rest, so every block renders through the same document and code.
///
/// The *only* writer on the page: every value goes through [#write] so it lands in the change
/// signature, since an update that changes nothing drops the page's own incoming clicks.
public final class MachineView {

    private static final int DETAIL_ROWS = 6;

    /// Ingredient and result cells in the split view; package-private because [MachinePage] must
    /// bind exactly as many click handlers as this draws.
    static final int SPLIT_CELLS = 4;

    /// Cells in the undivided view, for a fuel slot or an item buffer.
    static final int FLAT_CELLS = 12;

    /// Player inventory cells: thirty-six storage, then nine hotbar.
    static final int STORAGE_CELLS = 36;
    static final int HOTBAR_CELLS = 9;

    /// Pixel metrics, mirroring `Hytech.ui`. The page is sized here rather than by intrinsic
    /// height: a horizontal stack's children stretch on the cross axis instead of sizing to
    /// content, so height has to be computed from what was actually drawn.
    private static final int SLOT_PITCH = 56;
    private static final int PANEL_CHROME = 20;      // @Panel's padding, top and bottom
    private static final int HEADING = 28;           // a panel heading and its gap
    private static final int PRIMARY_BLOCK = 66;     // value, bar, caption
    private static final int SECONDARY_BLOCK = 68;   // heading, bar, caption
    private static final int DETAIL_ROW = 22;
    private static final int PROGRESS_BLOCK = 36;    // bar and caption
    private static final int SECTION_GAP = 14;
    private static final int SLOT_GRID_GAP = 10;
    private static final int INVENTORY_ROWS = 5;
    private static final int INVENTORY_EXTRAS = 37;  // hint, separator and their gaps
    private static final int CONTAINER_CHROME = 72;  // title bar plus #Content padding
    private static final int FOOTER = 44;
    private static final int PANEL_GAP = 10;

    /// Restated from `Hytech.ui`, because writing an anchor replaces the one the markup declared.
    private static final int MAIN_WIDTH = 760;
    private static final int CONTAINER_GAP = 14;

    private static final int FLAT_PER_ROW = 6;

    /// Duplicated from `Hytech.ui`, which paints the same resting-state values on open.
    private static final String CELL_IDLE = "#1b2530";
    private static final String CELL_IDLE_HOVER = "#2a3846";
    private static final String CELL_HELD = "#c9a050";
    private static final String CELL_HELD_HOVER = "#e0bb68";

    private final UICommandBuilder commands;

    /// Everything written this pass, so the page can skip an update that would change nothing.
    private final StringBuilder signature = new StringBuilder();

    /// Which container and slot each drawn cell stands for, so a click can be resolved without
    /// re-deriving a machine's ingredient/result split.
    private final Map<String, SlotRef> cells = new HashMap<>();

    /// Which of the machine's slots take ingredients, so a click-transfer can refuse junk a
    /// container filter wouldn't otherwise catch.
    private final Set<Integer> ingredientSlots = new HashSet<>();

    @Nullable
    private final SlotTransfer transfer;

    /// The cell painted as held last pass, so only the changed cells get repainted.
    @Nullable
    private final String previouslyHeld;

    @Nullable
    private String held;

    /// The machine's own test for an item it cannot use, kept so a transfer into an ingredient
    /// slot can be refused, not just greyed out.
    @Nullable
    private Predicate<ItemStack> incompatible;

    private boolean primaryShown;
    private boolean secondaryShown;
    private boolean slotsShown;
    private boolean splitShown;
    private boolean flatShown;
    private int flatCellsShown;
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

    // -------------------------------------------------------------------------------------------
    // Primitives
    // -------------------------------------------------------------------------------------------

    /// "8.4s", "1m 05s". Seconds under a minute keep a decimal, because a crusher operation is
    /// often shorter than the second a whole number would round it to.
    @Nonnull
    public static String formatSeconds(float seconds) {
        if (seconds < 60f) return String.format("%.1fs", seconds);

        int whole = (int) seconds;

        return String.format("%dm %02ds", whole / 60, whole % 60);
    }

    /// Writes one value and records it in the change signature. Public so the side configurator
    /// writes through this view rather than around the signature.
    public void write(@Nonnull String selector, @Nonnull String value) {
        this.commands.set(selector, value);
        note(selector, value);
    }

    public void write(@Nonnull String selector, float value) {
        this.commands.set(selector, value);
        note(selector, value);
    }

    /// Unused today, and kept deliberately: without it `write(selector, 5)` would bind to the
    /// float overload by widening and quietly send the client a float where an int was meant.
    @SuppressWarnings("unused")
    public void write(@Nonnull String selector, int value) {
        this.commands.set(selector, value);
        note(selector, value);
    }

    public void write(@Nonnull String selector, boolean value) {
        this.commands.set(selector, value);
        note(selector, value);
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

    /// Replaces an element's whole anchor. `.Anchor.Height` is rejected by the client — only
    /// `Anchor` as a whole is settable, and it replaces rather than merges, so every field the
    /// markup declared must be restated. Takes raw fields rather than a built `Anchor` because
    /// `Anchor` has no getters, so it can't be summarised into the signature afterwards.
    private void writeAnchor(@Nonnull String selector, @Nullable Integer width, int height,
                             @Nullable Integer right, @Nullable Integer bottom) {
        var anchor = new Anchor();

        anchor.setHeight(Value.of(height));
        if (width != null) anchor.setWidth(Value.of(width));
        if (right != null) anchor.setRight(Value.of(right));
        if (bottom != null) anchor.setBottom(Value.of(bottom));

        this.commands.setObject(selector + ".Anchor", anchor);
        this.signature.append(selector).append(".Anchor=")
                .append(width).append(',').append(height).append(',')
                .append(right).append(',').append(bottom).append(';');
    }

    /// The machine's headline number: what it holds, and how full. No heading of its own — the
    /// value already says what it is ("12,400 / 50,000 RF").
    public void primary(@Nonnull String value, float ratio, @Nonnull String caption) {
        this.primaryShown = true;

        write("#PrimaryValue.Text", value);
        write("#PrimaryBar.Value", clamp(ratio));
        write("#PrimaryCaption.Text", caption);
    }

    /// One label/value line. Calls beyond what the document declares are ignored rather than thrown.
    public void detail(@Nonnull String label, @Nonnull String value) {
        if (this.detailsUsed >= DETAIL_ROWS) return;

        int row = this.detailsUsed++;

        write("#Detail" + row + "Label.Text", label);
        write("#Detail" + row + "Value.Text", value);
    }

    /// A second bar for a *level*: sunlight, wind exposure. Anything with a duration is
    /// [#progress] instead, which lives beside the slots it is working on.
    public void secondary(@Nonnull String heading, float ratio, @Nonnull String caption) {
        this.secondaryShown = true;

        write("#SecondaryHeading.Text", heading);
        write("#SecondaryBar.Value", clamp(ratio));
        write("#SecondaryCaption.Text", caption);
    }

    // Shared by the write() overloads since commands.set has no common supertype to dispatch on.
    private void note(@Nonnull String selector, @Nonnull Object value) {
        this.signature.append(selector).append('=').append(value).append(';');
    }

    // -------------------------------------------------------------------------------------------
    // Contents
    // -------------------------------------------------------------------------------------------

    /// A machine declaring both an ingredient half and a result half gets the split view; zero for
    /// either means an undivided grid instead (a fuel slot, an item buffer).
    public void slots(@Nonnull String heading,
                      @Nullable ItemContainer container,
                      int inputSlots,
                      int outputSlots,
                      @Nullable Predicate<ItemStack> incompatible) {

        this.incompatible = incompatible;

        if (container == null) return;

        this.slotsShown = true;

        write("#SlotsHeading.Text", heading);

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
            this.flatCellsShown = shown;

            // Undivided: every cell is an ingredient cell.
            drawCells(container, "#FlatSlot", 0, shown, 0, SlotTransfer.ZONE_MACHINE, true);
            hideCells("#FlatSlot", shown, FLAT_CELLS);

            hideCells("#InSlot", 0, SPLIT_CELLS);
            hideCells("#OutSlot", 0, SPLIT_CELLS);
        }
    }

    /// How far through whatever it is doing the block is, and how long is left. One call for
    /// every kind of timed operation so they read identically. `secondsRemaining` <= 0 prints no
    /// countdown (idle or blocked).
    public void progress(float ratio, float secondsRemaining, @Nonnull String status) {
        this.progressShown = true;

        write("#ProgressBar.Value", clamp(ratio));
        write("#ProgressCaption.Text", secondsRemaining > 0f
                ? status + "  -  " + formatSeconds(secondsRemaining) + " left"
                : status);
    }

    /// The player's own inventory along the bottom of the page: storage, then hotbar.
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

    /// Draws `count` cells, `prefix`+`firstCell` onwards, from `firstSlot` of `container`. Values
    /// only — the cells exist in the document already; restructuring the page would drop clicks.
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

                // A label, not `.Quantity`: a page-hosted ItemSlot rejects that selector.
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

    // -------------------------------------------------------------------------------------------
    // Finish
    // -------------------------------------------------------------------------------------------

    /// Hides everything the machine did not fill in, and repaints the held cell. Called after the
    /// machine has had its say.
    void finish() {
        write("#PrimarySection.Visible", this.primaryShown);
        write("#SecondarySection.Visible", this.secondaryShown);
        write("#SplitSlots.Visible", this.splitShown);
        write("#FlatSlots.Visible", this.flatShown);
        write("#ProgressRow.Visible", this.progressShown);
        write("#DetailSection.Visible", this.detailsUsed > 0);

        // The contents column and the inventory stand or fall together — no slots, nothing to move items into.
        write("#ProcessPanel.Visible", this.slotsShown);
        write("#InventorySection.Visible", this.slotsShown && this.inventoryShown);

        for (int row = this.detailsUsed; row < DETAIL_ROWS; row++) {
            write("#Detail" + row + ".Visible", false);
        }

        repaintSelection();
        resize();
    }

    /// Sizes both containers to what was drawn.
    private void resize() {
        int status = HEADING
                + (this.primaryShown ? PRIMARY_BLOCK + SECTION_GAP : 0)
                + (this.secondaryShown ? SECONDARY_BLOCK + SECTION_GAP : 0)
                + this.detailsUsed * DETAIL_ROW;

        int contents = 0;
        if (this.slotsShown) {
            int rows = this.splitShown
                    ? 2
                    : Math.max(1, (this.flatCellsShown + FLAT_PER_ROW - 1) / FLAT_PER_ROW);

            contents = HEADING
                    + rows * SLOT_PITCH + SLOT_GRID_GAP
                    + (this.progressShown ? PROGRESS_BLOCK : 0);
        }

        int columns = Math.max(status, contents) + PANEL_CHROME;

        int inventory = this.slotsShown && this.inventoryShown
                ? INVENTORY_ROWS * SLOT_PITCH + INVENTORY_EXTRAS + PANEL_CHROME
                : 0;

        // #Columns is a horizontal stack, whose own height would otherwise wait on its children's — a circle broken by writing it.
        int container = CONTAINER_CHROME + columns + PANEL_GAP
                + (inventory > 0 ? inventory + PANEL_GAP : 0) + FOOTER;

        writeAnchor("#Columns", null, columns, null, PANEL_GAP);
        writeAnchor("#InventorySection", null, inventory, null, PANEL_GAP);
        writeAnchor("#MainContainer", MAIN_WIDTH, container, CONTAINER_GAP, null);
    }

    /// Repaints only the cell that gained the highlight and the one that lost it, not all fifty-plus.
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

    /// Whether a Configure Sides button makes sense for this block.
    public void configurable(boolean canConfigure) {
        write("#ConfigureButton.Visible", canConfigure);
    }
}
