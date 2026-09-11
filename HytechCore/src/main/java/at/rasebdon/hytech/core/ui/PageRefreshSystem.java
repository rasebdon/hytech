package at.rasebdon.hytech.core.ui;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;

/// Pushes fresh values into every open Hytech page once a second.
public final class PageRefreshSystem extends TickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /// Matches the update rate of the values these pages show; refreshing faster shows nothing new.
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
                // One bad page must not stop the others refreshing.
                LOGGER.atWarning().withCause(error)
                        .log("Failed to refresh %s", page.getClass().getSimpleName());
            }
        }
    }
}
