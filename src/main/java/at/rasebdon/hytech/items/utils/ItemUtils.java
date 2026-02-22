package at.rasebdon.hytech.items.utils;

import at.rasebdon.hytech.core.util.HytechUtil;
import com.hypixel.hytale.builtin.crafting.state.ProcessingBenchState;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerBlockState;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerState;

import javax.annotation.Nullable;

public class ItemUtils {

    @Nullable
    public static ItemContainerBlockState getLegacyItemContainer(World world, Vector3i pos) {
        if (world == null) {
            return null;
        }

        var benchState = HytechUtil.getBlockState(world, pos, ProcessingBenchState.class);
        if (benchState != null) {
            return benchState;
        }

        return HytechUtil.getBlockState(world, pos, ItemContainerState.class);
    }
}
