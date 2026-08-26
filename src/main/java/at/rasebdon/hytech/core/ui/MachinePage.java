package at.rasebdon.hytech.core.ui;

import at.rasebdon.hytech.core.util.HytechUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.BiConsumer;

/// The page every Hytech machine, tank and container opens.
///
/// One class rather than one per machine: what differs between a battery and the burner generator
/// is only which sections they fill, and that is a lambda. The Configure Sides button and the
/// inventory window are handled here, so every machine gets both without asking.
public final class MachinePage extends HytechCustomPage {

    private static final String DOCUMENT = "Hytech/MachinePage.ui";

    private static final String ACTION_CONFIGURE = "configure";
    private static final String ACTION_CONTAINER = "container";
    private static final String ACTION_CLOSE = "close";
    private static final String ACTION_PUSH = "push:";

    /// Auto-push rows the document declares, matching `MachineView`.
    private static final int PUSH_ROWS = 5;

    private final World world;
    private final Vector3i blockPos;

    /// Fills the page's sections. Called on open and on every refresh, so it reads live state
    /// rather than a snapshot taken when the page opened.
    private final BiConsumer<MachinePage, MachineView> content;

    /// The machine's item container, if it has one. Opened as its own window rather than shown
    /// on this page -- see openContainer.
    @Nullable
    private final ItemContainer container;

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
    }

    @Override
    @Nonnull
    protected String document() {
        return DOCUMENT;
    }

    /// The machine's container, for MachineView#container.
    @Nullable
    public ItemContainer container() {
        return this.container;
    }

    @Override
    protected String render(@Nonnull UICommandBuilder commands) {
        var view = new MachineView(commands);

        var resources = SideConfigPage.presentResources(this.world, this.blockPos);

        view.title(HytechUtil.getBlockDisplayName(this.world, this.blockPos));
        view.configurable(!resources.isEmpty());

        this.content.accept(this, view);

        // Written after the machine has had its say, so the toggles always sit in the same place
        // whatever sections the machine filled.
        for (var resource : resources) {
            var block = resource.blockAt(this.world, this.blockPos);
            if (block == null) continue;

            view.autoPush(resource.label(), block.isExtracting());
        }

        view.finish();

        return view.signature();
    }

    @Override
    protected void bind(@Nonnull UIEventBuilder events) {
        onClick(events, "#ConfigureButton", ACTION_CONFIGURE);
        onClick(events, "#ContainerButton", ACTION_CONTAINER);
        onClick(events, "#CloseButton", ACTION_CLOSE);

        // Bound once, for every row the document has: binding happens on open, while which rows
        // are *visible* is decided on every render.
        for (int row = 0; row < PUSH_ROWS; row++) {
            onClick(events, "#Push" + row + "Button", ACTION_PUSH + row);
        }
    }

    @Override
    protected void onAction(@Nonnull String action,
                            @Nonnull Ref<EntityStore> ref,
                            @Nonnull Store<EntityStore> store) {
        if (action.startsWith(ACTION_PUSH)) {
            toggleAutoPush(action.substring(ACTION_PUSH.length()));
            refresh();
            return;
        }

        switch (action) {
            case ACTION_CLOSE -> close();
            case ACTION_CONFIGURE -> openSideConfig(ref, store);
            case ACTION_CONTAINER -> openContainer(ref, store);
            default -> {
            }
        }
    }

    /// Flips one resource's auto-push, addressed by its row on the page.
    ///
    /// The row indexes the resources this block carries, re-read here rather than remembered: a
    /// page can outlive a change to the block it describes, and a stale index would toggle the
    /// wrong container.
    private void toggleAutoPush(String row) {
        int index;
        try {
            index = Integer.parseInt(row);
        } catch (NumberFormatException error) {
            return;
        }

        var resources = SideConfigPage.presentResources(this.world, this.blockPos);
        if (index < 0 || index >= resources.size()) return;

        var block = resources.get(index).blockAt(this.world, this.blockPos);
        if (block == null) return;

        block.setExtracting(!block.isExtracting());
    }

    /// Hands the machine's container to a real inventory window.
    ///
    /// setPageWithWindows with Page.Bench is the documented way to give a player item slots: it
    /// switches the client to the container screen, which is the one carrying their own
    /// inventory, and the engine performs the moves. That screen *replaces* this page, which is
    /// why the two cannot be combined and why this is a button rather than a section.
    private void openContainer(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (this.container == null) return;

        var player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        var pageManager = player.getPageManager();
        if (pageManager == null) return;

        pageManager.setPageWithWindows(ref, store, Page.Bench, true,
                new ContainerWindow(this.container));
    }

    private void openSideConfig(Ref<EntityStore> ref, Store<EntityStore> store) {
        var sideConfig = SideConfigPage.of(this.playerRef, this.world, this.blockPos, this::reopen);
        if (sideConfig == null) return;

        HytechPages.open(store, ref, sideConfig);
    }

    /// Reopens this machine's page, so Back from the side configurator returns here rather than
    /// dropping the player on nothing.
    private void reopen(Store<EntityStore> store, Ref<EntityStore> ref) {
        HytechPages.open(store, ref,
                new MachinePage(this.playerRef, this.world, this.blockPos, this.container, this.content));
    }
}
