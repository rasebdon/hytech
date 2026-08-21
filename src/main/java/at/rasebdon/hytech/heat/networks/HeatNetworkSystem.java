package at.rasebdon.hytech.heat.networks;

import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticNetworkChangedEvent;
import at.rasebdon.hytech.core.networks.LogisticNetwork;
import at.rasebdon.hytech.core.networks.LogisticNetworkSystem;
import at.rasebdon.hytech.heat.HytechHeatContainer;
import at.rasebdon.hytech.heat.events.HeatNetworkChangedEvent;

import java.util.Set;

public class HeatNetworkSystem extends LogisticNetworkSystem<HytechHeatContainer> {

    @Override
    protected LogisticNetwork<HytechHeatContainer> createNetwork(
            Set<LogisticPipeComponent<HytechHeatContainer>> pipes) {
        return new HeatNetwork(pipes);
    }

    @Override
    protected LogisticNetworkChangedEvent<HytechHeatContainer> createEvent(
            LogisticNetwork<HytechHeatContainer> network, LogisticChangeType changeType) {
        return new HeatNetworkChangedEvent(network, changeType);
    }
}
