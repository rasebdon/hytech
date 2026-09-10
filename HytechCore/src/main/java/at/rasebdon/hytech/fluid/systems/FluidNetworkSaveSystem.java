package at.rasebdon.hytech.fluid.systems;

import at.rasebdon.hytech.core.networks.LogisticNetwork;
import at.rasebdon.hytech.core.networks.LogisticNetworkSystem;
import at.rasebdon.hytech.core.systems.TypedScalarNetworkSaveSystem;
import at.rasebdon.hytech.fluid.HytechFluidContainer;

public class FluidNetworkSaveSystem extends TypedScalarNetworkSaveSystem<HytechFluidContainer> {

    public FluidNetworkSaveSystem(LogisticNetworkSystem<HytechFluidContainer> networkSystem) {
        super(networkSystem);
    }

    @Override
    protected long amountOf(LogisticNetwork<HytechFluidContainer> network) {
        var container = network.getContainer();

        return container == null ? 0L : container.getAmount();
    }
}
