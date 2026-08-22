package at.rasebdon.hytech.core.interactions.ui;

import at.rasebdon.hytech.core.util.LogisticLookup;
import au.ellie.hyui.builders.GroupBuilder;
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
    private static final String ROWS_ID = "container-rows";

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

        // Into the container the HTML declares; addElement leaves the element outside the layout
        // tree and it never appears.
        var rows = page.getById(ROWS_ID, GroupBuilder.class);
        if (rows.isEmpty()) return page;

        for (int i = 0; i < components.size(); i++) {
            rows.get().addChild(LabelBuilder.label()
                    .withId(ROWS_ID + "-" + i)
                    .withText(components.get(i).toString()));
        }

        return page;
    }
}
