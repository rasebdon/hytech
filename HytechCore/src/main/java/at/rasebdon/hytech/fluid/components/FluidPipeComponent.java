package at.rasebdon.hytech.fluid.components;

import at.rasebdon.hytech.core.components.AbstractTypedScalarPipeComponent;
import at.rasebdon.hytech.core.components.LogisticComponent;
import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticComponentChangedEvent;
import at.rasebdon.hytech.core.transport.BlockFaceConfig;
import at.rasebdon.hytech.core.transport.BlockFaceConfigType;
import at.rasebdon.hytech.fluid.HytechFluidContainer;
import at.rasebdon.hytech.fluid.events.FluidContainerChangedEvent;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

/// A fluid pipe. A holder for its network container, not a container itself.
public class FluidPipeComponent extends AbstractTypedScalarPipeComponent<HytechFluidContainer> {

    @Nonnull
    public static final BuilderCodec<FluidPipeComponent> CODEC =
            BuilderCodec.builder(FluidPipeComponent.class, FluidPipeComponent::new,
                            AbstractTypedScalarPipeComponent.CODEC)
                    .build();

    public FluidPipeComponent() {
        this(new BlockFaceConfig(), LogisticPipeComponent.DEFAULT_CONNECTION_MODEL_ASSETS,
                0L, 0L, 0L, null);
    }

    public FluidPipeComponent(
            BlockFaceConfig blockFaceConfig,
            Map<BlockFaceConfigType, String> connectionModelAssetNames,
            long savedAmount,
            long pipeCapacity,
            long pipeTransferSpeed,
            @Nullable String savedResourceType) {
        super(blockFaceConfig, connectionModelAssetNames, savedAmount, pipeCapacity,
                pipeTransferSpeed, savedResourceType);
    }

    @Override
    @Nonnull
    public Component<ChunkStore> clone() {
        return new FluidPipeComponent(this.blockFaceConfig.clone(), this.connectionModelAssetNames,
                this.savedAmount, this.pipeCapacity, this.pipeTransferSpeed, this.savedResourceType);
    }

    @Override
    protected LogisticComponentChangedEvent<HytechFluidContainer> createContainerChangedEvent(
            LogisticChangeType type, LogisticComponent<HytechFluidContainer> component) {
        return new FluidContainerChangedEvent(type, component);
    }

    @Override
    @Nullable
    public HytechFluidContainer getContainer() {
        return this.network == null ? null : this.network.getContainer();
    }

    @Override
    public String toString() {
        var container = getContainer();

        if (container != null) {
            return String.format("(FluidPipe): [NET] %s %d/%d mB | Sides: [%s]",
                    container.getResourceType() == null ? "(empty)" : container.getResourceType(),
                    container.getAmount(), container.getTotalCapacity(), describeFaces());
        }

        return String.format("(FluidPipe): %s %d/%d mB | Sides: [%s]",
                this.savedResourceType == null ? "(empty)" : this.savedResourceType,
                this.savedAmount, this.pipeCapacity, describeFaces());
    }
}
