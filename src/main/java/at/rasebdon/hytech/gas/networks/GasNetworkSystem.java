package at.rasebdon.hytech.gas.networks;

import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticNetworkChangedEvent;
import at.rasebdon.hytech.core.networks.LogisticNetwork;
import at.rasebdon.hytech.core.networks.LogisticNetworkSystem;
import at.rasebdon.hytech.gas.HytechGasContainer;
import at.rasebdon.hytech.gas.events.GasNetworkChangedEvent;

import java.util.Set;

public class GasNetworkSystem extends LogisticNetworkSystem<HytechGasContainer> {

    @Override
    protected LogisticNetwork<HytechGasContainer> createNetwork(
            Set<LogisticPipeComponent<HytechGasContainer>> pipes) {
        return new GasNetwork(pipes);
    }

    @Override
    protected LogisticNetworkChangedEvent<HytechGasContainer> createEvent(
            LogisticNetwork<HytechGasContainer> network, LogisticChangeType changeType) {
        return new GasNetworkChangedEvent(network, changeType);
    }
}
