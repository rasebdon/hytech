package at.rasebdon.hytech.core.containers;

import javax.annotation.Nullable;

/// What the logistic framework needs of any transferable resource -- the whole seam
/// [at.rasebdon.hytech.core.systems.AbstractTransferSystem] needs to give a new resource type
/// pull, push, priority ordering, fair-share distribution and rate limiting for free.
///
/// Deliberately not parameterised on a self type: an F-bound would give [#moveTo] a statically
/// typed target, but a network only ever holds one container family, so the `instanceof` in each
/// implementation is a bug guard, not a routine cast.
public interface LogisticContainer {

    /// Per transfer *pass*, not per second -- each module sets its own pass interval.
    long getTransferSpeed();

    boolean isEmpty();

    /// For slot-based containers, no free slot -- even if a partial stack could still accept
    /// more of its own item. A routing hint, not a hard guarantee.
    boolean isFull();

    long getAvailable();

    /// [Long#MAX_VALUE] when there is no scalar bound. Sum with [#saturatingSum], not `+`.
    long getAcceptable();

    /// The two resource families do this differently: a scalar subtracts and adds, items
    /// delegate slot choice and stack merging to the vanilla container.
    long moveTo(@Nullable LogisticContainer target, long maxAmount);

    /// Avoids the wraparound plain `+` would do once one unbounded container is in the sum.
    static long saturatingSum(long a, long b) {
        long sum = a + b;

        return ((a ^ sum) & (b ^ sum)) < 0 ? Long.MAX_VALUE : sum;
    }
}
