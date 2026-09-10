package at.rasebdon.hytech.core;

import at.rasebdon.hytech.core.components.LogisticBlockComponent;
import at.rasebdon.hytech.core.components.LogisticComponent;
import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.util.HytechUtil;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/// One registered resource type, with everything needed to find it on a block.
///
/// Replaces the two parallel sets of component types that `HytechCoreModule` used to keep. The
/// wrench and the side-configuration UI both have to answer "which resource am I configuring",
/// and that question has no answer if energy's block type and energy's pipe type are just two
/// unrelated entries in two unordered sets.
///
/// @param id     the module key, matching the component id -- `energy`, `items`, `fluid`, …
/// @param label  the player-facing name shown by the wrench and the side UI
/// @param accent the resource's colour, as a `#rrggbb` string
public record LogisticResourceType(
        @Nonnull String id,
        @Nonnull String label,
        @Nonnull String accent,
        @Nonnull ComponentType<ChunkStore, ? extends LogisticBlockComponent<?>> blockType,
        @Nonnull ComponentType<ChunkStore, ? extends LogisticPipeComponent<?>> pipeType) {

    /// What a resource that names no colour of its own is drawn in.
    ///
    /// A neutral slate rather than anything suggestive, so an unstyled resource reads as
    /// "unstyled" instead of borrowing another one's meaning.
    public static final String DEFAULT_ACCENT = "#5a6a7a";

    /// This type's block component on `blockPos`, or null if the block has none.
    @Nullable
    public LogisticBlockComponent<?> blockAt(@Nonnull World world, @Nonnull Vector3i blockPos) {
        return HytechUtil.getBlockComponent(world, blockPos, this.blockType);
    }

    /// This type's pipe component on `blockPos`, or null if the block has none.
    @Nullable
    public LogisticPipeComponent<?> pipeAt(@Nonnull World world, @Nonnull Vector3i blockPos) {
        return HytechUtil.getBlockComponent(world, blockPos, this.pipeType);
    }

    /// Whichever of the two this block carries. A position is never both.
    @Nullable
    public LogisticComponent<?> componentAt(@Nonnull World world, @Nonnull Vector3i blockPos) {
        LogisticComponent<?> block = blockAt(world, blockPos);

        return block != null ? block : pipeAt(world, blockPos);
    }

    /// True when the block participates in this resource's network at all.
    public boolean isPresentAt(@Nonnull World world, @Nonnull Vector3i blockPos) {
        return componentAt(world, blockPos) != null;
    }

    /// Every resource this block participates in, in registration order.
    ///
    /// Registration order is load-bearing rather than incidental: the side configurator's tabs, the
    /// auto-push rows and the wrench all index this list, so they agree about which resource is
    /// which only because they all walk it the same way.
    @Nonnull
    public static List<LogisticResourceType> presentAt(@Nonnull World world,
                                                       @Nonnull Vector3i blockPos) {
        return HytechCoreModule.get().getResourceTypes().stream()
                .filter(resource -> resource.isPresentAt(world, blockPos))
                .toList();
    }

    @Override
    @Nonnull
    public String toString() {
        return this.label;
    }
}
