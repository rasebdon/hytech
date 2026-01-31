package com.rasebdon.hytech.energy.events;

import com.rasebdon.hytech.core.components.LogisticContainerComponent;
import com.rasebdon.hytech.core.events.LogisticChangeType;
import com.rasebdon.hytech.core.events.LogisticContainerChangedEvent;
import com.rasebdon.hytech.energy.IEnergyContainer;

public class EnergyContainerChangedEvent extends LogisticContainerChangedEvent<IEnergyContainer> {

    public EnergyContainerChangedEvent(LogisticChangeType changeType, LogisticContainerComponent<IEnergyContainer> component) {
        super(changeType, component);
    }
}
