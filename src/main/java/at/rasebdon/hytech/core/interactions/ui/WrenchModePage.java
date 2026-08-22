package at.rasebdon.hytech.core.interactions.ui;

import at.rasebdon.hytech.core.HytechCoreModule;
import at.rasebdon.hytech.core.components.WrenchModeComponent;
import at.rasebdon.hytech.core.util.HytechUtil;
import au.ellie.hyui.builders.ButtonBuilder;
import au.ellie.hyui.builders.HyUIPage;
import au.ellie.hyui.html.TemplateProcessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/// A menu for picking which resource the wrench configures. Crouch and right-click to open.
///
/// Replaced a crouch-and-scroll gesture, which was undiscoverable and visibly flickery: the only
/// trace of a scroll available to a plugin is the hotbar active slot changing, so the client
/// switched slots optimistically and the server-side restore was always a tick behind.
///
/// The buttons are declared statically in the HTML, one per possible resource, and hidden when
/// unused. Creating them at runtime does not work: HyUI registers element ids at parse time, so
/// runtime-created buttons render but never receive their click -- which is exactly why an earlier
/// version of this page showed the current mode but could not change it.
public final class WrenchModePage {

    private static final String HTML = "Core/WrenchModePage.html";

    /// How many mode buttons the HTML declares. Registering more resource types than this leaves
    /// the extras unreachable from the menu rather than breaking the page.
    private static final int MAX_BUTTONS = 6;

    private WrenchModePage() {
    }

    /// Opens the picker for one player. Does nothing if no resource modules registered.
    public static void open(@Nonnull Store<EntityStore> store,
                            @Nonnull Ref<EntityStore> playerRef,
                            @Nonnull PlayerRef pageTarget) {

        var resources = HytechCoreModule.get().getResourceTypes();
        if (resources.isEmpty()) return;

        var modeType = HytechCoreModule.get().getWrenchModeComponentType();
        var mode = store.getComponent(playerRef, modeType);
        if (mode == null) {
            mode = new WrenchModeComponent();
            store.putComponent(playerRef, modeType, mode);
        }

        var selected = mode.resolve();

        var template = new TemplateProcessor()
                .setVariable("currentMode", selected == null ? "-" : selected.label());

        var page = HytechPage.of(HTML, template);

        int shown = Math.min(resources.size(), MAX_BUTTONS);

        if (resources.size() > MAX_BUTTONS) {
            HytechUtil.sendPlayerMessage(playerRef,
                    "Wrench menu shows the first " + MAX_BUTTONS + " resource types.");
        }

        for (int i = 0; i < MAX_BUTTONS; i++) {
            String id = buttonId(i);

            if (i >= shown) {
                page.editById(id, ButtonBuilder.class, button -> button.withVisible(false));
                continue;
            }

            var resource = resources.get(i);
            boolean active = resource == selected;
            var target = mode;

            page.editById(id, ButtonBuilder.class, button -> button
                    .withVisible(true)
                    .withText(active ? "> " + resource.label() : resource.label()));

            HytechPage.onClick(page, id, (_, ctx) -> {
                target.select(resource.id());

                HytechUtil.sendPlayerMessage(playerRef, "Wrench mode: " + resource.label());
                ctx.getPage().ifPresent(HyUIPage::close);
            });
        }

        page.open(pageTarget, store);
    }

    private static String buttonId(int index) {
        return "wrench-mode-" + index;
    }
}
