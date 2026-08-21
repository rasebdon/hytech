package at.rasebdon.hytech.core.containers;

import javax.annotation.Nullable;

/// A scalar container whose contents also have an identity: a tank of *something*.
///
/// Energy and heat are fungible, so a plain [ScalarContainer] covers them. Fluids and gases
/// are not -- water must not silently merge into lava. This models a single-type tank, as
/// Mekanism does: the tank adopts the type of whatever first enters it, rejects anything
/// else until it drains, and releases the type once empty. A network therefore ends up
/// carrying one resource, which keeps the transfer algorithm identical to energy's apart
/// from one compatibility check.
///
/// @param <R> the resource identity, compared with `equals`
public interface TypedScalarContainer<R> extends ScalarContainer {

    /// What this tank currently holds, or null when empty and free to adopt anything.
    @Nullable
    R getResourceType();

    /// Sets the held type. Called with the incoming type as a tank fills, and with null once
    /// it drains, so an emptied tank does not stay reserved.
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

        // A network only ever carries one resource family, so R is the same on both ends and
        // this cast cannot fail in practice. The canAccept check below is the real guard.
        var to = (TypedScalarContainer<R>) raw;
        if (!to.canAccept(type)) return 0L;

        long moved = Math.min(maxAmount, Math.min(getAvailable(), to.getAcceptable()));
        if (moved <= 0L) return 0L;

        reduce(moved);

        // Claim the destination before adding, so a tank that was empty is never briefly
        // holding a quantity of nothing.
        if (to.getResourceType() == null) {
            to.setResourceType(type);
        }
        to.add(moved);

        // Releasing the type on empty is what lets a drained tank be reused for something
        // else without the player having to break and replace it.
        if (isEmpty()) {
            setResourceType(null);
        }

        return moved;
    }
}
