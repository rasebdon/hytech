package at.rasebdon.hytech.core.transport;

import at.rasebdon.hytech.core.components.LogisticComponent;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class LogisticNeighbor<TContainer> {
    private final TContainer container;

    @Nullable
    private final LogisticComponent<TContainer> logisticContainerComponent;

    public LogisticNeighbor(TContainer container) {
        this.container = container;
        this.logisticContainerComponent = null;
    }

    public LogisticNeighbor(LogisticComponent<TContainer> logisticContainerComponent) {
        this.container = logisticContainerComponent.getContainer();
        this.logisticContainerComponent = logisticContainerComponent;
    }

    public TContainer getContainer() {
        return container;
    }

    @Nullable
    public LogisticComponent<TContainer> getLogisticContainer() {
        return logisticContainerComponent;
    }

    public boolean allowsInputTowards(TContainer source) {
        return logisticContainerComponent == null || logisticContainerComponent.hasInputFaceTowards(source);
    }

    public boolean allowsOutputTowards(TContainer source) {
        return logisticContainerComponent == null || logisticContainerComponent.hasOutputFaceTowards(source);
    }

    public boolean isAvailable() {
        return logisticContainerComponent == null || logisticContainerComponent.isAvailable();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LogisticNeighbor<?> other)) return false;
        return Objects.equals(container, other.container);
    }

    @Override
    public int hashCode() {
        return Objects.hash(container);
    }
}
