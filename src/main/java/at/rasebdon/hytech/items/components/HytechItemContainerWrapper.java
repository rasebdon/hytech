package at.rasebdon.hytech.items.components;

import at.rasebdon.hytech.core.components.ContainerHolder;
import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.items.HytechItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import javax.annotation.Nonnull;

/// Adapts a vanilla block's native container (chest, processing bench, ...) to the logistic
/// container interface, so it can take part in item networks like a Hytech block does.
public class HytechItemContainerWrapper
        extends ContainerHolder<HytechItemContainer>
        implements HytechItemContainer {

    private final ItemContainer itemContainer;

    public HytechItemContainerWrapper(@Nonnull ItemContainer itemContainer) {
        super();
        this.itemContainer = itemContainer;
    }

    public HytechItemContainer getContainer() {
        return this;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    /// Attaching or detaching a vanilla container changes what the adjacent pipes can
    /// reach, so their networks have to re-derive their pull/push targets.
    @Override
    public void reload() {
        for (var neighbor : getNeighbors()) {
            if (neighbor.getHolder() instanceof LogisticPipeComponent<HytechItemContainer> pipe) {
                var network = pipe.getNetwork();
                if (network != null) {
                    network.rebuildTargets();
                }
            }
        }
    }

    @Override
    public ItemContainer getItemContainer() {
        return this.itemContainer;
    }

    /// Vanilla containers are not throttled by Hytech, so they never limit a transfer.
    @Override
    public long getTransferSpeed() {
        return Long.MAX_VALUE;
    }
}
