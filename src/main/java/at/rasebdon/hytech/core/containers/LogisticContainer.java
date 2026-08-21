package at.rasebdon.hytech.core.containers;

import javax.annotation.Nullable;

/// What the logistic framework needs of any transferable resource.
///
/// Before this existed, `TContainer` was an unbounded type variable and the framework could
/// not call a single method on it -- which is why every resource type had to ship its own
/// copy of the transfer algorithm. These six methods are the whole seam
/// [at.rasebdon.hytech.core.systems.AbstractTransferSystem] needs, so a new resource type
/// gets pull, push, priority ordering, fair-share distribution and rate limiting for free.
///
/// Deliberately not parameterised on a self type. An F-bound
/// (`LogisticContainer<T extends LogisticContainer<T>>`) would give [#moveTo] a statically
/// typed target, but the bound then has to be repeated on every generic declaration in
/// `core/` for no practical gain: a network only ever holds containers of one kind, so the
/// `instanceof` in each implementation is a guard against a bug rather than a routine cast.
public interface LogisticContainer {

    /// How much this container will move in a single transfer pass.
    ///
    /// Note this is per *pass*, not per second -- each module sets its own pass interval
    /// (see [at.rasebdon.hytech.core.systems.AbstractTransferSystem#getTransferIntervalSeconds]).
    long getTransferSpeed();

    /// Nothing left to give.
    boolean isEmpty();

    /// Cannot take any more. For slot-based containers this means no free slot, even though
    /// a partly filled stack might still accept more of its own item -- which is what makes
    /// it a routing hint rather than a hard guarantee.
    boolean isFull();

    /// How much could leave this container right now.
    long getAvailable();

    /// How much could enter right now, or [Long#MAX_VALUE] when there is no scalar bound.
    /// Sum these with [#saturatingSum] rather than `+`.
    long getAcceptable();

    /// Moves up to `maxAmount` into `target`, returning how much actually moved.
    ///
    /// The container owns this rather than the transfer system because the two resource
    /// families do it differently: a scalar subtracts and adds, while items delegate slot
    /// choice and stack merging to the vanilla container.
    long moveTo(@Nullable LogisticContainer target, long maxAmount);

    /// Adds without wrapping past [Long#MAX_VALUE], which plain `+` would do as soon as one
    /// unbounded container is in the sum.
    static long saturatingSum(long a, long b) {
        long sum = a + b;

        // Overflow shows up as a sum that cannot be reached from either operand.
        return ((a ^ sum) & (b ^ sum)) < 0 ? Long.MAX_VALUE : sum;
    }
}
