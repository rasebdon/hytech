package at.rasebdon.hytech.core.interactions.ui;

import au.ellie.hyui.builders.HyUIPage;
import au.ellie.hyui.builders.PageBuilder;
import au.ellie.hyui.events.PageRefreshResult;
import au.ellie.hyui.events.UIContext;
import au.ellie.hyui.html.TemplateProcessor;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;

import javax.annotation.Nonnull;
import java.util.function.BiConsumer;

/// Shared setup for every Hytech machine page.
///
/// All of them want the same thing: a dismissable page, a once-a-second server-side refresh
/// so supplier-bound template variables re-render, and a working Exit button. That tail was
/// copy-pasted into each interaction; now it is stated once.
public final class HytechPage {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /// Matches the cadence of the systems feeding these pages -- charge levels and burn state
    /// both update about once a second, so a faster refresh would show nothing new.
    private static final long REFRESH_MILLIS = 1000L;

    /// Every page's close button uses this id, so the exit wiring can be shared.
    public static final String EXIT_BUTTON_ID = "exit-button";

    private HytechPage() {
    }

    /// A page loaded from `htmlPath` with refresh and exit already wired.
    ///
    /// Callers add their own elements and listeners on top; `PageBuilder` is a fluent builder,
    /// so the returned instance is still open to further configuration.
    @Nonnull
    public static PageBuilder of(@Nonnull String htmlPath, @Nonnull TemplateProcessor template) {
        var page = PageBuilder.detachedPage()
                .loadHtml(htmlPath, template)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .withRefreshRate(REFRESH_MILLIS)
                .onRefresh(_ -> PageRefreshResult.UPDATE)
                .enableRuntimeTemplateUpdates(true);

        // Only if the page actually declares one. addEventListener throws on an unknown id, so
        // wiring this unconditionally made every page without an Exit button fail to open -- as
        // the side configurator did, which has Back instead.
        if (page.getElementRegistry().containsKey(EXIT_BUTTON_ID)) {
            page.addEventListener(EXIT_BUTTON_ID, CustomUIEventBindingType.Activating,
                    (_, ctx) -> ctx.getPage().ifPresent(HyUIPage::close));
        }

        return page;
    }

    /// Wires a click handler, but only if the page actually has that element.
    ///
    /// `addEventListener` throws on an unknown id, and a throw here happens while *opening* the
    /// page -- so one stale id takes the whole UI down rather than degrading. Elements must
    /// already be attached when this is called: builders added with `addElement` only enter the
    /// registry at that point, not when they are constructed.
    public static void onClick(@Nonnull PageBuilder page, @Nonnull String elementId,
                               @Nonnull BiConsumer<Object, UIContext> handler) {
        if (!page.getElementRegistry().containsKey(elementId)) {
            LOGGER.atWarning().log("No UI element '%s' to wire; skipping listener", elementId);
            return;
        }

        page.addEventListener(elementId, CustomUIEventBindingType.Activating, handler);
    }
}
