package at.rasebdon.hytech.heat;

import at.rasebdon.hytech.core.containers.ScalarContainer;

/// Heat is stored and moved as a fungible scalar, exactly like energy.
///
/// A deliberate simplification: real heat equalises toward a shared temperature rather than
/// accumulating, so a full heat block here simply stops accepting more instead of reaching
/// thermal equilibrium with its neighbours. Modelling gradients would need a diffusion
/// transfer system rather than the pull/push one every other resource uses.
public interface HytechHeatContainer extends ScalarContainer {
}
