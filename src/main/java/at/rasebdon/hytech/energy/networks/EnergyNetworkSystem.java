package at.rasebdon.hytech.energy.networks;

import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticNetworkChangedEvent;
import at.rasebdon.hytech.core.networks.LogisticNetwork;
import at.rasebdon.hytech.core.networks.LogisticNetworkSystem;
import at.rasebdon.hytech.energy.HytechEnergyContainer;
import at.rasebdon.hytech.energy.events.EnergyNetworkChangedEvent;

import java.util.Set;

public class EnergyNetworkSystem extends LogisticNetworkSystem<HytechEnergyContainer> {
    @Override
    protected LogisticNetwork<HytechEnergyContainer> createNetwork(Set<LogisticPipeComponent<HytechEnergyContainer>> pipes) {
        return new EnergyNetwork(pipes);
    }

    @Override
    protected LogisticNetworkChangedEvent<HytechEnergyContainer> createEvent(LogisticNetwork<HytechEnergyContainer> network, LogisticChangeType changeType) {
        return new EnergyNetworkChangedEvent(network, changeType);
    }
}
