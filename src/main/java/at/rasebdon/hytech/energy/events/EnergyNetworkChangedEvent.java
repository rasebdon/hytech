package at.rasebdon.hytech.energy.events;

import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticNetworkChangedEvent;
import at.rasebdon.hytech.core.networks.LogisticNetwork;
import at.rasebdon.hytech.energy.IEnergyContainer;

public class EnergyNetworkChangedEvent extends LogisticNetworkChangedEvent<IEnergyContainer> {
    public EnergyNetworkChangedEvent(LogisticNetwork<IEnergyContainer> network, LogisticChangeType changeType) {
        super(network, changeType);
    }
}
