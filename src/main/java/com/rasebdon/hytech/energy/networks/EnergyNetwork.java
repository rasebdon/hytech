package com.rasebdon.hytech.energy.networks;

import com.rasebdon.hytech.core.components.LogisticPipeComponent;
import com.rasebdon.hytech.core.events.LogisticChangeType;
import com.rasebdon.hytech.core.events.LogisticNetworkChangedEvent;
import com.rasebdon.hytech.core.networks.LogisticNetwork;
import com.rasebdon.hytech.core.systems.LogisticTransferTarget;
import com.rasebdon.hytech.energy.IEnergyContainer;

import java.util.Set;

public class EnergyNetwork extends LogisticNetwork<IEnergyContainer> implements IEnergyContainer {

    private void transferEnergy(IEnergyContainer from, IEnergyContainer to, long requested) {
        if (from == null || to == null) return;

        long available = from.getEnergy();
        if (available <= 0) return;

        long remaining = to.getRemainingCapacity();
        if (remaining <= 0) return;

        long speed = Math.min(from.getTransferSpeed(), to.getTransferSpeed());
        if (speed <= 0) return;

        long amount = Math.min(Math.min(requested, speed), Math.min(available, remaining));
        if (amount <= 0) return;

        to.addEnergy(amount);
        from.reduceEnergy(amount);
    }


    @Override
    protected LogisticNetwork<IEnergyContainer> createNetwork(Set<LogisticPipeComponent<IEnergyContainer>> pipes) {
        return null;
    }

    @Override
    protected LogisticNetworkChangedEvent<IEnergyContainer> createEvent(LogisticNetwork<IEnergyContainer> network, LogisticChangeType changeType) {
        return null;
    }

    @Override
    public IEnergyContainer getNetworkContainer() {
        return null;
    }

    @Override
    public void pullFromTargets() {
        // Pull: neighbor -> pipe (invert direction of the stored edge)
        for (LogisticTransferTarget<IEnergyContainer> edge : getPullTargets()) {
            if (edge == null) continue;

            var pipe = edge.source();
            var neighbor = edge.target();
            if (pipe == null || neighbor == null) continue;

            // Pull only as much as the pipe can take.
            transferEnergy(neighbor, pipe, pipe.getRemainingCapacity());
        }
    }

    @Override
    public void pushToTargets() {
        // Push: pipe -> neighbor (direction as stored)
        for (LogisticTransferTarget<IEnergyContainer> edge : getPushTargets()) {
            if (edge == null) continue;

            IEnergyContainer pipe = edge.source();
            IEnergyContainer neighbor = edge.target();
            if (pipe == null || neighbor == null) continue;

            transferEnergy(pipe, neighbor, pipe.getEnergy());
        }
    }

    @Override
    public long getEnergy() {
        return 0;
    }

    @Override
    public long getTotalCapacity() {
        return 0;
    }

    @Override
    public long getTransferSpeed() {
        return 0;
    }

    @Override
    public void addEnergy(long amount) {

    }

    @Override
    public void reduceEnergy(long amount) {

    }

    @Override
    public IEnergyContainer getContainer() {
        return null;
    }
}
