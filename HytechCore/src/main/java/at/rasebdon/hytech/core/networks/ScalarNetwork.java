package at.rasebdon.hytech.core.networks;

import at.rasebdon.hytech.core.components.AbstractScalarPipeComponent;
import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.containers.ScalarContainer;

import java.util.Set;

/// Aggregate buffer for a connected run of scalar pipes: capacity sums, speed is the minimum.
public abstract class ScalarNetwork<TContainer> extends LogisticNetwork<TContainer>
        implements ScalarContainer {

    protected long amount;
    protected long totalCapacity;
    protected long transferSpeed;

    private long lastPassAmount;

    protected ScalarNetwork(Set<LogisticPipeComponent<TContainer>> initialPipes) {
        super(initialPipes);
        recalculateStats();
    }

    @Override
    protected void onPipesChanged() {
        recalculateStats();
    }

    @Override
    public void reload() {
        rebuildTargets();
        recalculateStats();
    }

    protected void recalculateStats() {
        long stored = 0;
        long capacity = 0;
        long minSpeed = Long.MAX_VALUE;

        for (var pipe : pipes) {
            if (!(pipe instanceof AbstractScalarPipeComponent<?> scalarPipe)) continue;

            stored += scalarPipe.getSavedAmount();
            capacity += scalarPipe.getPipeCapacity();
            minSpeed = Math.min(minSpeed, scalarPipe.getPipeTransferSpeed());
        }

        this.totalCapacity = Math.max(0, capacity);
        this.transferSpeed = minSpeed == Long.MAX_VALUE ? 0 : minSpeed;
        this.amount = Math.min(stored, this.totalCapacity);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public long getAmount() {
        return this.amount;
    }

    @Override
    public long getTotalCapacity() {
        return this.totalCapacity;
    }

    @Override
    public long getTransferSpeed() {
        return this.transferSpeed;
    }

    @Override
    public long getDelta() {
        return this.amount - this.lastPassAmount;
    }

    @Override
    public void add(long amount) {
        if (amount <= 0) return;

        this.amount = Math.min(this.totalCapacity, this.amount + amount);
    }

    @Override
    public void reduce(long amount) {
        if (amount <= 0) return;

        this.amount = Math.max(0, this.amount - amount);
    }

    @Override
    public void updateDelta() {
        this.lastPassAmount = this.amount;
    }
}
