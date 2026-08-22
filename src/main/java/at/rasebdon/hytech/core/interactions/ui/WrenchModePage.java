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
/// Crouch and right-click with the wrench to open it.
///
/// This replaced a crouch-and-scroll gesture. Scroll was workable but wrong on two counts: it is
/// undiscoverable, and the only trace of a scroll available to a plugin is the hotbar's active
/// slot changing -- so the client switched slots optimistically and the server-side restore was
/// always a tick behind, which showed as a flicker. A menu has neither problem.
public final class WrenchModePage {

    private static final String HTML = "Core/WrenchModePage.html";
    private static final String LIST_ID = "wrench-mode-list";
    private static final String GENERATED_ID = "wrench-mode-generated";

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

        var list = GroupBuilder.group().withId(GENERATED_ID).withLayoutMode("Top");

        for (var resource : resources) {
            list.addChild(ButtonBuilder.secondaryTextButton()
                    .withId(buttonId(resource.id()))
                    .withText(label(resource.label(), resource == selected)));
        }

        // addElement registers the subtree's ids so the buttons can be wired; inside() then puts
        // it where the HTML says it belongs. Without the first the clicks never bind -- which is
        // why the picker showed the current mode but could not change it.
        page.addElement(list);
        list.inside("#" + LIST_ID);

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
