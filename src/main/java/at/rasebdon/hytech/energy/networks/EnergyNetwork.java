package at.rasebdon.hytech.energy.networks;

import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.networks.LogisticNetwork;
import at.rasebdon.hytech.energy.HytechEnergyContainer;
import at.rasebdon.hytech.energy.components.EnergyPipeComponent;

import java.util.Set;

/// The aggregate buffer formed by a connected run of energy pipes.
///
/// Capacity and speed are the sum and the minimum over the member pipes, so a network is
/// only as fast as its slowest segment.
public class EnergyNetwork extends LogisticNetwork<HytechEnergyContainer> implements HytechEnergyContainer {

    private long energy;
    private long lastTickEnergy;
    private long totalCapacity;
    private long transferSpeed;

    public EnergyNetwork(Set<LogisticPipeComponent<HytechEnergyContainer>> initialPipes) {
        super(initialPipes);
        recalculateStats();
    }

    @Override
    protected void onPipesChanged() {
        recalculateStats();
    }

    private void recalculateStats() {
        long stored = 0;
        long capacity = 0;
        long minSpeed = Long.MAX_VALUE;

        for (var pipe : pipes) {
            // instanceof rather than a cast: a mixed-type network would be a bug elsewhere,
            // but it should not take the whole network down with a ClassCastException.
            if (!(pipe instanceof EnergyPipeComponent energyPipe)) continue;

            stored += energyPipe.getSavedEnergy();
            capacity += energyPipe.getPipeCapacity();
            minSpeed = Math.min(minSpeed, energyPipe.getPipeTransferSpeed());
        }

        this.totalCapacity = Math.max(0, capacity);
        this.transferSpeed = minSpeed == Long.MAX_VALUE ? 0 : minSpeed;
        this.energy = Math.min(stored, this.totalCapacity);
    }

    @Override
    public HytechEnergyContainer getContainer() {
        return this;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void reload() {
        // Was an empty TODO, which meant a network never recomputed its capacity or speed
        // when its neighbourhood changed -- so attaching a battery to a live run had no
        // effect until a restart. ContainerHolder calls this on every neighbour change.
        rebuildTargets();
        recalculateStats();
    }

    /* ---------------- Container ---------------- */

    @Override
    public long getAmount() {
        return energy;
    }

    @Override
    public long getTotalCapacity() {
        return totalCapacity;
    }

    @Override
    public long getTransferSpeed() {
        return transferSpeed;
    }

    @Override
    public long getDelta() {
        return this.energy - this.lastTickEnergy;
    }

    @Override
    public void add(long amount) {
        if (amount <= 0) return;
        energy = Math.min(totalCapacity, energy + amount);
    }

    @Override
    public void reduce(long amount) {
        if (amount <= 0) return;
        energy = Math.max(0, energy - amount);
    }

    @Override
    public void updateDelta() {
        this.lastTickEnergy = this.energy;
    }
}
