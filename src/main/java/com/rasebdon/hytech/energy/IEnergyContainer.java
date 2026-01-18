package com.rasebdon.hytech.energy;

public interface IEnergyContainer {
    long getEnergyStored();

    long getMaxEnergyStored();

    long getMaxReceive();

    long getMaxExtract();

    boolean canReceive();

    boolean canExtract();

    long receiveEnergy(long var1, boolean var3);

    long extractEnergy(long var1, boolean var3);

    float getFillRatio();

    boolean isFull();

    boolean isEmpty();
}


