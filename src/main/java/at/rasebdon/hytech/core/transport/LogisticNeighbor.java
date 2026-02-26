package at.rasebdon.hytech.core.transport;

import at.rasebdon.hytech.core.components.ContainerHolder;
import at.rasebdon.hytech.core.components.LogisticComponent;
import org.jetbrains.annotations.Nullable;

public class LogisticNeighbor<TContainer> {
    private final ContainerHolder<TContainer> holder;

    @Nullable
    private final LogisticComponent<TContainer> logisticContainerComponent;

    public LogisticNeighbor(ContainerHolder<TContainer> holder) {
        this.holder = holder;

        if (holder instanceof LogisticComponent<TContainer> logisticComponent) {
            this.logisticContainerComponent = logisticComponent;
        } else {
            this.logisticContainerComponent = null;
        }
    }

    public ContainerHolder<TContainer> getHolder() {
        return holder;
    }

    @Nullable
    public LogisticComponent<TContainer> getLogisticContainer() {
        return this.logisticContainerComponent;
    }

    public boolean allowsInputTowards(ContainerHolder<TContainer> holder) {
        return logisticContainerComponent == null || logisticContainerComponent.hasInputOrBothTowards(holder);
    }

    public boolean allowsOutputTowards(ContainerHolder<TContainer> holder) {
        return logisticContainerComponent == null || logisticContainerComponent.hasOutputOrBothTowards(holder);
    }
}
