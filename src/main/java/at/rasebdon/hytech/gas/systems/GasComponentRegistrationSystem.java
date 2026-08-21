package at.rasebdon.hytech.gas.systems;

import at.rasebdon.hytech.core.components.LogisticBlockComponent;
import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.networks.LogisticNetworkSystem;
import at.rasebdon.hytech.core.systems.LogisticComponentRegistrationSystem;
import at.rasebdon.hytech.gas.HytechGasContainer;
import at.rasebdon.hytech.gas.events.GasContainerChangedEvent;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public class GasComponentRegistrationSystem
        extends LogisticComponentRegistrationSystem<HytechGasContainer> {

    public GasComponentRegistrationSystem(
            ComponentType<ChunkStore, ? extends LogisticBlockComponent<HytechGasContainer>> blockType,
            ComponentType<ChunkStore, ? extends LogisticPipeComponent<HytechGasContainer>> pipeType,
            IEventRegistry eventRegistry,
            LogisticNetworkSystem<HytechGasContainer> networkSystem
    ) {
        super(blockType, pipeType, eventRegistry, GasContainerChangedEvent.class, networkSystem);
    }
}
