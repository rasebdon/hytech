package at.rasebdon.hytech.core.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/// Opens Hytech pages and keeps the open ones refreshing.
public final class HytechPages {

    // Weakly held: a page whose player disconnected without a dismiss event would otherwise leak.
    private static final Set<HytechCustomPage> OPEN =
            Collections.newSetFromMap(new WeakHashMap<>());

    private HytechPages() {
    }

    /// Never opens with windows: a window switches the client to the Bench page, a different
    /// screen that can't be layered over a custom page.
    public static boolean open(@Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> playerRef,
                               @Nonnull HytechCustomPage page) {

        var player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) return false;

        player.getPageManager().openCustomPage(playerRef, store, page);

        OPEN.add(page);

        return true;
    }

    @Nonnull
    public static Set<HytechCustomPage> open() {
        return Set.copyOf(OPEN);
    }

    static void forget(@Nonnull HytechCustomPage page) {
        OPEN.remove(page);
    }
}
