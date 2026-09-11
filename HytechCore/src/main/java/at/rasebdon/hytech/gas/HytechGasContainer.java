package at.rasebdon.hytech.gas;

import at.rasebdon.hytech.core.containers.TypedScalarContainer;

/// A gas tank or pipe network: single-type, like Mekanism — adopts whatever enters first,
/// rejects anything else until drained and emptied.
public interface HytechGasContainer extends TypedScalarContainer<String> {
}
