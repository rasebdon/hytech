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

/// Default page for a logistic block with no bespoke UI: tanks, buffers, the test blocks.
public class OpenLogisticContainerPageInteraction extends OpenPageBlockInteraction {

    @Nonnull
    public static final BuilderCodec<OpenLogisticContainerPageInteraction> CODEC =
            BuilderCodec.builder(
                            OpenLogisticContainerPageInteraction.class,
                            OpenLogisticContainerPageInteraction::new,
                            OpenPageBlockInteraction.CODEC)
                    .documentation("Opens the generic Hytech container page for the target block.")
                    .build();

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

            view.detail(resource.label(), summarise(component.getContainer()));
        }

        if (!headlineShown) {
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
                (_, view) -> fill(view, world, blockPos));
    }

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
