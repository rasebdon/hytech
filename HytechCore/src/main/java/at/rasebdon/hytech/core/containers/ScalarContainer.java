package at.rasebdon.hytech.core.containers;

import javax.annotation.Nullable;

/// A container holding a single fungible quantity: energy, heat, or one fluid or gas.
///
/// Everything the transfer system needs is derived here, so a scalar resource type only has
/// to supply an amount, a capacity, and the two mutators. Energy was the first of these and
/// its interface was already this shape under resource-specific names.
public interface ScalarContainer extends LogisticContainer {

    long getAmount();

    long getTotalCapacity();

    /* ---------------- Derived values ---------------- */

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

    /* ---------------- Mutations ---------------- */

    /// Adds `amount`, clamped to the remaining capacity.
    void add(long amount);

    /// Removes `amount`, clamped to what is actually held.
    void reduce(long amount);

    /// Change since the last [#updateDelta], for UI readouts. Purely presentational -- no
    /// transfer decision depends on it.
    long getDelta();

    /// Snapshots the current amount so the next [#getDelta] measures from here.
    void updateDelta();

    @Override
    default long moveTo(@Nullable LogisticContainer target, long maxAmount) {
        if (maxAmount <= 0L) return 0L;

        // A network only ever holds one container family, so a mismatch here is a wiring
        // bug rather than something to handle.
        if (!(target instanceof ScalarContainer to)) return 0L;
        if (to == this) return 0L;

        long moved = Math.min(maxAmount, Math.min(getAvailable(), to.getAcceptable()));
        if (moved <= 0L) return 0L;

        reduce(moved);
        to.add(moved);

        return moved;
    }
}
