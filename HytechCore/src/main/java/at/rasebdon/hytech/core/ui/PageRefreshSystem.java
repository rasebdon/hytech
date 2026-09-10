package at.rasebdon.hytech.core.ui;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

/// Pushes fresh values into every open Hytech page once a second.
///
/// HyUI did this internally; on the native API it is ours to run. One system for every page
/// rather than a timer per page, because a page is only ever open for one player and there are
/// rarely more than a handful at a time.
public final class PageRefreshSystem extends TickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /// Matches the systems feeding these pages: charge levels and burn state both update about
    /// once a second, so refreshing faster would show nothing new.
    private static final float REFRESH_INTERVAL_SECONDS = 1f;

    private float sinceRefresh;

    @Override
    public void tick(float dt, int systemIndex, @NonNull Store<EntityStore> store) {
        if (this.sinceRefresh < REFRESH_INTERVAL_SECONDS) {
            this.sinceRefresh += dt;
            return;
        }

        this.sinceRefresh = 0f;

        for (var page : HytechPages.open()) {
            try {
                page.refresh();
            } catch (RuntimeException error) {
                // One bad page must not stop the others refreshing, and a throw here would
                // otherwise surface as a dead tick rather than as this line.
                LOGGER.atWarning().withCause(error)
                        .log("Failed to refresh %s", page.getClass().getSimpleName());
            }
        }
    }
}
