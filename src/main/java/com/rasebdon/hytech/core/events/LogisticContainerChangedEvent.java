package com.rasebdon.hytech.core.events;

import com.rasebdon.hytech.core.components.LogisticContainerComponent;

public abstract class LogisticContainerChangedEvent<TContainer> extends LogisticChangedEvent<LogisticContainerComponent<TContainer>> {
    protected LogisticContainerChangedEvent(LogisticChangeType changeType, LogisticContainerComponent<TContainer> component) {
        super(component, changeType);
    }
}

