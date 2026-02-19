package at.rasebdon.hytech.energy;

public interface HytechEnergyContainer {
    long getEnergy();

    long getTotalCapacity();

    long getTransferSpeed();

    long getEnergyDelta();

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

    void updateEnergyDelta();
}