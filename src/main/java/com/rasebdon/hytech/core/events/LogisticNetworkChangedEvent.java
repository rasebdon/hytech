package com.rasebdon.hytech.core.events;

import com.rasebdon.hytech.core.components.LogisticPipeComponent;
import com.rasebdon.hytech.core.networks.LogisticNetwork;

public abstract class LogisticNetworkChangedEvent<
        TNetwork extends LogisticNetwork<TNetwork, TPipe, TContainer>,
        TPipe extends LogisticPipeComponent<TNetwork, TPipe, TContainer>,
        TContainer>
        extends LogisticChangedEvent<TNetwork> {

    protected LogisticNetworkChangedEvent(TNetwork network, LogisticChangeType changeType) {
        super(network, changeType);
    }
}

