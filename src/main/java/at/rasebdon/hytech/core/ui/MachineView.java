package at.rasebdon.hytech.core.ui;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Predicate;

/// What a machine puts on its page, written once per refresh.
///
/// The page document declares every section; a machine fills in the ones it has and this hides the
/// rest. So a battery, a fluid tank and the burner generator all render through the same document
/// and the same code, and a new resource type needs neither.
public final class MachineView {

    /// Detail rows the document declares.
    private static final int DETAIL_ROWS = 3;

    /// Item cells the document declares. A sixteen-slot buffer shows the first few and says how
    /// many more there are, which is what a glance at a page is for.
    private static final int SLOT_CELLS = 6;

    /// Auto-push rows the document declares: one per registered resource type.
    private static final int PUSH_ROWS = 5;

    private final UICommandBuilder commands;

    /// Everything written this pass, so the page can skip an update that would change nothing.
    /// Cheap and exact: the values are the strings and numbers already being sent.
    private final StringBuilder signature = new StringBuilder();

    private boolean primaryShown;
    private boolean secondaryShown;
    private boolean slotsShown;
    private int detailsUsed;
    private int pushRowsUsed;

    MachineView(@Nonnull UICommandBuilder commands) {
        this.commands = commands;
    }

    public void title(@Nonnull String text) {
        set("#TitleLabel.Text", text);
    }

    private void set(@Nonnull String selector, @Nonnull String value) {
        this.commands.set(selector, value);
        this.signature.append(selector).append('=').append(value).append(';');
    }

    private void set(@Nonnull String selector, float value) {
        this.commands.set(selector, value);
        this.signature.append(selector).append('=').append(value).append(';');
    }

    private void set(@Nonnull String selector, boolean value) {
        this.commands.set(selector, value);
        this.signature.append(selector).append('=').append(value).append(';');
    }

    /// A signature of this pass, for change detection.
    @Nonnull
    String signature() {
        return this.signature.toString();
    }

    /// The machine's headline number: what it holds, and how full.
    public void primary(@Nonnull String heading, @Nonnull String value, float ratio,
                        @Nonnull String caption) {
        this.primaryShown = true;

        set("#PrimaryHeading.Text", heading);
        set("#PrimaryValue.Text", value);
        set("#PrimaryBar.Value", clamp(ratio));
        set("#PrimaryCaption.Text", caption);
    }

    /// A second bar: burn progress, sunlight, altitude. Omit and the section disappears.
    public void secondary(@Nonnull String heading, float ratio, @Nonnull String caption) {
        this.secondaryShown = true;

        set("#SecondaryHeading.Text", heading);
        set("#SecondaryBar.Value", clamp(ratio));
        set("#SecondaryCaption.Text", caption);
    }

    /// A contents summary plus the button that opens the real container.
    ///
    /// Deliberately not item slots. Windows are a separate system from custom pages -- a window
    /// switches the client to the Bench page, which is the screen carrying the player's inventory,
    /// and a custom page replaces that screen. So slots embedded here would have nothing to drag
    /// from. `incompatible` still matters: it is what lets the summary say the fuel is unusable.
    public void container(@Nonnull String heading, @Nullable ItemContainer container,
                          @Nullable Predicate<ItemStack> incompatible) {
        if (container == null) return;

        this.slotsShown = true;

        set("#SlotsHeading.Text", heading);
        set("#SlotsSummary.Text", summarise(container, incompatible));

        fillCells(container);
    }

