package com.rasebdon.hytech.energy.networks;

import com.rasebdon.hytech.core.events.LogisticChangeType;
import com.rasebdon.hytech.core.events.LogisticNetworkChangedEvent;
import com.rasebdon.hytech.core.networks.LogisticNetworkSystem;
import com.rasebdon.hytech.energy.IEnergyContainer;
import com.rasebdon.hytech.energy.components.EnergyPipeComponent;
import com.rasebdon.hytech.energy.events.EnergyNetworkChangedEvent;

import java.util.Set;

public class EnergyNetworkSystem extends LogisticNetworkSystem<EnergyNetwork, EnergyPipeComponent, IEnergyContainer> {
    @Override
    protected EnergyNetwork createNetwork(Set<EnergyPipeComponent> energyPipeComponents) {
        return new EnergyNetwork(energyPipeComponents);
    }

    @Override
    protected LogisticNetworkChangedEvent<EnergyNetwork, EnergyPipeComponent, IEnergyContainer> createEvent(EnergyNetwork network, LogisticChangeType changeType) {
        return new EnergyNetworkChangedEvent(network, changeType);
    }
}
