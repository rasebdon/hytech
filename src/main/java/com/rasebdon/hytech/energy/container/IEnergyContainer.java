package com.rasebdon.hytech.energy.container;

public interface IEnergyContainer {
    long getEnergy();

    long getTotalCapacity();

    long getRemainingCapacity();

    long getTransferSpeed();

    void transferEnergyTo(IEnergyContainer other);

    void addEnergy(long amount);

    void reduceEnergy(long amount);

    float getFillRatio();

    boolean isFull();

    boolean isEmpty();
}


