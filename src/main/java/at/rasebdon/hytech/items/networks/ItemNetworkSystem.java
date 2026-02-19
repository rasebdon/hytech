package at.rasebdon.hytech.items.networks;

import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticNetworkChangedEvent;
import at.rasebdon.hytech.core.networks.LogisticNetwork;
import at.rasebdon.hytech.core.networks.LogisticNetworkSystem;
import at.rasebdon.hytech.items.HytechItemContainer;
import at.rasebdon.hytech.items.events.ItemNetworkChangedEvent;

import java.util.Set;

public class ItemNetworkSystem extends LogisticNetworkSystem<HytechItemContainer> {
    @Override
    protected LogisticNetwork<HytechItemContainer> createNetwork(Set<LogisticPipeComponent<HytechItemContainer>> pipes) {
        return new ItemNetwork(pipes);
    }

    @Override
    protected LogisticNetworkChangedEvent<HytechItemContainer> createEvent(LogisticNetwork<HytechItemContainer> network, LogisticChangeType changeType) {
        return new ItemNetworkChangedEvent(network, changeType);
    }
}
