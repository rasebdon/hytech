package com.rasebdon.hytech.energy.container;

import java.util.Collection;

public interface IEnergyContainer {
    long getEnergy();

    long getTotalCapacity();

    long getRemainingCapacity();

    long getTransferSpeed();

    boolean canReceiveEnergy();

    boolean canExtractEnergy();

    void transferEnergyTo(IEnergyContainer other);

    void transferEnergyTo(Collection<IEnergyContainer> other);

    void addEnergy(long amount);

    void reduceEnergy(long amount);

    float getFillRatio();

    boolean isFull();

    boolean isEmpty();
}


