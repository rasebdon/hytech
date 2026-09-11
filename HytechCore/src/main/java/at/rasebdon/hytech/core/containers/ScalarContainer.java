package at.rasebdon.hytech.core.containers;

import javax.annotation.Nullable;

/// A container holding a single fungible quantity: energy, heat, or one fluid or gas. A scalar
/// resource type only has to supply an amount, a capacity, and the two mutators below.
public interface ScalarContainer extends LogisticContainer {

    long getAmount();

    long getTotalCapacity();

    default long getRemainingCapacity() {
        return Math.max(0L, getTotalCapacity() - getAmount());
    }

    default float getFillRatio() {
        long capacity = getTotalCapacity();

        return capacity == 0L ? 0f : (float) getAmount() / capacity;
    }

    @Override
    default boolean isEmpty() {
        return getAmount() <= 0L;
    }

    @Override
    default boolean isFull() {
        return getAmount() >= getTotalCapacity();
    }

    @Override
    default long getAvailable() {
        return Math.max(0L, getAmount());
    }

    @Override
    default long getAcceptable() {
        return getRemainingCapacity();
    }

    /// Clamped to the remaining capacity.
    void add(long amount);

    /// Clamped to what is actually held.
    void reduce(long amount);

    /// Change since the last [#updateDelta]. Presentational only -- no transfer decision reads it.
    long getDelta();

    void updateDelta();

    @Override
    default long moveTo(@Nullable LogisticContainer target, long maxAmount) {
        if (maxAmount <= 0L) return 0L;

        // A mismatch here is a wiring bug, not something to handle.
        if (!(target instanceof ScalarContainer to)) return 0L;
        if (to == this) return 0L;

        long moved = Math.min(maxAmount, Math.min(getAvailable(), to.getAcceptable()));
        if (moved <= 0L) return 0L;

        reduce(moved);
        to.add(moved);

        return moved;
    }
}
