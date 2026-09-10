package at.rasebdon.hytech.gas;

import at.rasebdon.hytech.core.containers.TypedScalarContainer;

/// A gas tank or pipe network: one gas at a time, identified by a string id.
///
/// Single-type on purpose, as Mekanism does it. A tank adopts whatever first enters it,
/// rejects anything else until drained, and releases the claim once empty. That keeps the
/// transfer algorithm identical to the energy one apart from a single compatibility check,
/// and means a tank is repurposed by emptying it rather than by replacing it.
public interface HytechGasContainer extends TypedScalarContainer<String> {
}
