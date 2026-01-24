package com.rasebdon.hytech.core.events;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.IEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public abstract class LogisticChangedEvent<TComponent> implements IEvent<Void> {
    public final Ref<ChunkStore> blockRef;
    public final Store<ChunkStore> store;
    public final LogisticChangeType changeType;
    public final TComponent component;

    protected LogisticChangedEvent(Ref<ChunkStore> blockRef, Store<ChunkStore> store, LogisticChangeType changeType, TComponent component) {
        this.blockRef = blockRef;
        this.store = store;
        this.changeType = changeType;
        this.component = component;
    }

    public boolean isAdded() {
        return this.changeType == LogisticChangeType.ADDED;
    }

    public boolean isRemoved() {
        return this.changeType == LogisticChangeType.REMOVED;
    }

    public TComponent getComponent() {
        return component;
    }

    public boolean isChanged() {
        return this.changeType == LogisticChangeType.CHANGED;
    }
}
