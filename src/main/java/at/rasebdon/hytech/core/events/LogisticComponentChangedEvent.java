package at.rasebdon.hytech.core.events;

import at.rasebdon.hytech.core.components.LogisticComponent;

public abstract class LogisticComponentChangedEvent<TContainer> extends LogisticChangedEvent<LogisticComponent<TContainer>> {
    protected LogisticComponentChangedEvent(LogisticChangeType changeType, LogisticComponent<TContainer> component) {
        super(component, changeType);
    }
}

