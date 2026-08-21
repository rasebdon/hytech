package at.rasebdon.hytech.energy.networks;

import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.networks.ScalarNetwork;
import at.rasebdon.hytech.energy.HytechEnergyContainer;

import java.util.Set;

/// A connected run of energy pipes, aggregated by [ScalarNetwork].
public class EnergyNetwork extends ScalarNetwork<HytechEnergyContainer> implements HytechEnergyContainer {

    public EnergyNetwork(Set<LogisticPipeComponent<HytechEnergyContainer>> initialPipes) {
        super(initialPipes);
    }

    @Override
    public HytechEnergyContainer getContainer() {
        return this;
    }
}
