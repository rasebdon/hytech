package com.rasebdon.hytech.core.events;

import com.hypixel.hytale.event.IEvent;
import com.rasebdon.hytech.core.components.LogisticContainerComponent;

public abstract class LogisticContainerChangedEvent<TContainer> implements IEvent<Void> {
    public final ChangeType changeType;
    public final LogisticContainerComponent<TContainer> container;

    protected LogisticContainerChangedEvent(ChangeType changeType, LogisticContainerComponent<TContainer> container) {
        this.changeType = changeType;
        this.container = container;
    }

    public boolean isAdded() {
        return this.changeType == ChangeType.ADDED;
    }

    public boolean isRemoved() {
        return this.changeType == ChangeType.REMOVED;
    }

    public LogisticContainerComponent<TContainer> getContainer() {
        return container;
    }

    public enum ChangeType {
        ADDED,
        REMOVED,
        CHANGED,
    }
}