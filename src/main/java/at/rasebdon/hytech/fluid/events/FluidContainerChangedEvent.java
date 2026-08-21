package at.rasebdon.hytech.fluid.events;

import at.rasebdon.hytech.core.components.LogisticComponent;
import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticComponentChangedEvent;
import at.rasebdon.hytech.fluid.HytechFluidContainer;

/// Exists only to be a distinct class for the event registry, which dispatches by type.
public class FluidContainerChangedEvent extends LogisticComponentChangedEvent<HytechFluidContainer> {
    public FluidContainerChangedEvent(LogisticChangeType changeType,
                                         LogisticComponent<HytechFluidContainer> component) {
        super(changeType, component);
    }
}
