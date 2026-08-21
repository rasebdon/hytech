package at.rasebdon.hytech.energy;

import at.rasebdon.hytech.core.containers.ScalarContainer;

/// Energy is a plain fungible scalar, so this adds nothing to [ScalarContainer] but names.
///
/// The energy-flavoured accessors are kept as aliases because the UI templates and the read
/// interaction read far better with them, and they cost nothing -- but the framework only
/// ever talks to the generic names, so nothing here is load bearing.
public interface HytechEnergyContainer extends ScalarContainer {

    default long getEnergy() {
        return getAmount();
    }

    default long getEnergyDelta() {
        return getDelta();
    }

    default void addEnergy(long amount) {
        add(amount);
    }

    default void reduceEnergy(long amount) {
        reduce(amount);
    }

    default void updateEnergyDelta() {
        updateDelta();
    }
}
