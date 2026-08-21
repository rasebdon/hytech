package at.rasebdon.hytech.fluid.networks;

import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticNetworkChangedEvent;
import at.rasebdon.hytech.core.networks.LogisticNetwork;
import at.rasebdon.hytech.core.networks.LogisticNetworkSystem;
import at.rasebdon.hytech.fluid.HytechFluidContainer;
import at.rasebdon.hytech.fluid.events.FluidNetworkChangedEvent;

import java.util.Set;

public class FluidNetworkSystem extends LogisticNetworkSystem<HytechFluidContainer> {

    @Override
    protected LogisticNetwork<HytechFluidContainer> createNetwork(
            Set<LogisticPipeComponent<HytechFluidContainer>> pipes) {
        return new FluidNetwork(pipes);
    }

    @Override
    protected LogisticNetworkChangedEvent<HytechFluidContainer> createEvent(
            LogisticNetwork<HytechFluidContainer> network, LogisticChangeType changeType) {
        return new FluidNetworkChangedEvent(network, changeType);
    }
}
