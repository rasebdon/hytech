package at.rasebdon.hytech.items.components;

import at.rasebdon.hytech.items.HytechItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerBlockState;

import javax.annotation.Nonnull;

public record HytechItemContainerWrapper(ItemContainerBlockState blockState) implements HytechItemContainer {

    public HytechItemContainerWrapper(@Nonnull ItemContainerBlockState blockState) {
        this.blockState = blockState;
    }

    public HytechItemContainer getContainer() {
        return this;
    }

    @Override
    public ItemContainer getItemContainer() {
        return blockState.getItemContainer();
    }
}