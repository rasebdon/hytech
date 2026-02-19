package at.rasebdon.hytech.energy.events;

import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticNetworkChangedEvent;
import at.rasebdon.hytech.core.networks.LogisticNetwork;
import at.rasebdon.hytech.energy.HytechEnergyContainer;

public class EnergyNetworkChangedEvent extends LogisticNetworkChangedEvent<HytechEnergyContainer> {
    public EnergyNetworkChangedEvent(LogisticNetwork<HytechEnergyContainer> network, LogisticChangeType changeType) {
        super(network, changeType);
    }
}
