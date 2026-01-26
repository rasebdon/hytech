package com.rasebdon.hytech.core.events;

import com.rasebdon.hytech.core.networks.LogisticNetwork;

public abstract class LogisticNetworkChangedEvent<TContainer>
        extends LogisticChangedEvent<LogisticNetwork<TContainer>> {

    protected LogisticNetworkChangedEvent(LogisticNetwork<TContainer> tContainerLogisticNetwork, LogisticChangeType changeType) {
        super(tContainerLogisticNetwork, changeType);
    }
}

