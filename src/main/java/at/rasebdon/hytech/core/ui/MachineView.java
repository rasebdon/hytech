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
    ///
    /// The four cell counts are package-private because [MachinePage] binds a click handler to
    /// every declared cell and must bind exactly as many as this draws.
    static final int SPLIT_CELLS = 4;

    /// Cells in the undivided view, for a fuel slot or an item buffer.
    static final int FLAT_CELLS = 12;

    /// Player inventory cells: thirty-six storage, then nine hotbar.
    static final int STORAGE_CELLS = 36;
    static final int HOTBAR_CELLS = 9;

    /// Pixel metrics, mirroring `Hytech.ui`.
    ///
    /// The page is sized by the server rather than by intrinsic height: the two containers sit in a
    /// horizontal stack, and a child of a horizontal stack stretches to its parent on the cross
    /// axis, so "as tall as its content" is not something the layout will work out on its own.
    /// Adding up what was actually drawn is what makes a solar panel's page short and a smelter's
    /// tall, instead of every block paying for the worst case.
    ///
    /// Kept together and named so the arithmetic below reads as a layout rather than as constants.
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

    /// Cells per row in the undivided grid, at the width the contents panel gets.
    private static final int FLAT_PER_ROW = 6;

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

    /// Writes one value and records it in the change signature.
    ///
    /// Public because the side configurator writes through this view rather than touching the
    /// command builder: a value written around the signature is a value that can go stale on the
    /// page without the refresh ever noticing.
    public void write(@Nonnull String selector, @Nonnull String value) {
        this.commands.set(selector, value);
        note(selector, value);
    }

    public void write(@Nonnull String selector, float value) {
        this.commands.set(selector, value);
        note(selector, value);
    }

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

    /// Replaces an element's whole anchor.
    ///
    /// `.Anchor.Height` looks like it should work -- style objects nest that way, and
    /// `#Button.Style.Default.Background` is exactly how the faces are recoloured -- but the client
    /// rejects it with "selector doesn't match a markup property". Only `Anchor` as a whole is
    /// settable, which is what vanilla's own MemoriesPage does. It *replaces* rather than merges,
    /// so every field the markup declared has to be restated, not just the one being changed.
    ///
    /// Takes the fields rather than a built `Anchor` because `Anchor` exposes setters and no
    /// getters, so a built one cannot be summarised for the change signature afterwards -- and a
    /// signature entry that came out as an identity hash would differ on every pass and defeat the
    /// skip that keeps the page's own clicks alive.
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

    /// The machine's headline number: what it holds, and how full.
    ///
    /// No heading of its own -- the panel is called Status and the value says what it is ("12,400 /
    /// 50,000 RF"), so a third line reading "Energy" was telling the player something they had just
    /// read.
    public void primary(@Nonnull String value, float ratio, @Nonnull String caption) {
        this.primaryShown = true;

        write("#PrimaryValue.Text", value);
        write("#PrimaryBar.Value", clamp(ratio));
        write("#PrimaryCaption.Text", caption);
    }

    /// One label/value line. Extra calls beyond what the document declares are ignored rather than
    /// throwing, since a machine adding a seventh stat should not take its page down.
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

    /// Records a written value in the change signature. `commands.set` is overloaded per type
    /// and has no common supertype to dispatch on, so the overloads above split on the `set`
    /// call and share this.
    private void note(@Nonnull String selector, @Nonnull Object value) {
        this.signature.append(selector).append('=').append(value).append(';');
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

            // Undivided: every slot takes what goes in, so every cell is an ingredient cell.
            // That is what makes the burner refuse a non-fuel through the same path.
            drawCells(container, "#FlatSlot", 0, shown, 0, SlotTransfer.ZONE_MACHINE, true);
            hideCells("#FlatSlot", shown, FLAT_CELLS);

            hideCells("#InSlot", 0, SPLIT_CELLS);
            hideCells("#OutSlot", 0, SPLIT_CELLS);
        }
    }

    /// How far through whatever it is doing the block is, and how long is left.
    ///
    /// One call for every kind of timed operation the mod has -- a recipe in a crusher, a lump of
    /// charcoal in a burner -- so they are worded and drawn identically and the reading transfers
    /// between them. Drawn under the slots rather than in the status column: an operation in flight
    /// belongs next to the things it is consuming.
    ///
    /// `secondsRemaining` of zero or less prints no countdown, which is the honest rendering of a
    /// machine that is idle or blocked.
    public void progress(float ratio, float secondsRemaining, @Nonnull String status) {
        this.progressShown = true;

        write("#ProgressBar.Value", clamp(ratio));
        write("#ProgressCaption.Text", secondsRemaining > 0f
                ? status + "  -  " + formatSeconds(secondsRemaining) + " left"
                : status);
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

        // The contents column and the inventory stand or fall together. A battery has nothing you
        // could move an item into, so showing the player's inventory beneath it would be offering
        // a control that cannot do anything -- and the remaining columns flex to take the space.
        write("#ProcessPanel.Visible", this.slotsShown);
        write("#InventorySection.Visible", this.slotsShown && this.inventoryShown);

        for (int row = this.detailsUsed; row < DETAIL_ROWS; row++) {
            write("#Detail" + row + ".Visible", false);
        }

        repaintSelection();
        resize();
    }

    /// Sizes both containers to what was drawn.
    ///
    /// Writing an anchor *replaces* the one the markup declared, so each call restates every field
    /// that element was given -- the width and gaps included, not just the height being changed.
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

        // Every box whose height would otherwise have to be inferred gets told. `#Columns` is a
        // horizontal stack, and a horizontal stack's own height is exactly the quantity its
        // children are waiting on to know theirs -- so left alone the whole row can resolve to
        // nothing. Writing it breaks the circle.
        int container = CONTAINER_CHROME + columns + PANEL_GAP
                + (inventory > 0 ? inventory + PANEL_GAP : 0) + FOOTER;

        // Every field the markup gives these three is restated here, because setting an anchor
        // replaces it rather than merging into it.
        writeAnchor("#Columns", null, columns, null, PANEL_GAP);
        writeAnchor("#InventorySection", null, inventory, null, PANEL_GAP);
        writeAnchor("#MainContainer", MAIN_WIDTH, container, CONTAINER_GAP, null);
    }

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

    /// Whether a Configure Sides button makes sense for this block.
    public void configurable(boolean canConfigure) {
        write("#ConfigureButton.Visible", canConfigure);
    }
}
