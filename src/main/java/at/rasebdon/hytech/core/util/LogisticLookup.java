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
///
/// The wrench, the face overlay and the read interaction all need this, and they each used to
/// walk the registries themselves. That was harmless while every block had exactly one
/// container — but the burner generator carries two (`hytech:energy:container` for its output
/// and `hytech:items:container` for its fuel), so "the component on this block" became
/// ambiguous, and two callers resolving it separately could disagree about which one they meant.
///
/// Ordering is the registration order of the modules, which is fixed by
/// [at.rasebdon.hytech.HytechPlugin]. That matters: it used to come out of a `HashSet`, so on a
/// multi-container block the wrench configured an arbitrary container and could pick a different
/// one after a restart.
public final class LogisticLookup {

    private LogisticLookup() {
    }

    /// The block component the wrench and overlay act on: the first registered one this block has.
    ///
    /// On a machine carrying several containers this is a *choice*, not an answer — see
    /// [#allBlockComponentsAt] for the caller that wants all of them. Per-resource side
    /// configuration on a multi-container machine really wants a UI tab rather than a wrench.
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
    ///
    /// Blocks first because a position can only be one or the other, and the block registries are
    /// the smaller walk.
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
    ///
    /// For anything that should describe a block completely rather than pick one aspect of it —
    /// the read interaction reports both the burner's energy and its fuel this way.
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
