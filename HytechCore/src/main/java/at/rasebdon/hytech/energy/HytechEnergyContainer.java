package at.rasebdon.hytech.energy;

import at.rasebdon.hytech.core.containers.ScalarContainer;

public interface HytechEnergyContainer extends ScalarContainer {

    default long getEnergy() {
        return getAmount();
    }
}
