package at.rasebdon.hytech.heat.networks;

import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.networks.ScalarNetwork;
import at.rasebdon.hytech.heat.HytechHeatContainer;

import java.util.Set;

/// A connected run of heat pipes, aggregated by [ScalarNetwork].
public class HeatNetwork extends ScalarNetwork<HytechHeatContainer> implements HytechHeatContainer {

    public HeatNetwork(Set<LogisticPipeComponent<HytechHeatContainer>> initialPipes) {
        super(initialPipes);
    }

    @Override
    public HytechHeatContainer getContainer() {
        return this;
    }
}
