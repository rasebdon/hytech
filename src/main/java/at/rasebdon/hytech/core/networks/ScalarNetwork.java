package at.rasebdon.hytech.core.networks;

import at.rasebdon.hytech.core.components.AbstractScalarPipeComponent;
import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.containers.ScalarContainer;

import java.util.Set;

/// The aggregate buffer formed by a connected run of scalar pipes.
///
/// Capacity is the sum over member pipes and speed is the minimum, so a network is only as
/// fast as its slowest segment -- one slow pipe throttles a whole run, which is the behaviour
/// players expect from a tiered pipe system.
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
        // Called by ContainerHolder on every neighbour change. Doing nothing here means the
        // network never notices a block being attached, which is what the energy version used
        // to get wrong.
        rebuildTargets();
        recalculateStats();
    }

    /// Recomputes capacity, speed and contents from the member pipes.
    ///
    /// Protected so a typed network can also derive which resource the run is carrying.
    protected void recalculateStats() {
        long stored = 0;
        long capacity = 0;
        long minSpeed = Long.MAX_VALUE;

        for (var pipe : pipes) {
            // instanceof rather than a cast: a network mixing pipe classes would be a bug
            // elsewhere, and it should not take the whole network down with it.
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

    /* ---------------- Container ---------------- */

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
