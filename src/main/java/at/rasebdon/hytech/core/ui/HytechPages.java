package at.rasebdon.hytech.core.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/// Opens Hytech pages and keeps the open ones refreshing.
public final class HytechPages {

    /// Pages currently open, so [PageRefreshSystem] can push new values into them.
    ///
    /// Weakly held: a page whose player disconnected without a dismiss event would otherwise be
    /// kept alive here forever, along with everything it closes over.
    private static final Set<HytechCustomPage> OPEN =
            Collections.newSetFromMap(new WeakHashMap<>());

    private HytechPages() {
    }

    /// Opens a page for a player, with an inventory window when the page has a container.
    ///
    /// This is the whole reason for leaving HyUI: a page that shows item slots needs the player's
    /// own inventory on screen to drag from, and that only happens when the page is opened
    /// together with a window. `openCustomPageWithWindows` sends the same `OpenWindow` packet a
    /// chest does, alongside our own layout.
    public static boolean open(@Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> playerRef,
                               @Nonnull HytechCustomPage page) {

        var player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) return false;

        var pageManager = player.getPageManager();
        if (pageManager == null) return false;

        var window = page.inventoryWindow();

        if (window == null) {
            pageManager.openCustomPage(playerRef, store, page);
        } else if (!pageManager.openCustomPageWithWindows(playerRef, store, page, window)) {
            // Windows are opened before the page is built, so a failure here means no page either.
            return false;
        }

        OPEN.add(page);

        return true;
    }

    /// The player component's `PlayerRef`, which a page needs for its constructor.
    @Nonnull
    public static PlayerRef playerRefOf(@Nonnull Store<EntityStore> store,
                                        @Nonnull Ref<EntityStore> playerRef) {
        var component = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (component == null) {
            throw new IllegalStateException("Entity is not a player");
        }

        return component;
    }

    /// Every open page, for the refresh loop.
    @Nonnull
    public static Set<HytechCustomPage> open() {
        return Set.copyOf(OPEN);
    }

    static void forget(@Nonnull HytechCustomPage page) {
        OPEN.remove(page);
    }
}
