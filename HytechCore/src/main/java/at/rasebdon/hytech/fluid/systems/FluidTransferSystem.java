package at.rasebdon.hytech.fluid.systems;

import at.rasebdon.hytech.core.systems.AbstractTransferSystem;
import at.rasebdon.hytech.fluid.HytechFluidContainer;
import at.rasebdon.hytech.fluid.events.FluidContainerChangedEvent;
import at.rasebdon.hytech.fluid.events.FluidNetworkChangedEvent;
import com.hypixel.hytale.event.IEventRegistry;

/// The single-type rule is enforced by the container's canAccept check during the move.
public class FluidTransferSystem extends AbstractTransferSystem<HytechFluidContainer> {

    public FluidTransferSystem(IEventRegistry eventRegistry) {
        super(eventRegistry, FluidContainerChangedEvent.class, FluidNetworkChangedEvent.class);
    }
}
