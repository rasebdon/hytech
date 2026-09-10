package at.rasebdon.hytech.fluid.components;

import at.rasebdon.hytech.core.components.AbstractTypedScalarBlockComponent;
import at.rasebdon.hytech.core.components.LogisticComponent;
import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticComponentChangedEvent;
import at.rasebdon.hytech.core.transport.BlockFaceConfig;
import at.rasebdon.hytech.fluid.HytechFluidContainer;
import at.rasebdon.hytech.fluid.events.FluidContainerChangedEvent;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/// A fluid tank. All the behaviour is in [AbstractTypedScalarBlockComponent].
public class FluidBlockComponent extends AbstractTypedScalarBlockComponent<HytechFluidContainer>
        implements HytechFluidContainer {

    @Nonnull
    public static final BuilderCodec<FluidBlockComponent> CODEC =
            BuilderCodec.builder(FluidBlockComponent.class, FluidBlockComponent::new,
                            AbstractTypedScalarBlockComponent.CODEC)
                    .build();

    public FluidBlockComponent() {
        this(new BlockFaceConfig(), 0, false, 0L, 0L, 0L, null);
    }

    public FluidBlockComponent(
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
        return new FluidBlockComponent(this.blockFaceConfig.clone(), this.transferPriority,
                this.isExtracting, this.amount, this.totalCapacity, this.transferSpeed,
                this.resourceType);
    }

    @Override
    protected LogisticComponentChangedEvent<HytechFluidContainer> createContainerChangedEvent(
            LogisticChangeType type, LogisticComponent<HytechFluidContainer> component) {
        return new FluidContainerChangedEvent(type, component);
    }

    @Override
    public HytechFluidContainer getContainer() {
        return this;
    }

    @Override
    public String toString() {
        return String.format("Fluid: %s %d/%d mB (Prio: %d) | Sides: [%s]",
                this.resourceType == null ? "(empty)" : this.resourceType,
                this.amount, this.totalCapacity, this.transferPriority, describeFaces());
    }
}
