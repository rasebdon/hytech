package at.rasebdon.hytech.core.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/// Base for every Hytech machine page, on Hytale's own custom-UI API.
///
/// Subclasses supply a `.ui` document, write their current values into a
/// [UICommandBuilder], and handle named actions.
public abstract class HytechCustomPage extends InteractiveCustomUIPage<PageAction> {

    @Nullable
    private String lastSignature;

    protected HytechCustomPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageAction.CODEC);
    }

    /// Path of the `.ui` document, relative to `Common/UI/Custom/`.
    @Nonnull
    protected abstract String document();

    /// Called on open and on every refresh; must be safe to run repeatedly. Returns a signature
    /// of everything written, or null to always send — [#refresh] skips the update when it matches.
    @Nullable
    protected abstract String render(@Nonnull UICommandBuilder commands);

    /// Binds click handlers. Called once, on open.
    protected void bind(@Nonnull UIEventBuilder events) {
    }

    /// Handles a named action from [#bind].
    protected void onAction(@Nonnull String action,
                            @Nonnull Ref<EntityStore> ref,
                            @Nonnull Store<EntityStore> store) {
    }

    /// The action name is a static literal (no `@` prefix, which would mark it as a selector).
    protected static void onClick(@Nonnull UIEventBuilder events,
                                  @Nonnull String selector,
                                  @Nonnull String action) {
        // locksInterface = false: a locking binding freezes the client on "Loading..." until
        // acknowledged, and a badly timed refresh could then never unfreeze it.
        events.addEventBinding(CustomUIEventBindingType.Activating, selector,
                EventData.of("Action", action), false);
    }

    protected static void onRightClick(@Nonnull UIEventBuilder events,
                                       @Nonnull String selector,
                                       @Nonnull String action) {
        events.addEventBinding(CustomUIEventBindingType.RightClicking, selector,
                EventData.of("Action", action), false);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder commands,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {
        commands.append(document());
        this.lastSignature = render(commands);
        bind(events);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull PageAction data) {
        var action = data.action();
        if (action == null || action.isBlank()) return;

        onAction(action, ref, store);
    }

    /// Skipped when nothing changed: an update increments the page's outstanding-acknowledgment
    /// count, and the client drops incoming clicks while that count is non-zero.
    public void refresh() {
        var commands = new UICommandBuilder();

        String signature = render(commands);

        if (signature != null && signature.equals(this.lastSignature)) return;

        this.lastSignature = signature;
        sendUpdate(commands, false);
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        super.onDismiss(ref, store);

        // Stops the refresh loop tracking a page nobody is looking at.
        HytechPages.forget(this);
    }
}
