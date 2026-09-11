package at.rasebdon.hytech.core.networks;

import at.rasebdon.hytech.core.components.AbstractTypedScalarPipeComponent;
import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.containers.TypedScalarContainer;

import javax.annotation.Nullable;
import java.util.Set;

/// A connected run of typed pipes, carrying exactly one resource at a time.
public abstract class TypedScalarNetwork<TContainer> extends ScalarNetwork<TContainer>
        implements TypedScalarContainer<String> {

    @Nullable
    private String resourceType;

    protected TypedScalarNetwork(Set<LogisticPipeComponent<TContainer>> initialPipes) {
        super(initialPipes);
    }

    @Override
    protected void recalculateStats() {
        super.recalculateStats();

        // Joining two differently-typed runs: the first type found wins, mismatched contents drop.
        String found = null;
        long matching = 0L;

        for (var pipe : pipes) {
            if (!(pipe instanceof AbstractTypedScalarPipeComponent<?> typedPipe)) continue;

            var pipeType = typedPipe.getSavedResourceType();
            if (pipeType == null) continue;

            if (found == null) {
                found = pipeType;
            }

            if (found.equals(pipeType)) {
                matching += typedPipe.getSavedAmount();
            }
        }

        this.resourceType = found;
        this.amount = found == null ? 0L : Math.min(matching, this.totalCapacity);
    }

    @Override
    @Nullable
    public String getResourceType() {
        return this.resourceType;
    }

    @Override
    public void setResourceType(@Nullable String type) {
        this.resourceType = type == null || type.isBlank() ? null : type;

        if (this.resourceType == null) {
            this.amount = 0L;
        }
    }

    @Override
    public boolean isEmpty() {
        return this.resourceType == null || this.amount <= 0L;
    }
}
