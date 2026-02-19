package at.rasebdon.hytech.energy.events;

import at.rasebdon.hytech.core.components.LogisticComponent;
import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticContainerChangedEvent;
import at.rasebdon.hytech.energy.HytechEnergyContainer;

public class EnergyContainerChangedEvent extends LogisticContainerChangedEvent<HytechEnergyContainer> {

    public EnergyContainerChangedEvent(LogisticChangeType changeType, LogisticComponent<HytechEnergyContainer> component) {
        super(changeType, component);
    }
}
