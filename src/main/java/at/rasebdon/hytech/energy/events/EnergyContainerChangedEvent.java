package at.rasebdon.hytech.energy.events;

import at.rasebdon.hytech.core.components.LogisticContainerComponent;
import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticContainerChangedEvent;
import at.rasebdon.hytech.energy.EnergyContainer;

public class EnergyContainerChangedEvent extends LogisticContainerChangedEvent<EnergyContainer> {

    public EnergyContainerChangedEvent(LogisticChangeType changeType, LogisticContainerComponent<EnergyContainer> component) {
        super(changeType, component);
    }
}
