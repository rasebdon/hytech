package com.rasebdon.hytech.core.events;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.IEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.core.components.LogisticContainerComponent;

public abstract class LogisticContainerSideConfigChangedEvent<TContainer> implements IEvent<Void> {
    private final LogisticContainerComponent<TContainer> containerComponent;
    private final Ref<ChunkStore> blockRef;
    private final Store<ChunkStore> store;

    public LogisticContainerSideConfigChangedEvent(LogisticContainerComponent<TContainer> containerComponent,
                                                   Ref<ChunkStore> blockRef,
                                                   Store<ChunkStore> store) {
        this.containerComponent = containerComponent;
        this.blockRef = blockRef;
        this.store = store;
    }

    public LogisticContainerComponent<TContainer> getContainerComponent() {
        return containerComponent;
    }

    public Ref<ChunkStore> getBlockRef() {
        return blockRef;
    }

    public Store<ChunkStore> getStore() {
        return store;
    }
}
