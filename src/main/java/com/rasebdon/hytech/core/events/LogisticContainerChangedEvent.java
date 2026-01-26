package com.rasebdon.hytech.core.events;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.core.components.LogisticContainerComponent;
import com.rasebdon.hytech.core.components.LogisticPipeComponent;

public abstract class LogisticContainerChangedEvent<TContainer>
        extends LogisticChangedEvent<LogisticContainerComponent<TContainer>> {

    public final Ref<ChunkStore> blockRef;
    public final Store<ChunkStore> store;
    private final boolean isPipe;

    protected LogisticContainerChangedEvent(Ref<ChunkStore> blockRef, Store<ChunkStore> store,
                                            LogisticChangeType changeType, LogisticContainerComponent<TContainer> component) {
        super(component, changeType);
        this.blockRef = blockRef;
        this.store = store;
        isPipe = component instanceof LogisticPipeComponent<TContainer>;
    }

    public boolean isPipe() {
        return this.isPipe;
    }
}

