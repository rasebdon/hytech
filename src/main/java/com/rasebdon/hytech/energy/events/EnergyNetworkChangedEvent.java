package com.rasebdon.hytech.energy.events;

import com.rasebdon.hytech.core.events.LogisticChangeType;
import com.rasebdon.hytech.core.events.LogisticNetworkChangedEvent;
import com.rasebdon.hytech.energy.IEnergyContainer;
import com.rasebdon.hytech.energy.components.EnergyPipeComponent;
import com.rasebdon.hytech.energy.networks.EnergyNetwork;

public class EnergyNetworkChangedEvent extends LogisticNetworkChangedEvent<EnergyNetwork, EnergyPipeComponent, IEnergyContainer> {
    public EnergyNetworkChangedEvent(EnergyNetwork network, LogisticChangeType changeType) {
        super(network, changeType);
    }
}
