package at.rasebdon.hytech.energy.systems;

import at.rasebdon.hytech.core.networks.LogisticNetwork;
import at.rasebdon.hytech.core.networks.LogisticNetworkSystem;
import at.rasebdon.hytech.core.systems.ScalarNetworkSaveSystem;
import at.rasebdon.hytech.energy.HytechEnergyContainer;

public class EnergyNetworkSaveSystem extends ScalarNetworkSaveSystem<HytechEnergyContainer> {

    public EnergyNetworkSaveSystem(LogisticNetworkSystem<HytechEnergyContainer> networkSystem) {
        super(networkSystem);
    }

    @Override
    protected long amountOf(LogisticNetwork<HytechEnergyContainer> network) {
        var container = network.getContainer();

        return container == null ? 0L : container.getAmount();
    }
}
