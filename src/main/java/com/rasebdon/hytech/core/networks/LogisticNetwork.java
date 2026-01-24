package com.rasebdon.hytech.core.networks;

import com.rasebdon.hytech.core.components.ILogisticContainer;
import com.rasebdon.hytech.core.components.LogisticPipeComponent;
import com.rasebdon.hytech.core.systems.LogisticTransferTarget;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class LogisticNetwork<TContainer extends ILogisticContainer> {
    protected List<LogisticPipeComponent<TContainer>> pipes;
    protected Map<TContainer, LogisticTransferTarget<TContainer>> pullTargets;

    /// Push targets are pipe transfer targets that are either configured Normal (BOTH) or PUSH mode
    protected Map<TContainer, LogisticTransferTarget<TContainer>> pushTargets;

    protected LogisticNetwork() {
        pullTargets = new HashMap<>();
        pushTargets = new HashMap<>();
    }

    public void addPipe(LogisticPipeComponent<TContainer> pipe) {
        updateNetwork();
    }

    public void removePipe(LogisticPipeComponent<TContainer> pipe) {
        updateNetwork();
    }

    public void updateNetwork() {

    }

    public abstract void pullFromTargets();

    public abstract void pushToTargets();
}
