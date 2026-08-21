package at.rasebdon.hytech.heat.systems;

import at.rasebdon.hytech.core.networks.LogisticNetwork;
import at.rasebdon.hytech.core.networks.LogisticNetworkSystem;
import at.rasebdon.hytech.core.systems.ScalarNetworkSaveSystem;
import at.rasebdon.hytech.heat.HytechHeatContainer;

public class HeatNetworkSaveSystem extends ScalarNetworkSaveSystem<HytechHeatContainer> {

    public HeatNetworkSaveSystem(LogisticNetworkSystem<HytechHeatContainer> networkSystem) {
        super(networkSystem);
    }

    @Override
    protected long amountOf(LogisticNetwork<HytechHeatContainer> network) {
        var container = network.getContainer();

        return container == null ? 0L : container.getAmount();
    }
}
