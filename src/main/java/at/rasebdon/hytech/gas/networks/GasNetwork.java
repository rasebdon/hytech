package at.rasebdon.hytech.gas.networks;

import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.networks.TypedScalarNetwork;
import at.rasebdon.hytech.gas.HytechGasContainer;

import java.util.Set;

/// A connected run of gas pipes, carrying one gas at a time.
public class GasNetwork extends TypedScalarNetwork<HytechGasContainer>
        implements HytechGasContainer {

    public GasNetwork(Set<LogisticPipeComponent<HytechGasContainer>> initialPipes) {
        super(initialPipes);
    }

    @Override
    public HytechGasContainer getContainer() {
        return this;
    }
}
