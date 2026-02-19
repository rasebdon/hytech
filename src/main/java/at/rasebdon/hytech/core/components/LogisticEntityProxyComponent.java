package at.rasebdon.hytech.core.components;

import at.rasebdon.hytech.core.HytechCoreModule;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class LogisticEntityProxyComponent implements Component<EntityStore> {

    public static final BuilderCodec<LogisticEntityProxyComponent> CODEC =
            BuilderCodec.builder(LogisticEntityProxyComponent.class, LogisticEntityProxyComponent::new)
                    .build();

    private final LogisticComponent<?> logisticContainerComponent;
    private final BlockFace blockFace;

    public LogisticEntityProxyComponent(LogisticComponent<?> logisticContainerComponent,
                                        BlockFace blockFace) {
        this.logisticContainerComponent = logisticContainerComponent;
        this.blockFace = blockFace;
    }

    public LogisticEntityProxyComponent() {
        this.logisticContainerComponent = null;
        this.blockFace = BlockFace.None;
    }

    public static ComponentType<EntityStore, LogisticEntityProxyComponent> getComponentType() {
        return HytechCoreModule.get().getLogisticEntityProxyComponentType();
    }

    public BlockFace getBlockFace() {
        return blockFace;
    }

    public LogisticComponent<?> getLogisticContainerComponent() {
        return logisticContainerComponent;
    }

    @Override
    public Component<EntityStore> clone() {
        return new LogisticEntityProxyComponent(
                this.logisticContainerComponent,
                this.blockFace
        );
    }
}
