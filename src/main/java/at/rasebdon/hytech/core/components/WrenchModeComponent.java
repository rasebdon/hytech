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

    /// Advances to the next or previous resource, wrapping around.
    ///
    /// Returns the new selection so the caller can tell the player what it is.
    @Nullable
    public LogisticResourceType cycle(int direction) {
        var types = HytechCoreModule.get().getResourceTypes();
        if (types.isEmpty()) return null;

        var current = resolve();
        int index = current == null ? 0 : types.indexOf(current);

        // Math.floorMod so scrolling down past the first entry wraps to the last.
        int next = Math.floorMod(index + Integer.signum(direction), types.size());

        var selected = types.get(next);
        this.resourceId = selected.id();

        return selected;
    }

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
