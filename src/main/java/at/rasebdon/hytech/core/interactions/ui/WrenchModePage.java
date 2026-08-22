package at.rasebdon.hytech.core.interactions.ui;

import at.rasebdon.hytech.core.HytechCoreModule;
import at.rasebdon.hytech.core.components.WrenchModeComponent;
import at.rasebdon.hytech.core.util.HytechUtil;
import au.ellie.hyui.builders.ButtonBuilder;
import au.ellie.hyui.builders.GroupBuilder;
import au.ellie.hyui.builders.HyUIPage;
import au.ellie.hyui.builders.PageBuilder;
import au.ellie.hyui.html.TemplateProcessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/// A menu for picking which resource the wrench configures.
///
/// Crouch-and-scroll works, but it is a hidden gesture and the client switches hotbar slots
/// optimistically, so restoring the slot server-side reads as a flicker. This is the
/// discoverable version: crouch and right-click with the wrench and pick from a list. Scroll
/// stays as the shortcut once you know the modes.
public final class WrenchModePage {

    private static final String HTML = "Core/WrenchModePage.html";
    private static final String LIST_ID = "wrench-mode-list";

    private WrenchModePage() {
    }

    /// Opens the picker for one player. Does nothing if no resource modules registered.
    public static void open(@Nonnull Store<EntityStore> store,
                            @Nonnull Ref<EntityStore> playerRef,
                            @Nonnull com.hypixel.hytale.server.core.universe.PlayerRef pageTarget) {

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

        var list = page.getById(LIST_ID, GroupBuilder.class);
        if (list.isEmpty()) return;

        // Attach every button first; they only enter the element registry then, and
        // addEventListener throws on an id it cannot find.
        for (var resource : resources) {
            list.get().addChild(ButtonBuilder.secondaryTextButton()
                    .withId(buttonId(resource.id()))
                    .withText(label(resource.label(), resource == selected)));
        }

        var finalMode = mode;

        for (var resource : resources) {
            HytechPage.onClick(page, buttonId(resource.id()), (_, ctx) -> {
                finalMode.select(resource.id());

                HytechUtil.sendPlayerMessage(playerRef, "Wrench mode: " + resource.label());
                ctx.getPage().ifPresent(HyUIPage::close);
            });
        }

        page.open(pageTarget, store);
    }

    private static String buttonId(String resourceId) {
        return "wrench-mode-" + resourceId;
    }

    /// Marks the active entry, so the list shows state rather than only offering choices.
    private static String label(@Nullable String resourceLabel, boolean active) {
        return active ? "> " + resourceLabel : resourceLabel;
    }
}
