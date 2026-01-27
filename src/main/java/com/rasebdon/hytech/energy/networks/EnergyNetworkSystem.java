package com.rasebdon.hytech.energy.networks;

import com.rasebdon.hytech.core.components.LogisticPipeComponent;
import com.rasebdon.hytech.core.events.LogisticChangeType;
import com.rasebdon.hytech.core.events.LogisticNetworkChangedEvent;
import com.rasebdon.hytech.core.networks.LogisticNetwork;
import com.rasebdon.hytech.core.networks.LogisticNetworkSystem;
import com.rasebdon.hytech.energy.IEnergyContainer;
import com.rasebdon.hytech.energy.events.EnergyNetworkChangedEvent;

import java.util.Set;

public class EnergyNetworkSystem extends LogisticNetworkSystem<IEnergyContainer> {
    @Override
    protected LogisticNetwork<IEnergyContainer> createNetwork(Set<LogisticPipeComponent<IEnergyContainer>> pipes) {
        return new EnergyNetwork(pipes);
    }

    @Override
    protected LogisticNetworkChangedEvent<IEnergyContainer> createEvent(LogisticNetwork<IEnergyContainer> network, LogisticChangeType changeType) {
        return new EnergyNetworkChangedEvent(network, changeType);
    }
}
