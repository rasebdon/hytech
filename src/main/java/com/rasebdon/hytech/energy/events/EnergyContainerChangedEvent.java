package com.rasebdon.hytech.energy.events;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.core.components.LogisticContainerComponent;
import com.rasebdon.hytech.core.events.LogisticChangeType;
import com.rasebdon.hytech.core.events.LogisticContainerChangedEvent;
import com.rasebdon.hytech.energy.IEnergyContainer;

public class EnergyContainerChangedEvent extends LogisticContainerChangedEvent<IEnergyContainer> {

    public EnergyContainerChangedEvent(Ref<ChunkStore> blockRef, Store<ChunkStore> store,
                                       LogisticChangeType changeType, LogisticContainerComponent<IEnergyContainer> component) {
        super(blockRef, store, changeType, component);
    }
}
