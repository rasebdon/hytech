package at.rasebdon.hytech.items.utils;

import at.rasebdon.hytech.core.util.HytechUtil;
import com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3i;

import javax.annotation.Nullable;

public class ItemUtils {

    /// Returns the native container of a vanilla block, or null if the block does not hold one.
    /// Processing benches keep their slots in separate containers, so they are exposed as one
    /// combined container: pipes insert ingredients and pull finished goods through the same face.
    @Nullable
    public static ItemContainer getLegacyItemContainer(World world, Vector3i pos) {
        if (world == null) {
            return null;
        }

        var containerBlock = HytechUtil.getBlockComponent(world, pos, ItemContainerBlock.getComponentType());
        if (containerBlock != null) {
            return containerBlock.getItemContainer();
        }

        var benchBlock = HytechUtil.getBlockComponent(world, pos, ProcessingBenchBlock.getComponentType());
        if (benchBlock != null) {
            return new CombinedItemContainer(benchBlock.getInputContainer(), benchBlock.getOutputContainer());
        }

        return null;
    }
}
