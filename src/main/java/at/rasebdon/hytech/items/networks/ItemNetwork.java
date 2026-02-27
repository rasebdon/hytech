package at.rasebdon.hytech.items.networks;

import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.networks.LogisticNetwork;
import at.rasebdon.hytech.items.HytechItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import java.util.Set;

public class ItemNetwork extends LogisticNetwork<HytechItemContainer> implements HytechItemContainer {
    protected ItemNetwork(Set<LogisticPipeComponent<HytechItemContainer>> initialPipes) {
        super(initialPipes);
    }

    @Override
    public HytechItemContainer getContainer() {
        return null;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public ItemContainer getItemContainer() {
        return null;
    }
}