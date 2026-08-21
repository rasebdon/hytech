package at.rasebdon.hytech.fluid.systems;

import at.rasebdon.hytech.core.components.LogisticBlockComponent;
import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.networks.LogisticNetworkSystem;
import at.rasebdon.hytech.core.systems.LogisticComponentRegistrationSystem;
import at.rasebdon.hytech.fluid.HytechFluidContainer;
import at.rasebdon.hytech.fluid.events.FluidContainerChangedEvent;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public class FluidComponentRegistrationSystem
        extends LogisticComponentRegistrationSystem<HytechFluidContainer> {

    public FluidComponentRegistrationSystem(
            ComponentType<ChunkStore, ? extends LogisticBlockComponent<HytechFluidContainer>> blockType,
            ComponentType<ChunkStore, ? extends LogisticPipeComponent<HytechFluidContainer>> pipeType,
            IEventRegistry eventRegistry,
            LogisticNetworkSystem<HytechFluidContainer> networkSystem
    ) {
        super(blockType, pipeType, eventRegistry, FluidContainerChangedEvent.class, networkSystem);
    }
}
