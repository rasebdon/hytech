package com.rasebdon.hytech.energy.systems;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.core.components.LogisticBlockComponent;
import com.rasebdon.hytech.core.components.LogisticPipeComponent;
import com.rasebdon.hytech.core.systems.LogisticContainerRegistrationSystem;
import com.rasebdon.hytech.energy.IEnergyContainer;
import com.rasebdon.hytech.energy.events.EnergyContainerChangedEvent;
import com.rasebdon.hytech.energy.networks.EnergyNetworkSystem;

public class EnergyContainerRegistrationSystem extends LogisticContainerRegistrationSystem<IEnergyContainer> {

    public EnergyContainerRegistrationSystem(
            ComponentType<ChunkStore, ? extends LogisticBlockComponent<IEnergyContainer>> blockComponentType,
            ComponentType<ChunkStore, ? extends LogisticPipeComponent<IEnergyContainer>> pipeComponentType,
            IEventRegistry eventRegistry,
            EnergyNetworkSystem energyNetworkSystem) {
        super(blockComponentType, pipeComponentType, eventRegistry,
                EnergyContainerChangedEvent.class,
                energyNetworkSystem);
    }
}