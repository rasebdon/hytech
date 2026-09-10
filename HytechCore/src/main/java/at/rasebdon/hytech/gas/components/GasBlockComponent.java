package at.rasebdon.hytech.gas.components;

import at.rasebdon.hytech.core.components.AbstractTypedScalarBlockComponent;
import at.rasebdon.hytech.core.components.LogisticComponent;
import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticComponentChangedEvent;
import at.rasebdon.hytech.core.transport.BlockFaceConfig;
import at.rasebdon.hytech.gas.HytechGasContainer;
import at.rasebdon.hytech.gas.events.GasContainerChangedEvent;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/// A gas tank. All the behaviour is in [AbstractTypedScalarBlockComponent].
public class GasBlockComponent extends AbstractTypedScalarBlockComponent<HytechGasContainer>
        implements HytechGasContainer {

    @Nonnull
    public static final BuilderCodec<GasBlockComponent> CODEC =
            BuilderCodec.builder(GasBlockComponent.class, GasBlockComponent::new,
                            AbstractTypedScalarBlockComponent.CODEC)
                    .build();

    public GasBlockComponent() {
        this(new BlockFaceConfig(), 0, false, 0L, 0L, 0L, null);
    }

    public GasBlockComponent(
            BlockFaceConfig blockFaceConfig,
            int transferPriority,
            boolean isExtracting,
            long amount,
            long totalCapacity,
            long transferSpeed,
            @Nullable String resourceType) {
        super(blockFaceConfig, transferPriority, isExtracting, amount, totalCapacity,
                transferSpeed, resourceType);
    }

    @Override
    @Nonnull
    public Component<ChunkStore> clone() {
        return new GasBlockComponent(this.blockFaceConfig.clone(), this.transferPriority,
                this.isExtracting, this.amount, this.totalCapacity, this.transferSpeed,
                this.resourceType);
    }

    @Override
    protected LogisticComponentChangedEvent<HytechGasContainer> createContainerChangedEvent(
            LogisticChangeType type, LogisticComponent<HytechGasContainer> component) {
        return new GasContainerChangedEvent(type, component);
    }

    @Override
    public HytechGasContainer getContainer() {
        return this;
    }

    @Override
    public String toString() {
        return String.format("Gas: %s %d/%d mB (Prio: %d) | Sides: [%s]",
                this.resourceType == null ? "(empty)" : this.resourceType,
                this.amount, this.totalCapacity, this.transferPriority, describeFaces());
    }
}
