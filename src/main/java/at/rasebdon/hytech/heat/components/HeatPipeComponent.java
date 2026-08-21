package at.rasebdon.hytech.heat.components;

import at.rasebdon.hytech.core.components.AbstractScalarPipeComponent;
import at.rasebdon.hytech.core.components.LogisticComponent;
import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticComponentChangedEvent;
import at.rasebdon.hytech.core.transport.BlockFaceConfig;
import at.rasebdon.hytech.core.transport.BlockFaceConfigType;
import at.rasebdon.hytech.heat.HytechHeatContainer;
import at.rasebdon.hytech.heat.events.HeatContainerChangedEvent;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

/// An insulated duct carrying heat.
public class HeatPipeComponent extends AbstractScalarPipeComponent<HytechHeatContainer> {

    @Nonnull
    public static final BuilderCodec<HeatPipeComponent> CODEC =
            BuilderCodec.builder(HeatPipeComponent.class, HeatPipeComponent::new,
                            AbstractScalarPipeComponent.CODEC)
                    .append(new KeyedCodec<>("SavedAmount", Codec.LONG),
                            (c, v) -> c.savedAmount = v,
                            (c) -> c.savedAmount)
                    .addValidator(Validators.greaterThanOrEqual(0L))
                    .documentation("Heat held by this segment when its chunk was last saved").add()
                    .build();

    public HeatPipeComponent() {
        this(new BlockFaceConfig(), LogisticPipeComponent.DEFAULT_CONNECTION_MODEL_ASSETS, 0L, 0L, 0L);
    }

    public HeatPipeComponent(
            BlockFaceConfig blockFaceConfig,
            Map<BlockFaceConfigType, String> connectionModelAssetNames,
            long savedAmount,
            long pipeCapacity,
            long pipeTransferSpeed) {
        super(blockFaceConfig, connectionModelAssetNames, savedAmount, pipeCapacity, pipeTransferSpeed);
    }

    @Override
    @Nonnull
    public Component<ChunkStore> clone() {
        return new HeatPipeComponent(this.blockFaceConfig.clone(), this.connectionModelAssetNames,
                this.savedAmount, this.pipeCapacity, this.pipeTransferSpeed);
    }

    @Override
    protected LogisticComponentChangedEvent<HytechHeatContainer> createContainerChangedEvent(
            LogisticChangeType type, LogisticComponent<HytechHeatContainer> component) {
        return new HeatContainerChangedEvent(type, component);
    }

    @Override
    @Nullable
    public HytechHeatContainer getContainer() {
        return this.network == null ? null : this.network.getContainer();
    }

    @Override
    public String toString() {
        var container = getContainer();

        if (container != null) {
            return String.format("(HeatPipe): [NET] %d/%d HU | Sides: [%s]",
                    container.getAmount(), container.getTotalCapacity(), describeFaces());
        }

        return String.format("(HeatPipe): %d/%d HU | Sides: [%s]",
                this.savedAmount, this.pipeCapacity, describeFaces());
    }
}
