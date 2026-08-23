package at.rasebdon.hytech.core.ui;

import at.rasebdon.hytech.core.HytechCoreModule;
import at.rasebdon.hytech.core.components.WrenchModeComponent;
import at.rasebdon.hytech.core.util.HytechUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/// Picks which resource the wrench configures. Crouch and right-click with the wrench to open.
///
/// Replaced a crouch-and-scroll gesture: the only trace of a scroll a plugin can see is the hotbar
/// active slot changing, so the client switched slots optimistically and the server-side restore was
/// always a tick behind, which showed as a flicker. A menu has neither that nor the discoverability
/// problem.
public final class WrenchModePage extends HytechCustomPage {

    private static final String DOCUMENT = "Hytech/WrenchModePage.ui";

    private static final String ACTION_CLOSE = "close";
    private static final String ACTION_MODE = "mode:";

    /// Mode buttons the document declares. More resource types than this leaves the extras
    /// unreachable from the menu rather than breaking the page.
    private static final int MAX_BUTTONS = 6;

    private final Ref<EntityStore> owner;

    public WrenchModePage(@Nonnull PlayerRef playerRef, @Nonnull Ref<EntityStore> owner) {
        super(playerRef);

        this.owner = owner;
    }

    @Override
    @Nonnull
    protected String document() {
        return DOCUMENT;
    }

    @Override
    protected void render(@Nonnull UICommandBuilder commands) {
        var resources = HytechCoreModule.get().getResourceTypes();
        var selected = mode() == null ? null : mode().resolve();

        for (int i = 0; i < MAX_BUTTONS; i++) {
            String selector = "#Mode" + i;

            if (i >= resources.size()) {
                commands.set(selector + ".Visible", false);
                continue;
            }

            var resource = resources.get(i);
            boolean active = resource == selected;

            commands.set(selector + ".Visible", true);
            // The marker is what turns a list of choices into a display of state.
            commands.set(selector + ".Text", active ? "> " + resource.label() : resource.label());
        }
    }

    @Override
    protected void bind(@Nonnull UIEventBuilder events) {
        onClick(events, "#CloseButton", ACTION_CLOSE);

        var resources = HytechCoreModule.get().getResourceTypes();

        for (int i = 0; i < Math.min(resources.size(), MAX_BUTTONS); i++) {
            onClick(events, "#Mode" + i, ACTION_MODE + resources.get(i).id());
        }
    }

    @Override
    protected void onAction(@Nonnull String action,
                            @Nonnull Ref<EntityStore> ref,
                            @Nonnull Store<EntityStore> store) {

        if (action.equals(ACTION_CLOSE)) {
            close();
            return;
        }

        if (!action.startsWith(ACTION_MODE)) return;

        String resourceId = action.substring(ACTION_MODE.length());

        var resource = HytechCoreModule.get().getResourceType(resourceId);
        if (resource == null) return;

        var mode = mode(store, ref);
        mode.select(resourceId);

        HytechUtil.sendPlayerMessage(ref, "Wrench mode: " + resource.label());
        close();
    }

    /// The player's stored mode, created on first use.
    @Nonnull
    private WrenchModeComponent mode(Store<EntityStore> store, Ref<EntityStore> ref) {
        var type = HytechCoreModule.get().getWrenchModeComponentType();

        var existing = store.getComponent(ref, type);
        if (existing != null) return existing;

        var created = new WrenchModeComponent();
        store.putComponent(ref, type, created);

        return created;
    }

    /// The stored mode for rendering, which happens outside an event so it must not create one.
    private WrenchModeComponent mode() {
        if (!this.owner.isValid()) return null;

        return this.owner.getStore()
                .getComponent(this.owner, HytechCoreModule.get().getWrenchModeComponentType());
    }
}
