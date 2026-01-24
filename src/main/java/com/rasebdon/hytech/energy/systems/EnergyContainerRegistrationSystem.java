package com.rasebdon.hytech.energy.systems;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.core.components.LogisticContainerComponent;
import com.rasebdon.hytech.core.events.LogisticChangeType;
import com.rasebdon.hytech.core.events.LogisticContainerChangedEvent;
import com.rasebdon.hytech.core.systems.LogisticContainerRegistrationSystem;
import com.rasebdon.hytech.energy.IEnergyContainer;
import com.rasebdon.hytech.energy.events.EnergyContainerChangedEvent;

public class EnergyContainerRegistrationSystem extends LogisticContainerRegistrationSystem<IEnergyContainer> {

    public EnergyContainerRegistrationSystem(
            ComponentType<ChunkStore, ? extends LogisticContainerComponent<IEnergyContainer>> componentType,
            IEventRegistry eventRegistry,
            Class<? extends LogisticContainerChangedEvent<IEnergyContainer>> eventClass) {
        super(componentType, eventRegistry, eventClass);
    }

    @Override
    protected LogisticContainerChangedEvent<IEnergyContainer> createEvent(
            Ref<ChunkStore> blockRef, Store<ChunkStore> store, LogisticChangeType changeType,
            LogisticContainerComponent<IEnergyContainer> component) {
        return new EnergyContainerChangedEvent(blockRef, store, changeType, component);

    }
}