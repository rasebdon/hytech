package at.rasebdon.hytech.core.events;

import at.rasebdon.hytech.core.components.LogisticContainerComponent;

public abstract class LogisticContainerChangedEvent<TContainer> extends LogisticChangedEvent<LogisticContainerComponent<TContainer>> {
    protected LogisticContainerChangedEvent(LogisticChangeType changeType, LogisticContainerComponent<TContainer> component) {
        super(component, changeType);
    }
}

