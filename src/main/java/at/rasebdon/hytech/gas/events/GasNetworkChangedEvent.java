package at.rasebdon.hytech.gas.events;

import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticNetworkChangedEvent;
import at.rasebdon.hytech.core.networks.LogisticNetwork;
import at.rasebdon.hytech.gas.HytechGasContainer;

/// Exists only to be a distinct class for the event registry, which dispatches by type.
public class GasNetworkChangedEvent extends LogisticNetworkChangedEvent<HytechGasContainer> {
    public GasNetworkChangedEvent(LogisticNetwork<HytechGasContainer> network,
                                       LogisticChangeType changeType) {
        super(network, changeType);
    }
}
