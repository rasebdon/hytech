package at.rasebdon.hytech.energy.systems;

import at.rasebdon.hytech.core.components.LogisticBlockComponent;
import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.networks.LogisticNetworkSystem;
import at.rasebdon.hytech.core.systems.LogisticComponentRegistrationSystem;
import at.rasebdon.hytech.energy.HytechEnergyContainer;
import at.rasebdon.hytech.energy.events.EnergyContainerChangedEvent;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public class EnergyComponentRegistrationSystem extends LogisticComponentRegistrationSystem<HytechEnergyContainer> {

    public EnergyComponentRegistrationSystem(
            ComponentType<ChunkStore, ? extends LogisticBlockComponent<HytechEnergyContainer>> blockComponentType,
            ComponentType<ChunkStore, ? extends LogisticPipeComponent<HytechEnergyContainer>> pipeComponentType,
            IEventRegistry eventRegistry,
            LogisticNetworkSystem<HytechEnergyContainer> energyNetworkSystem) {
        super(blockComponentType, pipeComponentType, eventRegistry,
                EnergyContainerChangedEvent.class,
                energyNetworkSystem);
    }
}