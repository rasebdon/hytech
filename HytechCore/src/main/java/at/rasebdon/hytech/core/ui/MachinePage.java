package at.rasebdon.hytech.core.ui;

import at.rasebdon.hytech.core.LogisticResourceType;
import at.rasebdon.hytech.core.util.HytechUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.InventoryUtils;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/// The page every Hytech machine, tank and container opens.
///
/// One class rather than one per machine: what differs is only which sections get filled, via a
/// lambda. Side configuration, the player's inventory and two-click item transfer live here once.
public final class MachinePage extends HytechCustomPage {

    private static final String DOCUMENT = "Hytech/MachinePage.ui";

    private static final String ACTION_CONFIGURE = "configure";
    private static final String ACTION_CANCEL = "cancel";
    private static final String ACTION_CLOSE = "close";
    private static final String ACTION_SLOT = "slot:";

    /// Item cells the document declares, by prefix and count. Counted from [MachineView]'s own
    /// constants so bind and render can't drift apart.
    private static final Map<String, Integer> CELL_GROUPS = Map.of(
            "#InSlot", MachineView.SPLIT_CELLS,
            "#OutSlot", MachineView.SPLIT_CELLS,
            "#FlatSlot", MachineView.FLAT_CELLS,
            "#Inv", MachineView.STORAGE_CELLS + MachineView.HOTBAR_CELLS);

    private final World world;
    private final Vector3i blockPos;

    /// Fills the page's sections; called on open and on every refresh, so it must read live state.
    private final BiConsumer<MachinePage, MachineView> content;

    /// The machine's item container, if it has one.
    @Nullable
    private final ItemContainer container;

    private final SideConfigPanel sides;
    private final SlotTransfer transfer = new SlotTransfer();

    /// What each cell stood for last render, so a click resolves without re-deriving the split.
    @Nonnull
    private Map<String, MachineView.SlotRef> cells = Map.of();

    /// The cell painted as held last render, so only the changed cells get rewritten.
    @Nullable
    private String heldCell;

    /// The machine's own ingredient filter, captured each render — the container's own filters
    /// stop insertions into result slots, but nothing else gates the ingredient slots.
    @Nullable
    private Predicate<ItemStack> incompatible;

    /// Which of the machine's slots took ingredients last render.
    @Nonnull
    private Set<Integer> ingredientSlots = Set.of();

    public MachinePage(@Nonnull PlayerRef playerRef,
                       @Nonnull World world,
                       @Nonnull Vector3i blockPos,
                       @Nullable ItemContainer container,
                       @Nonnull BiConsumer<MachinePage, MachineView> content) {
        super(playerRef);

        this.world = world;
        this.blockPos = new Vector3i(blockPos);
        this.container = container;
        this.content = content;
        this.sides = new SideConfigPanel(world, blockPos);
    }

    @Override
    @Nonnull
    protected String document() {
        return DOCUMENT;
    }

    /// The machine's container, for [MachineView#slots].
    @Nullable
    public ItemContainer container() {
        return this.container;
    }

    @Override
    protected String render(@Nonnull UICommandBuilder commands) {
        var view = new MachineView(commands, this.transfer, this.heldCell);

        var resources = LogisticResourceType.presentAt(this.world, this.blockPos);

        view.title(HytechUtil.getBlockDisplayName(this.world, this.blockPos));
        view.configurable(!resources.isEmpty());

        this.content.accept(this, view);

        this.sides.render(view, resources);
        view.inventory(playerSection(InventoryComponent.STORAGE_SECTION_ID),
                playerSection(InventoryComponent.HOTBAR_SECTION_ID));

        view.finish();

        this.cells = view.cells();
        this.heldCell = view.held();
        this.incompatible = view.incompatible();
        this.ingredientSlots = view.ingredientSlots();

        return view.signature();
    }

    @Override
    protected void bind(@Nonnull UIEventBuilder events) {
        // @DecoratedContainer draws the close-button artwork only; behavior is bound here.
        onClick(events, "#CloseButton", ACTION_CLOSE);
        onClick(events, "#ConfigureButton", ACTION_CONFIGURE);
        onClick(events, "#CancelTransferButton", ACTION_CANCEL);

        // Left click moves the whole stack, right click moves one; same action, quantity in the payload.
        for (var group : CELL_GROUPS.entrySet()) {
            for (int cell = 0; cell < group.getValue(); cell++) {
                String selector = group.getKey() + cell;

                onClick(events, selector, ACTION_SLOT + selector);
                onRightClick(events, selector, ACTION_SLOT + "1:" + selector);
            }
        }

        this.sides.bind(events);
    }

    @Override
    protected void onAction(@Nonnull String action,
                            @Nonnull Ref<EntityStore> ref,
                            @Nonnull Store<EntityStore> store) {

        if (action.startsWith(ACTION_SLOT)) {
            clickSlot(action.substring(ACTION_SLOT.length()), ref, store);
            refresh();
            return;
        }

        if (this.sides.onAction(action)) {
            refresh();
            return;
        }

        switch (action) {
            case ACTION_CONFIGURE -> {
                this.sides.toggle();
                refresh();
            }
            case ACTION_CLOSE -> close();
            case ACTION_CANCEL -> {
                this.transfer.clear();
                refresh();
            }
            default -> {
                // Not one of this page's own actions.
            }
        }
    }

    /// Resolves a click through what the cell was last drawn as; a stale click on a
    /// since-changed cell is a no-op rather than a move to the wrong place.
    private void clickSlot(@Nonnull String payload,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull Store<EntityStore> store) {

        int quantity = SlotTransfer.WHOLE_STACK;
        String cell = payload;

        if (payload.startsWith("1:")) {
            quantity = 1;
            cell = payload.substring(2);
        }

        var target = this.cells.get(cell);
        if (target == null) return;

        this.transfer.click(target.zone(), target.slot(), quantity,
                zone -> containerFor(zone, ref, store),
                this::accepts);
    }

    /// Only ingredient slots are gated, by the machine's own predicate; everything else accepts anything.
    private boolean accepts(@Nonnull String zone, int slot, @Nonnull ItemStack stack) {
        if (!SlotTransfer.ZONE_MACHINE.equals(zone)) return true;
        if (!this.ingredientSlots.contains(slot)) return true;

        return this.incompatible == null || !this.incompatible.test(stack);
    }

    /// Which container a zone name stands for, resolved fresh on every click.
    @Nullable
    private ItemContainer containerFor(@Nonnull String zone,
                                       @Nonnull Ref<EntityStore> ref,
                                       @Nonnull Store<EntityStore> store) {
        return switch (zone) {
            case SlotTransfer.ZONE_MACHINE -> this.container;
            case SlotTransfer.ZONE_STORAGE ->
                    InventoryUtils.getSectionById(ref, InventoryComponent.STORAGE_SECTION_ID, store);
            case SlotTransfer.ZONE_HOTBAR ->
                    InventoryUtils.getSectionById(ref, InventoryComponent.HOTBAR_SECTION_ID, store);
            default -> null;
        };
    }

    /// Rendering has no ref/store of its own; null once the player is gone, so the page just
    /// stops drawing their inventory instead of throwing in the refresh loop.
    @Nullable
    private ItemContainer playerSection(int sectionId) {
        var ref = this.playerRef.getReference();
        if (ref == null || !ref.isValid()) return null;

        return InventoryUtils.getSectionById(ref, sectionId, ref.getStore());
    }
}
