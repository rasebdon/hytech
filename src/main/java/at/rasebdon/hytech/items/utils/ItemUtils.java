package at.rasebdon.hytech.items.utils;

import at.rasebdon.hytech.core.util.HytechUtil;
import com.hypixel.hytale.builtin.crafting.state.ProcessingBenchState;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.meta.BlockStateModule;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerBlockState;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerState;
import org.jetbrains.annotations.Nullable;

public class ItemUtils {

    @SuppressWarnings("removal")
    @Nullable
    public static ItemContainerBlockState getLegacyItemContainer(World world, Vector3i pos) {
        if (world == null) {
            return null;
        }

        var blockStateModule = BlockStateModule.get();

        var benchStateType = blockStateModule.getComponentType(ProcessingBenchState.class);
        if (benchStateType != null) {
            var benchState = HytechUtil.getBlockComponent(world, pos, benchStateType);

            if (benchState != null) {
                return benchState;
            }
        }

        var itemContainerStateType = blockStateModule.getComponentType(ItemContainerState.class);
        if (itemContainerStateType != null) {
            return HytechUtil.getBlockComponent(world, pos, itemContainerStateType);
        }

        return null;
    }

}
