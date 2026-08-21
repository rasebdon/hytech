package at.rasebdon.hytech.fluid.systems;

import at.rasebdon.hytech.core.components.ContainerHolder;
import at.rasebdon.hytech.core.systems.AbstractTransferSystem;
import at.rasebdon.hytech.fluid.HytechFluidContainer;
import at.rasebdon.hytech.fluid.events.FluidContainerChangedEvent;
import at.rasebdon.hytech.fluid.events.FluidNetworkChangedEvent;
import com.hypixel.hytale.event.IEventRegistry;

/// Fluid transfer. The algorithm lives in [AbstractTransferSystem]; the single-type rule is
/// enforced by the container canAccept check during the move itself.
public class FluidTransferSystem extends AbstractTransferSystem<HytechFluidContainer> {

    public FluidTransferSystem(IEventRegistry eventRegistry) {
        super(eventRegistry, FluidContainerChangedEvent.class, FluidNetworkChangedEvent.class);
    }

    @Override
    protected void onBeforePass(ContainerHolder<HytechFluidContainer> holder) {
        if (!holder.isAvailable()) return;

        var container = holder.getContainer();
        if (container == null) return;

        container.updateDelta();
    }
}
