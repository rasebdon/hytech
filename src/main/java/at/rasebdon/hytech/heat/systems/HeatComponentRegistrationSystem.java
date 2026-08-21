package at.rasebdon.hytech.heat.systems;

import at.rasebdon.hytech.core.components.LogisticBlockComponent;
import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.networks.LogisticNetworkSystem;
import at.rasebdon.hytech.core.systems.LogisticComponentRegistrationSystem;
import at.rasebdon.hytech.heat.HytechHeatContainer;
import at.rasebdon.hytech.heat.events.HeatContainerChangedEvent;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public class HeatComponentRegistrationSystem
        extends LogisticComponentRegistrationSystem<HytechHeatContainer> {

    public HeatComponentRegistrationSystem(
            ComponentType<ChunkStore, ? extends LogisticBlockComponent<HytechHeatContainer>> blockType,
            ComponentType<ChunkStore, ? extends LogisticPipeComponent<HytechHeatContainer>> pipeType,
            IEventRegistry eventRegistry,
            LogisticNetworkSystem<HytechHeatContainer> networkSystem
    ) {
        super(blockType, pipeType, eventRegistry, HeatContainerChangedEvent.class, networkSystem);
    }
}
