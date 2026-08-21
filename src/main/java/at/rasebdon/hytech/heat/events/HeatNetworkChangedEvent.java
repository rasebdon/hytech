package at.rasebdon.hytech.heat.events;

import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticNetworkChangedEvent;
import at.rasebdon.hytech.core.networks.LogisticNetwork;
import at.rasebdon.hytech.heat.HytechHeatContainer;

/// Exists only to be a distinct class for the event registry, which dispatches by type.
public class HeatNetworkChangedEvent extends LogisticNetworkChangedEvent<HytechHeatContainer> {
    public HeatNetworkChangedEvent(LogisticNetwork<HytechHeatContainer> network, LogisticChangeType changeType) {
        super(network, changeType);
    }
}
