package at.rasebdon.hytech.energy.events;

import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticNetworkChangedEvent;
import at.rasebdon.hytech.core.networks.LogisticNetwork;
import at.rasebdon.hytech.energy.EnergyContainer;

public class EnergyNetworkChangedEvent extends LogisticNetworkChangedEvent<EnergyContainer> {
    public EnergyNetworkChangedEvent(LogisticNetwork<EnergyContainer> network, LogisticChangeType changeType) {
        super(network, changeType);
    }
}
