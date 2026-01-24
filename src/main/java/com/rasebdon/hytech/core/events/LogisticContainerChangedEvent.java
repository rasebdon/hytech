package com.rasebdon.hytech.core.events;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.core.components.ILogisticContainer;
import com.rasebdon.hytech.core.components.LogisticContainerComponent;

public abstract class LogisticContainerChangedEvent<TContainer extends ILogisticContainer>
        extends LogisticChangedEvent<LogisticContainerComponent<TContainer>> {

    protected LogisticContainerChangedEvent(Ref<ChunkStore> blockRef, Store<ChunkStore> store,
                                            LogisticChangeType changeType, LogisticContainerComponent<TContainer> component) {
        super(blockRef, store, changeType, component);
    }
}
