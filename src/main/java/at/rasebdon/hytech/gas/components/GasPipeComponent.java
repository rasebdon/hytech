package at.rasebdon.hytech.gas.components;

import at.rasebdon.hytech.core.components.AbstractTypedScalarPipeComponent;
import at.rasebdon.hytech.core.components.LogisticComponent;
import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticComponentChangedEvent;
import at.rasebdon.hytech.core.transport.BlockFaceConfig;
import at.rasebdon.hytech.core.transport.BlockFaceConfigType;
import at.rasebdon.hytech.gas.HytechGasContainer;
import at.rasebdon.hytech.gas.events.GasContainerChangedEvent;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

/// A gas pipe. A holder for its network container, not a container itself.
public class GasPipeComponent extends AbstractTypedScalarPipeComponent<HytechGasContainer> {

    @Nonnull
    public static final BuilderCodec<GasPipeComponent> CODEC =
            BuilderCodec.builder(GasPipeComponent.class, GasPipeComponent::new,
                            AbstractTypedScalarPipeComponent.CODEC)
                    .build();

    public GasPipeComponent() {
        this(new BlockFaceConfig(), LogisticPipeComponent.DEFAULT_CONNECTION_MODEL_ASSETS,
                0L, 0L, 0L, null);
    }

    public GasPipeComponent(
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
        return new GasPipeComponent(this.blockFaceConfig.clone(), this.connectionModelAssetNames,
                this.savedAmount, this.pipeCapacity, this.pipeTransferSpeed, this.savedResourceType);
    }

    @Override
    protected LogisticComponentChangedEvent<HytechGasContainer> createContainerChangedEvent(
            LogisticChangeType type, LogisticComponent<HytechGasContainer> component) {
        return new GasContainerChangedEvent(type, component);
    }

    @Override
    @Nullable
    public HytechGasContainer getContainer() {
        return this.network == null ? null : this.network.getContainer();
    }

    @Override
    public String toString() {
        var container = getContainer();

        if (container != null) {
            return String.format("(GasPipe): [NET] %s %d/%d mB | Sides: [%s]",
                    container.getResourceType() == null ? "(empty)" : container.getResourceType(),
                    container.getAmount(), container.getTotalCapacity(), describeFaces());
        }

        return String.format("(GasPipe): %s %d/%d mB | Sides: [%s]",
                this.savedResourceType == null ? "(empty)" : this.savedResourceType,
                this.savedAmount, this.pipeCapacity, describeFaces());
    }
}
