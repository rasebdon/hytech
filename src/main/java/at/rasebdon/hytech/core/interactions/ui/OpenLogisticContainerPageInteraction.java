package at.rasebdon.hytech.core.interactions.ui;

import at.rasebdon.hytech.core.util.LogisticLookup;
import au.ellie.hyui.builders.LabelBuilder;
import au.ellie.hyui.builders.PageBuilder;
import au.ellie.hyui.html.TemplateProcessor;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.universe.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;

import javax.annotation.Nonnull;

/// The default UI for any logistic block that has no bespoke page.
///
/// Tanks, buffers and the creative test blocks all want the same thing: show what is inside, and
/// offer the side configurator. Each component already describes itself through `toString`, so
/// this reuses that rather than reimplementing per-resource formatting -- which also means a new
/// resource type gets a working UI with no UI code at all.
public class OpenLogisticContainerPageInteraction extends OpenPageBlockInteraction {

    private static final String HTML = "Core/LogisticContainerPage.html";
    /// Rows the HTML declares. A block with more containers than this shows the first few.
    private static final int MAX_ROWS = 3;
    private static final String ROW_ID_PREFIX = "container-row-";

    @Nonnull
    public static final BuilderCodec<OpenLogisticContainerPageInteraction> CODEC =
            BuilderCodec.builder(
                            OpenLogisticContainerPageInteraction.class,
                            OpenLogisticContainerPageInteraction::new,
                            OpenPageBlockInteraction.CODEC)
                    .documentation("Opens the generic Hytech container page for the target block.")
                    .build();

    @Override
    @Nullable
    protected PageBuilder getPageBuilder(@NotNull InteractionContext context,
                                         @NotNull World world,
                                         @NotNull Vector3i blockPos) {

        var components = LogisticLookup.allComponentsAt(world, blockPos);
        if (components.isEmpty()) return null;

        var template = new TemplateProcessor()
                .setVariable("blockName", getBlockName(world, blockPos));

        var page = HytechPage.of(HTML, template);

        // Fixed rows, relabelled and hidden as needed. Rows created at runtime render but are
        // never registered for events, and a nested container carrying its own layout-mode
        // disconnects the client outright -- so the page declares its maximum and hides the rest.
        for (int i = 0; i < MAX_ROWS; i++) {
            String id = ROW_ID_PREFIX + i;

            if (i >= components.size()) {
                page.editById(id, LabelBuilder.class, label -> label.withVisible(false));
                continue;
            }

            // Each component already formats itself, so a new resource type gets a working page
            // with no UI code at all.
            String text = components.get(i).toString();

            page.editById(id, LabelBuilder.class,
                    label -> label.withVisible(true).withText(text));
        }

        return page;
    }
}
