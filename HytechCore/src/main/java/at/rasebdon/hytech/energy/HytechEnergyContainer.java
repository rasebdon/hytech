package at.rasebdon.hytech.energy;

import at.rasebdon.hytech.core.containers.ScalarContainer;

/// Energy is a plain fungible scalar, so this adds nothing to [ScalarContainer] but a name.
///
/// `getEnergy()` is kept as an alias because the pages and the read interaction read better with
/// it. There used to be four more -- `getEnergyDelta`, `addEnergy`, `reduceEnergy`,
/// `updateEnergyDelta` -- justified the same way, but nothing ever called them: the framework only
/// talks to the generic names, and so does every content mod. Aliases earn their place by being
/// used at a call site, so those went.
public interface HytechEnergyContainer extends ScalarContainer {

    default long getEnergy() {
        return getAmount();
    }
}
