package at.rasebdon.hytech.core.events;

import at.rasebdon.hytech.core.networks.LogisticNetwork;

public abstract class LogisticNetworkChangedEvent<TContainer> extends LogisticChangedEvent<LogisticNetwork<TContainer>> {

    protected LogisticNetworkChangedEvent(LogisticNetwork<TContainer> network, LogisticChangeType changeType) {
        super(network, changeType);
    }
}

