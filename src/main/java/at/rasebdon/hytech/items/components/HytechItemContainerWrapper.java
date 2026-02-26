package at.rasebdon.hytech.items.components;

import at.rasebdon.hytech.core.components.ContainerHolder;
import at.rasebdon.hytech.items.HytechItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerBlockState;

import javax.annotation.Nonnull;

public record HytechItemContainerWrapper(ItemContainerBlockState blockState) implements
        ContainerHolder<HytechItemContainer>,
        HytechItemContainer {

    public HytechItemContainerWrapper(@Nonnull ItemContainerBlockState blockState) {
        this.blockState = blockState;
    }

    public HytechItemContainer getContainer() {
        return this;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public ItemContainer getItemContainer() {
        return blockState.getItemContainer();
    }
}