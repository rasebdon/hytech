package at.rasebdon.hytech.core.components;

import at.rasebdon.hytech.core.HytechCoreModule;
import at.rasebdon.hytech.core.LogisticResourceType;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/// Which resource a player's wrench is currently configuring.
///
/// A block can carry several logistic containers -- the burner generator has both energy and
/// items -- so "cycle this face" is ambiguous until you say *which* resource's face. This is
/// the answer, held per player and persisted so it survives a relog.
///
/// Stored as the resource **id** rather than an index, because indices shift the moment a module
/// is added or reordered, which would silently repoint every player's wrench.
public class WrenchModeComponent implements Component<EntityStore> {

    @Nonnull
    public static final BuilderCodec<WrenchModeComponent> CODEC =
            BuilderCodec.builder(WrenchModeComponent.class, WrenchModeComponent::new)
                    .append(new KeyedCodec<>("ResourceId", Codec.STRING),
                            (c, v) -> c.resourceId = v,
                            (c) -> c.resourceId)
                    .documentation("Id of the resource type the wrench is configuring")
                    .add()
                    .build();

    @Nullable
    private String resourceId;

    public WrenchModeComponent() {
        this(null);
    }

    public WrenchModeComponent(@Nullable String resourceId) {
        this.resourceId = resourceId;
    }

    /// The selected resource, or the first registered one if nothing is selected yet or the
    /// stored id no longer exists.
    @Nullable
    public LogisticResourceType resolve() {
        var types = HytechCoreModule.get().getResourceTypes();
        if (types.isEmpty()) return null;

        if (this.resourceId != null) {
            var found = HytechCoreModule.get().getResourceType(this.resourceId);
            if (found != null) return found;
        }

        return types.getFirst();
    }

    /// Selects a resource directly, for the picker menu.
    public void select(@Nullable String resourceId) {
        this.resourceId = resourceId;
    }

    /// Copy-constructed rather than `super.clone()`d. `Component` extends `Cloneable`, but
    /// `Object.clone` is a shallow field copy, which for a component means the copy and the
    /// original share their mutable state -- a face config, a container. Every component here
    /// builds a fresh instance instead, and the ones holding a mutable field copy it explicitly.
    @SuppressWarnings({"CloneDoesntCallSuperClone", "MethodDoesntCallSuperMethod"})
    @Override
    @Nonnull
    public Component<EntityStore> clone() {
        return new WrenchModeComponent(this.resourceId);
    }

    @Override
    public String toString() {
        var resolved = resolve();

        return resolved == null ? "Wrench mode: none" : "Wrench mode: " + resolved.label();
    }
}
