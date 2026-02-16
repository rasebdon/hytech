package at.rasebdon.hytech.items.events;

import at.rasebdon.hytech.core.components.LogisticContainerComponent;
import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticContainerChangedEvent;
import at.rasebdon.hytech.items.ItemContainer;

public class ItemContainerChangedEvent extends LogisticContainerChangedEvent<ItemContainer> {

    public ItemContainerChangedEvent(LogisticChangeType changeType, LogisticContainerComponent<ItemContainer> component) {
        super(changeType, component);
    }
}
