package at.rasebdon.hytech.core.components;

import at.rasebdon.hytech.core.HytechCoreModule;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;

/// Lets the wrench configure a face by clicking its marker entity, since a push/pull face is
/// left out of the block's own model and hitbox.
///
/// Not persisted: markers are respawned from the pipe's state on re-render, so callers must
/// tolerate a null component from a deserialized entity.
public class LogisticEntityProxyComponent implements Component<EntityStore> {

    public static final BuilderCodec<LogisticEntityProxyComponent> CODEC =
            BuilderCodec.builder(LogisticEntityProxyComponent.class, LogisticEntityProxyComponent::new)
                    .build();

    @Nullable
    private final LogisticComponent<?> logisticContainerComponent;
    private final BlockFace blockFace;

    public LogisticEntityProxyComponent(@Nullable LogisticComponent<?> logisticContainerComponent,
                                        BlockFace blockFace) {
        this.logisticContainerComponent = logisticContainerComponent;
        this.blockFace = blockFace;
    }

    public LogisticEntityProxyComponent() {
        this(null, BlockFace.None);
    }

    public static ComponentType<EntityStore, LogisticEntityProxyComponent> getComponentType() {
        return HytechCoreModule.get().getLogisticEntityProxyComponentType();
    }

    public BlockFace getBlockFace() {
        return blockFace;
    }

    @Nullable
    public LogisticComponent<?> getLogisticContainerComponent() {
        return logisticContainerComponent;
    }

    /// Copy-constructed, not `super.clone()`d: `Object.clone` is a shallow copy that would share
    /// mutable state with the original.
    @SuppressWarnings({"CloneDoesntCallSuperClone", "MethodDoesntCallSuperMethod"})
    @Override
    public Component<EntityStore> clone() {
        return new LogisticEntityProxyComponent(this.logisticContainerComponent, this.blockFace);
    }
}
