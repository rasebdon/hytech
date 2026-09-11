package at.rasebdon.hytech.heat;

import at.rasebdon.hytech.core.containers.ScalarContainer;

/// Heat is stored and moved as a fungible scalar, like energy — a deliberate simplification,
/// since real heat would equalise toward neighbours rather than just filling up and stopping.
public interface HytechHeatContainer extends ScalarContainer {
}
