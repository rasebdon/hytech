package at.rasebdon.hytech.fluid;

import at.rasebdon.hytech.core.containers.TypedScalarContainer;

/// Single-type tank: adopts whatever fluid first enters, rejects anything else until drained.
public interface HytechFluidContainer extends TypedScalarContainer<String> {
}
