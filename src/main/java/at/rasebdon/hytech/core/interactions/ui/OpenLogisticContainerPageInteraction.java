package at.rasebdon.hytech.core.interactions.ui;

import at.rasebdon.hytech.core.LogisticResourceType;
import at.rasebdon.hytech.core.containers.ScalarContainer;
import at.rasebdon.hytech.core.containers.TypedScalarContainer;
import at.rasebdon.hytech.core.ui.HytechCustomPage;
import at.rasebdon.hytech.core.ui.MachinePage;
import at.rasebdon.hytech.core.ui.MachineView;
import at.rasebdon.hytech.core.util.LogisticLookup;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;

import javax.annotation.Nonnull;

/// The default page for a logistic block with no bespoke UI: tanks, buffers, the test blocks.
///
/// Reports the block's first scalar container as the headline and lists the rest as detail rows, so
/// a new resource type gets a working page with no UI code at all.
public class OpenLogisticContainerPageInteraction extends OpenPageBlockInteraction {

    @Nonnull
    public static final BuilderCodec<OpenLogisticContainerPageInteraction> CODEC =
            BuilderCodec.builder(
                            OpenLogisticContainerPageInteraction.class,
                            OpenLogisticContainerPageInteraction::new,
                            OpenPageBlockInteraction.CODEC)
                    .documentation("Opens the generic Hytech container page for the target block.")
                    .build();

    /// Reads live state on every refresh rather than closing over a snapshot, so a tank being
    /// filled by a pipe updates while the page is open.
    private static void fill(MachineView view, World world, Vector3i blockPos) {
        var resources = LogisticResourceType.presentAt(world, blockPos);

        boolean headlineShown = false;

        for (var resource : resources) {
            var component = resource.blockAt(world, blockPos);
            if (component == null) continue;

            var container = component.getContainer();

            if (!headlineShown && container instanceof ScalarContainer scalar) {
                headlineShown = true;

                view.primary(describe(scalar), scalar.getFillRatio(),
                        resource.label() + "  -  " + percent(scalar.getFillRatio()) + "% full");
                continue;
            }

            // Anything past the headline, and anything not scalar (items), becomes a detail line.
            // Each component already formats itself, which is why this needs no per-type code.
            view.detail(resource.label(), summarise(component.getContainer()));
        }

        if (!headlineShown) {
            // A block with only slot-based containers still deserves a headline.
            var first = LogisticLookup.allBlockComponentsAt(world, blockPos).stream().findFirst();
            first.ifPresent(component -> view.primary(
                    summarise(component.getContainer()), 0f, "Contents"));
        }
    }

    @Override
    @Nullable
    protected HytechCustomPage createPage(@NotNull World world,
                                          @NotNull Vector3i blockPos,
                                          @NotNull PlayerRef playerRef) {

        if (LogisticResourceType.presentAt(world, blockPos).isEmpty()) return null;

        return new MachinePage(playerRef, world, blockPos, null,
                (page, view) -> fill(view, world, blockPos));
    }

    /// "1,200 / 8,000  Water" -- amount, capacity and, for a typed tank, what it holds.
    private static String describe(ScalarContainer scalar) {
        String amounts = String.format("%,d / %,d", scalar.getAmount(), scalar.getTotalCapacity());

        if (scalar instanceof TypedScalarContainer<?> typed && typed.getResourceType() != null) {
            return amounts + "  " + typed.getResourceType();
        }

        return amounts;
    }

    private static String summarise(@Nullable Object container) {
        if (container instanceof ScalarContainer scalar) return describe(scalar);
        if (container == null) return "-";

        return container.toString();
    }
}
