package at.rasebdon.hytech.core.events;

import com.hypixel.hytale.event.IEvent;

public abstract class LogisticChangedEvent<TComponent> implements IEvent<Void> {
    private final TComponent component;
    private final LogisticChangeType changeType;

    protected LogisticChangedEvent(TComponent component, LogisticChangeType changeType) {
        this.component = component;
        this.changeType = changeType;
    }

    public boolean isAdded() {
        return this.changeType == LogisticChangeType.ADDED;
    }

    public boolean isRemoved() {
        return this.changeType == LogisticChangeType.REMOVED;
    }

    public boolean isChanged() {
        return this.changeType == LogisticChangeType.CHANGED;
    }

    public TComponent getComponent() {
        return component;
    }
}