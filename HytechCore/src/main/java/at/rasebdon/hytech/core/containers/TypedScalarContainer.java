package at.rasebdon.hytech.core.containers;

import javax.annotation.Nullable;

/// A scalar container whose contents also have an identity: a single-type tank (Mekanism style)
/// that adopts whatever first enters it, rejects anything else until drained, and releases the
/// type once empty.
///
/// @param <R> the resource identity, compared with `equals`
public interface TypedScalarContainer<R> extends ScalarContainer {

    /// What this tank currently holds, or null when empty and free to adopt anything.
    @Nullable
    R getResourceType();

    void setResourceType(@Nullable R type);

    /// Whether `type` could enter: either the tank is unclaimed, or it already holds this.
    default boolean canAccept(@Nullable R type) {
        if (type == null) return false;

        R mine = getResourceType();

        return mine == null || mine.equals(type);
    }

    @Override
    @SuppressWarnings("unchecked")
    default long moveTo(@Nullable LogisticContainer target, long maxAmount) {
        if (maxAmount <= 0L) return 0L;
        if (!(target instanceof TypedScalarContainer<?> raw)) return 0L;
        if (raw == this) return 0L;

        R type = getResourceType();
        if (type == null) return 0L;

        // R is the same on both ends of a network; canAccept below is the real guard.
        var to = (TypedScalarContainer<R>) raw;
        if (!to.canAccept(type)) return 0L;

        long moved = Math.min(maxAmount, Math.min(getAvailable(), to.getAcceptable()));
        if (moved <= 0L) return 0L;

        reduce(moved);

        if (to.getResourceType() == null) {
            to.setResourceType(type);
        }
        to.add(moved);

        if (isEmpty()) {
            setResourceType(null);
        }

        return moved;
    }
}
