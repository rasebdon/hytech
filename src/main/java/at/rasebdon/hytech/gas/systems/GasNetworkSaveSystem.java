package at.rasebdon.hytech.gas.systems;

import at.rasebdon.hytech.core.networks.LogisticNetwork;
import at.rasebdon.hytech.core.networks.LogisticNetworkSystem;
import at.rasebdon.hytech.core.systems.TypedScalarNetworkSaveSystem;
import at.rasebdon.hytech.gas.HytechGasContainer;

public class GasNetworkSaveSystem extends TypedScalarNetworkSaveSystem<HytechGasContainer> {

    public GasNetworkSaveSystem(LogisticNetworkSystem<HytechGasContainer> networkSystem) {
        super(networkSystem);
    }

    @Override
    protected long amountOf(LogisticNetwork<HytechGasContainer> network) {
        var container = network.getContainer();

        return container == null ? 0L : container.getAmount();
    }
}
