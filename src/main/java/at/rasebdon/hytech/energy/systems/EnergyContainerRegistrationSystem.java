package at.rasebdon.hytech.energy.systems;

import at.rasebdon.hytech.core.components.LogisticBlockComponent;
import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.systems.LogisticContainerRegistrationSystem;
import at.rasebdon.hytech.energy.IEnergyContainer;
import at.rasebdon.hytech.energy.events.EnergyContainerChangedEvent;
import at.rasebdon.hytech.energy.networks.EnergyNetworkSystem;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

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