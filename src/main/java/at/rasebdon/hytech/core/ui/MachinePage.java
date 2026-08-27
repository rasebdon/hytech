package at.rasebdon.hytech.core.ui;

import at.rasebdon.hytech.core.LogisticResourceType;
import at.rasebdon.hytech.core.util.HytechUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
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
/// One class rather than one per machine: what differs between a battery and the burner generator
/// is only which sections they fill, and that is a lambda. Side configuration, the player's own
/// inventory and the two-click item transfer are handled here, so every machine gets all three
/// without asking.
public final class MachinePage extends HytechCustomPage {

    private static final String DOCUMENT = "Hytech/MachinePage.ui";

    private static final String ACTION_CONFIGURE = "configure";
    private static final String ACTION_CONTAINER = "container";
    private static final String ACTION_CANCEL = "cancel";
    private static final String ACTION_CLOSE = "close";
    private static final String ACTION_SLOT = "slot:";

    /// Item cells the document declares, by prefix and count. Bound once on open; which of them
    /// are *visible*, and what each one stands for, is decided on every render.
    private static final Map<String, Integer> CELL_GROUPS = Map.of(
            "#InSlot", 4,
            "#OutSlot", 4,
            "#FlatSlot", 12,
            "#Inv", 45);

    private final World world;
    private final Vector3i blockPos;

    /// Fills the page's sections. Called on open and on every refresh, so it reads live state
    /// rather than a snapshot taken when the page opened.
    private final BiConsumer<MachinePage, MachineView> content;

    /// The machine's item container, if it has one.
    @Nullable
    private final ItemContainer container;

    private final SideConfigPanel sides;
    private final SlotTransfer transfer = new SlotTransfer();

    /// What each cell stood for last render, so a click can be resolved without re-deriving a
    /// machine's ingredient/result split here.
    @Nonnull
    private Map<String, MachineView.SlotRef> cells = Map.of();

    /// The cell painted as held last render, so the highlight moves with two writes rather than
    /// four across every cell on the page.
    @Nullable
    private String heldCell;

    /// The machine's own "this item is no use here" test, captured each render. What keeps
    /// cobblestone out of a crusher: the container's own filters stop insertions into *result*
    /// slots, but nothing else stops a player filling the ingredient slots with something the
    /// machine has no recipe for.
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
        // The X comes from @DecoratedContainer, which supplies the artwork and nothing else --
        // vanilla's own containers bind its behaviour themselves.
        onClick(events, "#CloseButton", ACTION_CLOSE);
        onClick(events, "#ConfigureButton", ACTION_CONFIGURE);
        onClick(events, "#ContainerButton", ACTION_CONTAINER);
        onClick(events, "#CancelTransferButton", ACTION_CANCEL);

        // A left click moves the whole stack, a right click moves one. Both are the same action
        // with a different quantity, so the payload carries the cell and the binding type carries
        // the amount -- one decoded event arrives per page, and this is how it tells them apart.
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
            case ACTION_CONTAINER -> openContainer(ref, store);
            case ACTION_CANCEL -> {
                this.transfer.clear();
                refresh();
            }
            default -> {
            }
        }
    }

    /// One click on an item cell, resolved through what that cell was last drawn as.
    ///
    /// A cell whose meaning changed since the page was drawn simply misses: the map is rebuilt
    /// every render, so a stale click on a slot that no longer exists is a no-op rather than a
    /// move to the wrong place.
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

    /// Whether an item may be placed in a cell.
    ///
    /// Only ingredient slots are gated, and only by the machine's own test -- the same predicate
    /// that already greys the contents summary, so the page never has to know what a crusher or a
    /// burner is. Everything else, the player's own inventory included, takes anything.
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

    /// One of the player's inventory sections, for drawing.
    ///
    /// Rendering has no `ref`/`store` of its own, so it goes through the page's own player
    /// reference. Null once the player is gone, which is exactly when the page should stop drawing
    /// their inventory rather than throwing into the refresh loop.
    @Nullable
    private ItemContainer playerSection(int sectionId) {
        var ref = this.playerRef.getReference();
        if (ref == null || !ref.isValid()) return null;

        return InventoryUtils.getSectionById(ref, sectionId, ref.getStore());
    }

    /// Hands the machine's container to a real inventory window.
    ///
    /// The cells on this page are clickable, which covers most moves, but they can never be
    /// *dragged*: a window switches the client to the container screen and a custom page replaces
    /// that screen rather than layering over it. This is the way out for anyone who would rather
    /// drag, and it costs the page -- the window takes over.
    private void openContainer(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (this.container == null) return;

        var player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        var pageManager = player.getPageManager();
        if (pageManager == null) return;

        pageManager.setPageWithWindows(ref, store, Page.Bench, true,
                new ContainerWindow(this.container));
    }
}
