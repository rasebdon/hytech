package com.rasebdon.hytech.energy.networks;

import com.rasebdon.hytech.core.components.LogisticPipeComponent;
import com.rasebdon.hytech.core.networks.LogisticNetwork;
import com.rasebdon.hytech.energy.IEnergyContainer;
import com.rasebdon.hytech.energy.components.EnergyPipeComponent;

import java.util.Set;

public class EnergyNetwork extends LogisticNetwork<IEnergyContainer> implements IEnergyContainer {

    private long energy;
    private long totalCapacity;
    private long transferSpeed;

    public EnergyNetwork(Set<LogisticPipeComponent<IEnergyContainer>> initialPipes) {
        super(initialPipes);
        recalculateStats();
    }

    @Override
    protected void resetPipes(Set<LogisticPipeComponent<IEnergyContainer>> newPipes) {
        super.resetPipes(newPipes);
        recalculateStats();
    }

    @Override
    protected void detachPipe(LogisticPipeComponent<IEnergyContainer> pipe) {
        super.detachPipe(pipe);
        recalculateStats();
    }

    private void recalculateStats() {
        energy = 0;
        long capacity = 0;
        long minSpeed = Long.MAX_VALUE;

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
            transferEnergy(target.target().getContainer(), this, transferSpeed);
        }
    }

    @Override
    public void pushToTargets() {
        if (isEmpty()) return;

        if (pushTargets.isEmpty()) return;

        long perTarget = Math.max(1, transferSpeed / pushTargets.size());

        for (var target : pushTargets) {
            if (isEmpty()) break;
            transferEnergy(this, target.target().getContainer(), perTarget);
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
    public void addEnergy(long amount) {
        if (amount <= 0) return;
        energy = Math.min(totalCapacity, energy + amount);
    }

    @Override
    public void reduceEnergy(long amount) {
        if (amount <= 0) return;
        energy = Math.max(0, energy - amount);
    }
}

