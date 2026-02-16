package at.rasebdon.hytech.energy.systems;

import at.rasebdon.hytech.core.components.LogisticBlockComponent;
import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.networks.LogisticNetworkSystem;
import at.rasebdon.hytech.core.systems.LogisticContainerRegistrationSystem;
import at.rasebdon.hytech.energy.EnergyContainer;
import at.rasebdon.hytech.energy.events.EnergyContainerChangedEvent;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public class EnergyContainerRegistrationSystem extends LogisticContainerRegistrationSystem<EnergyContainer> {

    public EnergyContainerRegistrationSystem(
            ComponentType<ChunkStore, ? extends LogisticBlockComponent<EnergyContainer>> blockComponentType,
            ComponentType<ChunkStore, ? extends LogisticPipeComponent<EnergyContainer>> pipeComponentType,
            IEventRegistry eventRegistry,
            LogisticNetworkSystem<EnergyContainer> energyNetworkSystem) {
        super(blockComponentType, pipeComponentType, eventRegistry,
                EnergyContainerChangedEvent.class,
                energyNetworkSystem);
    }
}