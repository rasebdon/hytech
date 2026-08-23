package at.rasebdon.hytech.core.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/// Base for every Hytech machine page, on Hytale's own custom-UI API.
///
/// Replaced HyUI, which registered element ids when its HTML was parsed, so only statically
/// declared elements could receive events. `UIEventBuilder.addEventBinding` binds any selector,
/// so that limit is gone -- and `.ui` gives the game's real design language rather than an
/// approximation of it.
///
/// Subclasses supply a `.ui` document, write their current values into a
/// [UICommandBuilder], and handle named actions. `.ui` is also what gives these pages the game's
/// real look -- `$C.@Panel`, the vanilla button art and the shared colour variables -- which the
/// HTML dialect only ever approximated.
public abstract class HytechCustomPage extends InteractiveCustomUIPage<PageAction> {

    protected HytechCustomPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageAction.CODEC);
    }

    /// Path of the `.ui` document, relative to `Common/UI/Custom/`.
    @Nonnull
    protected abstract String document();

    /// Writes the page's current values. Called on open *and* on every refresh, so it must be
    /// safe to run repeatedly and must not assume anything about previous state.
    protected abstract void render(@Nonnull UICommandBuilder commands);

    /// Binds click handlers. Called once, on open.
    protected void bind(@Nonnull UIEventBuilder events) {
    }

    /// Handles a named action from [#bind].
    protected void onAction(@Nonnull String action,
                            @Nonnull Ref<EntityStore> ref,
                            @Nonnull Store<EntityStore> store) {
    }

    /// Binds a click to a named action.
    ///
    /// The action name travels as a static literal in the event payload, which is how a page with
    /// many buttons tells them apart from a single decoded event. The key must not start with `@`:
    /// that prefix marks a value the client resolves as a selector, not a literal.
    protected static void onClick(@Nonnull UIEventBuilder events,
                                  @Nonnull String selector,
                                  @Nonnull String action) {
        events.addEventBinding(CustomUIEventBindingType.Activating, selector,
                EventData.of("Action", action));
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder commands,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {
        commands.append(document());
        render(commands);
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

    /// Pushes fresh values to an already-open page, without rebuilding it.
    ///
    /// Sending only the value commands means the client keeps its layout and scroll position, so
    /// a once-a-second refresh does not fight the player.
    public void refresh() {
        var commands = new UICommandBuilder();

        render(commands);
        sendUpdate(commands, false);
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        super.onDismiss(ref, store);

        // Stops the refresh loop tracking a page nobody is looking at.
        HytechPages.forget(this);
    }
}
