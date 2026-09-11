package at.rasebdon.hytech.core.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/// Marks a block as an infinite source or sink, for testing a resource network without any
/// generating machinery existing yet. Resource-type agnostic: talks only to container interfaces.
public class CreativeSourceComponent implements Component<ChunkStore> {

    @Nonnull
    public static final BuilderCodec<CreativeSourceComponent> CODEC =
            BuilderCodec.builder(CreativeSourceComponent.class, CreativeSourceComponent::new)
                    .append(new KeyedCodec<>("Voiding", Codec.BOOLEAN),
                            (c, v) -> c.voiding = v,
                            (c) -> c.voiding)
                    .documentation("False keeps the container full (a source); true keeps it empty (a sink)")
                    .add()
                    .append(new KeyedCodec<>("ResourceType", Codec.STRING),
                            (c, v) -> c.resourceType = normalise(v),
                            (c) -> c.resourceType)
                    .documentation("Resource to produce, for typed containers such as fluid and gas. Ignored by energy, heat and items")
                    .add()
                    .build();

    private boolean voiding;

    @Nullable
    private String resourceType;

    public CreativeSourceComponent() {
        this(false, null);
    }

    public CreativeSourceComponent(boolean voiding, @Nullable String resourceType) {
        this.voiding = voiding;
        this.resourceType = normalise(resourceType);
    }

    @Nullable
    private static String normalise(@Nullable String resourceType) {
        if (resourceType == null) return null;

        var trimmed = resourceType.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }

    /// True for a sink that swallows everything, false for a source that never runs dry.
    public boolean isVoiding() {
        return this.voiding;
    }

    @Nullable
    public String getResourceType() {
        return this.resourceType;
    }

    /// Copy-constructed, not `super.clone()`d: `Object.clone` is a shallow copy that would share
    /// mutable state with the original.
    @SuppressWarnings({"CloneDoesntCallSuperClone", "MethodDoesntCallSuperMethod"})
    @Override
    @Nonnull
    public Component<ChunkStore> clone() {
        return new CreativeSourceComponent(this.voiding, this.resourceType);
    }

    @Override
    public String toString() {
        if (this.voiding) return "Creative void";

        return this.resourceType == null
                ? "Creative source (untyped)"
                : "Creative source (" + this.resourceType + ")";
    }
}
