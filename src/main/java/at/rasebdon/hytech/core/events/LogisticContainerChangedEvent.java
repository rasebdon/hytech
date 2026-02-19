package at.rasebdon.hytech.core.events;

import at.rasebdon.hytech.core.components.LogisticComponent;

public abstract class LogisticContainerChangedEvent<TContainer> extends LogisticChangedEvent<LogisticComponent<TContainer>> {
    protected LogisticContainerChangedEvent(LogisticChangeType changeType, LogisticComponent<TContainer> component) {
        super(component, changeType);
    }
}

