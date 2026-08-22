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

    /// A Group stacks vertically by default; "Vertical" is not a valid HyUI layout mode and
    /// passing it disconnects the client.
    private static final String LAYOUT_STACK = "Top";

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

        var rows = GroupBuilder.group().withId(ROWS_ID).withLayoutMode(LAYOUT_STACK);

        for (int i = 0; i < components.size(); i++) {
            var component = components.get(i);

            // Supplier-bound so the row re-renders on the page's refresh tick and follows the
            // container as it fills, drains or changes resource.
            rows.addChild(LabelBuilder.label()
                    .withId(ROWS_ID + "-" + i)
                    .withText(component.toString()));
        }

        return HytechPage.of(HTML, template).addElement(rows);
    }
}
