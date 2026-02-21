package at.rasebdon.hytech.items.components;

import at.rasebdon.hytech.items.HytechItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerBlockState;

public class HytechItemContainerWrapper implements HytechItemContainer {
    private final ItemContainerBlockState blockState;

    public HytechItemContainerWrapper(ItemContainerBlockState blockState) {
        this.blockState = blockState;
    }

    @Override
    public ItemContainer getItemContainer() {
        return blockState.getItemContainer();
    }

    public ItemContainerBlockState getBlockState() {
        return blockState;
    }
}
