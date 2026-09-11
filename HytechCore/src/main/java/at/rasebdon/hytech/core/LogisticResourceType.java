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
public record LogisticResourceType(
        @Nonnull String id,
        @Nonnull String label,
        @Nonnull String accent,
        @Nonnull ComponentType<ChunkStore, ? extends LogisticBlockComponent<?>> blockType,
        @Nonnull ComponentType<ChunkStore, ? extends LogisticPipeComponent<?>> pipeType) {

    /// Neutral slate, so an unstyled resource reads as "unstyled" rather than borrowing meaning.
    public static final String DEFAULT_ACCENT = "#5a6a7a";

    @Nullable
    public LogisticBlockComponent<?> blockAt(@Nonnull World world, @Nonnull Vector3i blockPos) {
        return HytechUtil.getBlockComponent(world, blockPos, this.blockType);
    }

    @Nullable
    public LogisticPipeComponent<?> pipeAt(@Nonnull World world, @Nonnull Vector3i blockPos) {
        return HytechUtil.getBlockComponent(world, blockPos, this.pipeType);
    }

    /// A position is never both.
    @Nullable
    public LogisticComponent<?> componentAt(@Nonnull World world, @Nonnull Vector3i blockPos) {
        LogisticComponent<?> block = blockAt(world, blockPos);

        return block != null ? block : pipeAt(world, blockPos);
    }

    public boolean isPresentAt(@Nonnull World world, @Nonnull Vector3i blockPos) {
        return componentAt(world, blockPos) != null;
    }

    /// Registration order matters: the side configurator, auto-push rows and wrench all index
    /// this list and must agree on which resource is which.
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
