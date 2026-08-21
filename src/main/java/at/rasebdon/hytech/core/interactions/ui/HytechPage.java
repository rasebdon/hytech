package at.rasebdon.hytech.core.interactions.ui;

import au.ellie.hyui.builders.HyUIPage;
import au.ellie.hyui.builders.PageBuilder;
import au.ellie.hyui.events.PageRefreshResult;
import au.ellie.hyui.html.TemplateProcessor;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;

import javax.annotation.Nonnull;

/// Shared setup for every Hytech machine page.
///
/// All of them want the same thing: a dismissable page, a once-a-second server-side refresh
/// so supplier-bound template variables re-render, and a working Exit button. That tail was
/// copy-pasted into each interaction; now it is stated once.
public final class HytechPage {

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
        return PageBuilder.detachedPage()
                .loadHtml(htmlPath, template)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .withRefreshRate(REFRESH_MILLIS)
                .onRefresh(_ -> PageRefreshResult.UPDATE)
                .enableRuntimeTemplateUpdates(true)
                .addEventListener(EXIT_BUTTON_ID, CustomUIEventBindingType.Activating,
                        (_, ctx) -> ctx.getPage().ifPresent(HyUIPage::close));
    }
}
