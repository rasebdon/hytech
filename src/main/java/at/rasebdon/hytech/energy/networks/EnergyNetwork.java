package at.rasebdon.hytech.energy.networks;

import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.networks.LogisticNetwork;
import at.rasebdon.hytech.energy.IEnergyContainer;
import at.rasebdon.hytech.energy.components.EnergyPipeComponent;

import java.util.Set;

public class EnergyNetwork extends LogisticNetwork<IEnergyContainer> implements IEnergyContainer {

    private long energy;
    private long lastTickEnergy;
    private long totalCapacity;
    private long transferSpeed;

    public EnergyNetwork(Set<LogisticPipeComponent<IEnergyContainer>> initialPipes) {
        super(initialPipes);
        recalculateStats();
    }

    @Override
    protected void setPipes(Set<LogisticPipeComponent<IEnergyContainer>> newPipes) {
        super.setPipes(newPipes);
        recalculateStats();
    }

    @Override
    protected void addPipe(LogisticPipeComponent<IEnergyContainer> pipe) {
        super.addPipe(pipe);
        recalculateStats();
    }

    @Override
    protected void removePipe(LogisticPipeComponent<IEnergyContainer> pipe) {
        super.removePipe(pipe);
        recalculateStats();
    }

    private void recalculateStats() {
        energy = 0;
        long capacity = 0;
        long minSpeed = Long.MAX_VALUE;

        // TODO : Look into world saving
        for (var pipe : pipes) {
            var energyPipe = (EnergyPipeComponent) pipe;
            energy += energyPipe.getSavedEnergy();
            capacity += energyPipe.getPipeCapacity();
            minSpeed = Math.min(minSpeed, energyPipe.getPipeTransferSpeed());
        }

        this.totalCapacity = Math.max(0, capacity);
        this.transferSpeed = minSpeed == Long.MAX_VALUE ? 0 : minSpeed;

        // Clamp stored energy
        if (energy > totalCapacity) {
            energy = totalCapacity;
        }
    }

    @Override
    public void pullFromTargets() {
        if (isFull()) return;

        for (var target : pullTargets) {
            if (isFull()) break;

            if (target.isAvailable()) {
                transferEnergy(target.getContainer(), this, transferSpeed);
            }
        }
    }

    @Override
    public void pushToTargets() {
        if (isEmpty()) return;

        if (pushTargets.isEmpty()) return;

        long perTarget = Math.max(1, transferSpeed / pushTargets.size());

        for (var target : pushTargets) {
            if (isEmpty()) break;

            if (target.isAvailable()) {
                transferEnergy(this, target.getContainer(), perTarget);
            }
        }
    }

    private void transferEnergy(
            IEnergyContainer from,
            IEnergyContainer to,
            long requested
    ) {
        if (from == null || to == null) return;

        long available = from.getEnergy();
        if (available <= 0) return;

        long remaining = to.getRemainingCapacity();
        if (remaining <= 0) return;

        long speed = Math.min(from.getTransferSpeed(), to.getTransferSpeed());
        if (speed <= 0) return;

        long amount = Math.min(
                Math.min(requested, speed),
                Math.min(available, remaining)
        );

        if (amount <= 0) return;

        to.addEnergy(amount);
        from.reduceEnergy(amount);
    }

    @Override
    public IEnergyContainer getContainer() {
        return this;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public long getEnergy() {
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
    public long getEnergyDelta() {
        return this.energy - this.lastTickEnergy;
    }

    @Override
    public void addEnergy(long amount) {
        if (amount <= 0) return;
        energy = Math.min(totalCapacity, energy + amount);
    }

    @Override
    public void reduceEnergy(long amount) {
        if (amount <= 0) return;
        energy = Math.max(0, energy - amount);
    }

    @Override
    public void updateEnergyDelta() {
        this.lastTickEnergy = this.energy;
    }
}

