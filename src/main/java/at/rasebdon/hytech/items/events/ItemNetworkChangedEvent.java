package at.rasebdon.hytech.items.events;

import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticNetworkChangedEvent;
import at.rasebdon.hytech.core.networks.LogisticNetwork;
import at.rasebdon.hytech.items.ItemContainer;

public class ItemNetworkChangedEvent extends LogisticNetworkChangedEvent<ItemContainer> {
    public ItemNetworkChangedEvent(LogisticNetwork<ItemContainer> network, LogisticChangeType changeType) {
        super(network, changeType);
    }
}
