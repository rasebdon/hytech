package at.rasebdon.hytech.energy.networks;

import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.networks.ScalarNetwork;
import at.rasebdon.hytech.energy.HytechEnergyContainer;

import java.util.Set;

public class EnergyNetwork extends ScalarNetwork<HytechEnergyContainer> implements HytechEnergyContainer {

    public EnergyNetwork(Set<LogisticPipeComponent<HytechEnergyContainer>> initialPipes) {
        super(initialPipes);
    }

    @Override
    public HytechEnergyContainer getContainer() {
        return this;
    }
}
