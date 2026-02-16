package at.rasebdon.hytech.items.networks;

import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.networks.LogisticNetwork;
import at.rasebdon.hytech.items.ItemContainer;

import java.util.Set;

public class ItemNetwork extends LogisticNetwork<ItemContainer> implements ItemContainer {
    protected ItemNetwork(Set<LogisticPipeComponent<ItemContainer>> initialPipes) {
        super(initialPipes);
    }

    @Override
    public void pullFromTargets() {

    }

    @Override
    public void pushToTargets() {

    }

    @Override
    public ItemContainer getContainer() {
        return null;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }
}