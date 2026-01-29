package com.rasebdon.hytech.energy.events;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.core.components.LogisticContainerComponent;
import com.rasebdon.hytech.core.events.LogisticContainerSideConfigChangedEvent;
import com.rasebdon.hytech.energy.IEnergyContainer;

public class EnergyContainerSideConfigChangedEvent extends LogisticContainerSideConfigChangedEvent<IEnergyContainer> {
    public EnergyContainerSideConfigChangedEvent(LogisticContainerComponent<IEnergyContainer> component, Ref<ChunkStore> blockRef, Store<ChunkStore> store) {
        super(component, blockRef, store);
    }
}
