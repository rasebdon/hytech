package at.rasebdon.hytech.energy.events;

import at.rasebdon.hytech.core.components.LogisticContainerComponent;
import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticContainerChangedEvent;
import at.rasebdon.hytech.energy.IEnergyContainer;

public class EnergyContainerChangedEvent extends LogisticContainerChangedEvent<IEnergyContainer> {

    public EnergyContainerChangedEvent(LogisticChangeType changeType, LogisticContainerComponent<IEnergyContainer> component) {
        super(changeType, component);
    }
}
