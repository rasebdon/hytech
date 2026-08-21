package at.rasebdon.hytech.core.components;

import at.rasebdon.hytech.core.HytechCoreModule;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;

/// Lets the wrench configure a face by clicking its marker entity.
///
/// A face set to push or pull is left out of the block's model and hitbox, so its arm is
/// drawn by a marker entity instead. Without this the arm would be unclickable and the
/// face could never be cycled back.
///
/// Deliberately not persisted: the codec stores nothing, and markers are respawned from
/// the pipe's own state whenever it re-renders. Anything reading this must tolerate a
/// null component from a deserialized entity.
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

    @Override
    public Component<EntityStore> clone() {
        return new LogisticEntityProxyComponent(this.logisticContainerComponent, this.blockFace);
    }
}
