package com.rasebdon.hytech.energy.container;

import java.util.Collection;

public interface IEnergyContainer {

    long getEnergy();
    long getTotalCapacity();
    long getTransferSpeed();

    boolean canReceiveEnergy();
    boolean canExtractEnergy();

    /* ---------------- Derived values ---------------- */

    default long getRemainingCapacity() {
        return Math.max(0L, getTotalCapacity() - getEnergy());
    }

    default boolean isFull() {
        return getEnergy() >= getTotalCapacity();
    }

    default boolean isEmpty() {
        return getEnergy() <= 0;
    }

    default float getFillRatio() {
        long capacity = getTotalCapacity();
        return capacity == 0 ? 0f : (float) getEnergy() / capacity;
    }

    /* ---------------- Mutations ---------------- */

    void addEnergy(long amount);
    void reduceEnergy(long amount);

    /* ---------------- Transfer ---------------- */

    /**
     * @return amount of energy actually transferred
     */
    long transferEnergyTo(IEnergyContainer other);

    /**
     * @return total energy transferred
     */
    long transferEnergyTo(Collection<? extends IEnergyContainer> targets);
}