package at.rasebdon.hytech.items.networks;

import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticNetworkChangedEvent;
import at.rasebdon.hytech.core.networks.LogisticNetwork;
import at.rasebdon.hytech.core.networks.LogisticNetworkSystem;
import at.rasebdon.hytech.items.ItemContainer;
import at.rasebdon.hytech.items.events.ItemNetworkChangedEvent;

import java.util.Set;

public class ItemNetworkSystem extends LogisticNetworkSystem<ItemContainer> {
    @Override
    protected LogisticNetwork<ItemContainer> createNetwork(Set<LogisticPipeComponent<ItemContainer>> pipes) {
        return new ItemNetwork(pipes);
    }

    @Override
    protected LogisticNetworkChangedEvent<ItemContainer> createEvent(LogisticNetwork<ItemContainer> network, LogisticChangeType changeType) {
        return new ItemNetworkChangedEvent(network, changeType);
    }
}
