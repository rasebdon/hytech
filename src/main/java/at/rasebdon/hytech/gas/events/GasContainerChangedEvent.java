package at.rasebdon.hytech.gas.events;

import at.rasebdon.hytech.core.components.LogisticComponent;
import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticComponentChangedEvent;
import at.rasebdon.hytech.gas.HytechGasContainer;

/// Exists only to be a distinct class for the event registry, which dispatches by type.
public class GasContainerChangedEvent extends LogisticComponentChangedEvent<HytechGasContainer> {
    public GasContainerChangedEvent(LogisticChangeType changeType,
                                         LogisticComponent<HytechGasContainer> component) {
        super(changeType, component);
    }
}
