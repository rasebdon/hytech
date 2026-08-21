package at.rasebdon.hytech.core.components;

import at.rasebdon.hytech.core.containers.TypedScalarContainer;
import at.rasebdon.hytech.core.transport.BlockFaceConfig;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nullable;

/// A single-type tank: holds one resource at a time and rejects anything else until drained.
///
/// The resource identity is a plain string id so tanks are declared entirely in assets --
/// `"ResourceType": "Water"` -- with no enum to extend for every new fluid or gas.
///
/// Both the amount key and the resource-type key are declared here rather than left to
/// subclasses, because unlike energy there is no legacy naming to preserve: fluid and gas are
/// new, so they use the generic `Amount` and `ResourceType` from the start.
public abstract class AbstractTypedScalarBlockComponent<TContainer>
        extends AbstractScalarBlockComponent<TContainer>
        implements TypedScalarContainer<String> {

    @SuppressWarnings("rawtypes")
    public static final BuilderCodec<AbstractTypedScalarBlockComponent> CODEC =
            BuilderCodec.abstractBuilder(AbstractTypedScalarBlockComponent.class,
                            AbstractScalarBlockComponent.CODEC)
                    .append(new KeyedCodec<>("Amount", Codec.LONG),
                            (c, v) -> c.amount = v,
                            (c) -> c.amount)
                    .documentation("Currently stored amount").add()
                    .append(new KeyedCodec<>("ResourceType", Codec.STRING),
                            (c, v) -> c.resourceType = normalise(v),
                            (c) -> c.resourceType)
                    .documentation("Resource this tank currently holds; empty means it will adopt the first one inserted")
                    .add()
                    .build();

    @Nullable
    protected String resourceType;

    protected AbstractTypedScalarBlockComponent(
            BlockFaceConfig blockFaceConfig,
            int transferPriority,
            boolean isExtracting,
            long amount,
            long totalCapacity,
            long transferSpeed,
            @Nullable String resourceType) {
        super(blockFaceConfig, transferPriority, isExtracting, amount, totalCapacity, transferSpeed);

        this.resourceType = normalise(resourceType);

        // An amount without a type would be unreachable -- nothing can extract from a tank
        // that does not say what it holds, and nothing can insert while it looks occupied.
        if (this.resourceType == null) {
            this.amount = 0L;
        }
    }

    /// Treats blank as absent, so an asset can leave the key empty to mean "unclaimed" rather
    /// than having to omit it entirely.
    @Nullable
    protected static String normalise(@Nullable String resourceType) {
        if (resourceType == null) return null;

        var trimmed = resourceType.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    @Nullable
    public String getResourceType() {
        return this.resourceType;
    }

    @Override
    public void setResourceType(@Nullable String type) {
        this.resourceType = normalise(type);

        if (this.resourceType == null) {
            this.amount = 0L;
        }
    }

    /// Empty *and* unclaimed reads as "nothing to give", which is what routing cares about.
    @Override
    public boolean isEmpty() {
        return this.resourceType == null || this.amount <= 0L;
    }
}
