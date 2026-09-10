package at.rasebdon.hytech.items.events;

import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticNetworkChangedEvent;
import at.rasebdon.hytech.core.networks.LogisticNetwork;
import at.rasebdon.hytech.items.HytechItemContainer;

public class ItemNetworkChangedEvent extends LogisticNetworkChangedEvent<HytechItemContainer> {
    public ItemNetworkChangedEvent(LogisticNetwork<HytechItemContainer> network, LogisticChangeType changeType) {
        super(network, changeType);
    }
}
