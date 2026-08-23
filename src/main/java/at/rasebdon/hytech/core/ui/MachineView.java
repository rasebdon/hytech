package at.rasebdon.hytech.core.ui;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/// What a machine puts on its page, written once per refresh.
///
/// The page document declares every section; a machine fills in the ones it has and this hides the
/// rest. So a battery, a fluid tank and the burner generator all render through the same document
/// and the same code, and a new resource type needs neither.
public final class MachineView {

    /// Detail rows the document declares.
    private static final int DETAIL_ROWS = 3;

    private final UICommandBuilder commands;

    private boolean primaryShown;
    private boolean secondaryShown;
    private boolean slotsShown;
    private int detailsUsed;

    MachineView(@Nonnull UICommandBuilder commands) {
        this.commands = commands;
    }

    public void title(@Nonnull String text) {
        this.commands.set("#TitleLabel.Text", text);
    }

    /// The machine's headline number: what it holds, and how full.
    public void primary(@Nonnull String heading, @Nonnull String value, float ratio,
                        @Nonnull String caption) {
        this.primaryShown = true;

        this.commands.set("#PrimaryHeading.Text", heading);
        this.commands.set("#PrimaryValue.Text", value);
        this.commands.set("#PrimaryBar.Value", clamp(ratio));
        this.commands.set("#PrimaryCaption.Text", caption);
    }

    /// A second bar: burn progress, sunlight, altitude. Omit and the section disappears.
    public void secondary(@Nonnull String heading, float ratio, @Nonnull String caption) {
        this.secondaryShown = true;

        this.commands.set("#SecondaryHeading.Text", heading);
        this.commands.set("#SecondaryBar.Value", clamp(ratio));
        this.commands.set("#SecondaryCaption.Text", caption);
    }

    /// Item slots showing a container's contents.
    ///
    /// `incompatible` marks a stack the machine cannot use -- unburnable fuel, say -- so the
    /// player can see why nothing is happening.
    public void slots(@Nonnull String heading, @Nullable ItemContainer container,
                      int inventorySectionId,
                      @Nullable Predicate<ItemStack> incompatible) {
        if (container == null) return;

        this.slotsShown = true;

        this.commands.set("#SlotsHeading.Text", heading);
        this.commands.set("#Slots.Slots", snapshot(container, incompatible));

        // Ties the grid to the container window opened alongside this page. Without it the grid is
        // a picture: the client has no way to know these slots belong to a real container, so a
        // drag has nowhere to land.
        if (inventorySectionId >= 0) {
            this.commands.set("#Slots.InventorySectionId", inventorySectionId);
        }
    }

    /// One label/value line. Extra calls beyond what the document declares are ignored rather than
    /// throwing, since a machine adding a fourth stat should not take its page down.
    public void detail(@Nonnull String label, @Nonnull String value) {
        if (this.detailsUsed >= DETAIL_ROWS) return;

        int row = this.detailsUsed++;

        this.commands.set("#Detail" + row + "Label.Text", label);
        this.commands.set("#Detail" + row + "Value.Text", value);
    }

    /// Whether a Configure Sides button makes sense for this block.
    public void configurable(boolean canConfigure) {
        this.commands.set("#ConfigureButton.Visible", canConfigure);
    }

    /// Hides everything the machine did not fill in. Called after the machine has had its say.
    void finish() {
        this.commands.set("#PrimarySection.Visible", this.primaryShown);
        this.commands.set("#SecondarySection.Visible", this.secondaryShown);
        this.commands.set("#SlotsSection.Visible", this.slotsShown);
        this.commands.set("#DetailSection.Visible", this.detailsUsed > 0);

        for (int row = this.detailsUsed; row < DETAIL_ROWS; row++) {
            this.commands.set("#Detail" + row + ".Visible", false);
        }
    }

    /// The container as grid slots, rebuilt wholesale.
    ///
    /// Not diffed: a container is a handful of slots and an item pipe can change any of them
    /// between refreshes, so tracking which moved would cost more than it saves.
    @Nonnull
    private static List<ItemGridSlot> snapshot(@Nonnull ItemContainer container,
                                               @Nullable Predicate<ItemStack> incompatible) {
        var slots = new ArrayList<ItemGridSlot>(container.getCapacity());

        for (short slot = 0; slot < container.getCapacity(); slot++) {
            var stack = container.getItemStack(slot);

            if (ItemStack.isEmpty(stack)) {
                slots.add(new ItemGridSlot());
                continue;
            }

            var entry = new ItemGridSlot(stack);

            if (incompatible != null && incompatible.test(stack)) {
                entry.setItemIncompatible(true);
            }

            slots.add(entry);
        }

        return slots;
    }

    private static float clamp(float ratio) {
        return Math.max(0f, Math.min(1f, ratio));
    }
}
