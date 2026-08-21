package at.rasebdon.hytech.fluid.events;

import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticNetworkChangedEvent;
import at.rasebdon.hytech.core.networks.LogisticNetwork;
import at.rasebdon.hytech.fluid.HytechFluidContainer;

/// Exists only to be a distinct class for the event registry, which dispatches by type.
public class FluidNetworkChangedEvent extends LogisticNetworkChangedEvent<HytechFluidContainer> {
    public FluidNetworkChangedEvent(LogisticNetwork<HytechFluidContainer> network,
                                       LogisticChangeType changeType) {
        super(network, changeType);
    }
}
