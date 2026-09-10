package at.rasebdon.hytech.heat.events;

import at.rasebdon.hytech.core.components.LogisticComponent;
import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticComponentChangedEvent;
import at.rasebdon.hytech.heat.HytechHeatContainer;

/// Exists only to be a distinct class for the event registry, which dispatches by type.
public class HeatContainerChangedEvent extends LogisticComponentChangedEvent<HytechHeatContainer> {
    public HeatContainerChangedEvent(LogisticChangeType type, LogisticComponent<HytechHeatContainer> component) {
        super(type, component);
    }
}
