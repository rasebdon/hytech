package at.rasebdon.hytech.items.events;

import at.rasebdon.hytech.core.components.LogisticComponent;
import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticComponentChangedEvent;
import at.rasebdon.hytech.items.HytechItemContainer;

public class ItemContainerChangedEvent extends LogisticComponentChangedEvent<HytechItemContainer> {

    public ItemContainerChangedEvent(LogisticChangeType changeType, LogisticComponent<HytechItemContainer> component) {
        super(changeType, component);
    }
}
