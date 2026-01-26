package com.rasebdon.hytech.energy.events;

import com.rasebdon.hytech.core.events.LogisticChangeType;
import com.rasebdon.hytech.core.events.LogisticNetworkChangedEvent;
import com.rasebdon.hytech.core.networks.LogisticNetwork;
import com.rasebdon.hytech.energy.IEnergyContainer;

public class EnergyNetworkChangedEvent extends LogisticNetworkChangedEvent<IEnergyContainer> {
    public EnergyNetworkChangedEvent(LogisticNetwork<IEnergyContainer> iEnergyContainerLogisticNetwork, LogisticChangeType changeType) {
        super(iEnergyContainerLogisticNetwork, changeType);
    }
}
