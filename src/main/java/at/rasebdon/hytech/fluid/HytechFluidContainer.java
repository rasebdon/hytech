package at.rasebdon.hytech.fluid;

import at.rasebdon.hytech.core.containers.TypedScalarContainer;

/// A fluid tank or pipe network: one fluid at a time, identified by a string id.
///
/// Single-type on purpose, as Mekanism does it. A tank adopts whatever first enters it,
/// rejects anything else until drained, and releases the claim once empty. That keeps the
/// transfer algorithm identical to the energy one apart from a single compatibility check,
/// and means a tank is repurposed by emptying it rather than by replacing it.
public interface HytechFluidContainer extends TypedScalarContainer<String> {
}
