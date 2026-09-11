package at.rasebdon.hytech.core.util;

import at.rasebdon.hytech.core.HytechCoreModule;
import at.rasebdon.hytech.core.components.LogisticComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/// Finds whichever Hytech logistic components a block carries, without knowing its resource type.
/// A block like the burner generator can carry several (energy output, item fuel), so this gives
/// every caller one consistent answer instead of each resolving it separately.
///
/// Iterates in module registration order (fixed by [at.rasebdon.hytech.core.HytechCorePlugin]), so
/// a multi-container block resolves the same component every time rather than an arbitrary one.
public final class LogisticLookup {

    private LogisticLookup() {
    }

    /// The block component the wrench and overlay act on: the first registered one this block has.
    /// A *choice*, not an answer, on a machine with several — see [#allBlockComponentsAt].
    @Nullable
    public static LogisticComponent<?> blockComponentAt(@Nonnull World world, @Nonnull Vector3i blockPos) {
        for (var blockType : HytechCoreModule.get().getBlockComponents()) {
            var component = HytechUtil.getBlockComponent(world, blockPos, blockType);
            if (component != null) {
                return component;
            }
        }

        return null;
    }

    /// A block component if there is one, otherwise a pipe component.
    @Nullable
    public static LogisticComponent<?> componentAt(@Nonnull World world, @Nonnull Vector3i blockPos) {
        var block = blockComponentAt(world, blockPos);
        if (block != null) return block;

        for (var pipeType : HytechCoreModule.get().getPipeComponents()) {
            var component = HytechUtil.getBlockComponent(world, blockPos, pipeType);
            if (component != null) {
                return component;
            }
        }

        return null;
    }

    /// Every logistic component on the block, blocks then pipes.
    @Nonnull
    public static List<LogisticComponent<?>> allComponentsAt(@Nonnull World world, @Nonnull Vector3i blockPos) {
        var found = new ArrayList<LogisticComponent<?>>(2);

        found.addAll(allBlockComponentsAt(world, blockPos));

        for (var pipeType : HytechCoreModule.get().getPipeComponents()) {
            var component = HytechUtil.getBlockComponent(world, blockPos, pipeType);
            if (component != null) {
                found.add(component);
            }
        }

        return found;
    }

    /// Every *block* component on the block, in registration order.
    @Nonnull
    public static List<LogisticComponent<?>> allBlockComponentsAt(@Nonnull World world, @Nonnull Vector3i blockPos) {
        var found = new ArrayList<LogisticComponent<?>>(2);

        for (var blockType : HytechCoreModule.get().getBlockComponents()) {
            var component = HytechUtil.getBlockComponent(world, blockPos, blockType);
            if (component != null) {
                found.add(component);
            }
        }

        return found;
    }
}
