package at.rasebdon.hytech.fluid.networks;

import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.networks.TypedScalarNetwork;
import at.rasebdon.hytech.fluid.HytechFluidContainer;

import java.util.Set;

public class FluidNetwork extends TypedScalarNetwork<HytechFluidContainer>
        implements HytechFluidContainer {

    public FluidNetwork(Set<LogisticPipeComponent<HytechFluidContainer>> initialPipes) {
        super(initialPipes);
    }

    @Override
    public HytechFluidContainer getContainer() {
        return this;
    }
}
