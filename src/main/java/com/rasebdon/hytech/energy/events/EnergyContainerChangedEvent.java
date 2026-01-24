package com.rasebdon.hytech.energy.events;

import com.rasebdon.hytech.core.components.LogisticContainerComponent;
import com.rasebdon.hytech.core.events.LogisticContainerChangedEvent;
import com.rasebdon.hytech.energy.EnergyContainer;

public class EnergyContainerChangedEvent extends LogisticContainerChangedEvent<EnergyContainer> {
    public EnergyContainerChangedEvent(ChangeType changeType, LogisticContainerComponent<EnergyContainer> container) {
        super(changeType, container);
    }
}