    /// Draws the first [#SLOT_CELLS] occupied slots as item icons with their quantities.
    ///
    /// Values only -- the cells exist in the document and are hidden when unused. Clearing and
    /// re-appending children would restructure the page on every refresh, and a page that keeps
    /// sending structural updates is a page whose button clicks get dropped.
    private void fillCells(@Nonnull ItemContainer container) {
        int cell = 0;

        for (short slot = 0; slot < container.getCapacity() && cell < SLOT_CELLS; slot++) {
            var stack = container.getItemStack(slot);
            if (ItemStack.isEmpty(stack)) continue;

            set("#Slot" + cell + "Cell.Visible", true);
            set("#Slot" + cell + "Icon.ItemId", stack.getItemId());
            // No `.Quantity`: the client rejects it on a page-hosted slot with a
            // "CustomUI Set command error". Only a real window slot counts its own items, which
            // is why vanilla's DroppedItemSlot draws the number in a label of its own -- and why
            // this one does too.
            set("#Slot" + cell + "Count.Text", String.valueOf(stack.getQuantity()));

            cell++;
        }

        set("#SlotsGrid.Visible", cell > 0);

        for (int empty = cell; empty < SLOT_CELLS; empty++) {
            set("#Slot" + empty + "Cell.Visible", false);
        }
    }

    /// "12 charcoal" / "Empty" / "8 items (unusable)".
    @Nonnull
    private static String summarise(@Nonnull ItemContainer container,
                                    @Nullable Predicate<ItemStack> incompatible) {
        int total = 0;
        int unusable = 0;
        int occupied = 0;

        for (short slot = 0; slot < container.getCapacity(); slot++) {
            var stack = container.getItemStack(slot);
            if (ItemStack.isEmpty(stack)) continue;

            occupied++;
            total += stack.getQuantity();

            if (incompatible != null && incompatible.test(stack)) {
                unusable += stack.getQuantity();
            }
        }

        if (total == 0) return "Empty";

        // The cells only show the first few, so a fuller container says how much it is hiding
        // rather than looking like it holds six things.
        String hidden = occupied > SLOT_CELLS ? String.format(" (+%d more)", occupied - SLOT_CELLS) : "";

        // Naming the unusable portion is the point: otherwise a full machine that is not running
        // looks broken rather than mis-loaded.
        if (unusable == total) return total + " items (unusable)" + hidden;
        if (unusable > 0) return String.format("%d items (%d unusable)%s", total, unusable, hidden);

        return total + " items" + hidden;
    }

    /// One label/value line. Extra calls beyond what the document declares are ignored rather than
    /// throwing, since a machine adding a fourth stat should not take its page down.
    public void detail(@Nonnull String label, @Nonnull String value) {
        if (this.detailsUsed >= DETAIL_ROWS) return;

        int row = this.detailsUsed++;

        set("#Detail" + row + "Label.Text", label);
        set("#Detail" + row + "Value.Text", value);
    }

    /// One auto-push row: the resource, and whether the block is currently ejecting it.
    ///
    /// Rows are filled in the order the page offers them, which is the resource registration
    /// order -- the same order the side configurator walks, so the two pages agree about which
    /// resource is which.
    public void autoPush(@Nonnull String resource, boolean pushing) {
        if (this.pushRowsUsed >= PUSH_ROWS) return;

        int row = this.pushRowsUsed++;

        set("#Push" + row + "Button.Text",
                String.format("Push %s: %s", resource, pushing ? "On" : "Off"));
        set("#Push" + row + "Button.Visible", true);
    }

    /// Whether a Configure Sides button makes sense for this block.
    public void configurable(boolean canConfigure) {
        set("#ConfigureButton.Visible", canConfigure);
    }

    /// Hides everything the machine did not fill in. Called after the machine has had its say.
    void finish() {
        set("#PrimarySection.Visible", this.primaryShown);
        set("#SecondarySection.Visible", this.secondaryShown);
        set("#SlotsSection.Visible", this.slotsShown);
        set("#DetailSection.Visible", this.detailsUsed > 0);
        set("#PushSection.Visible", this.pushRowsUsed > 0);

        for (int row = this.pushRowsUsed; row < PUSH_ROWS; row++) {
            set("#Push" + row + "Button.Visible", false);
        }

        for (int row = this.detailsUsed; row < DETAIL_ROWS; row++) {
            set("#Detail" + row + ".Visible", false);
        }
    }

    private static float clamp(float ratio) {
        return Math.max(0f, Math.min(1f, ratio));
    }
}
