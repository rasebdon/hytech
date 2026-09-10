package at.rasebdon.hytech.energy.components;

import at.rasebdon.hytech.core.components.AbstractScalarPipeComponent;
import at.rasebdon.hytech.core.components.LogisticComponent;
import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticComponentChangedEvent;
import at.rasebdon.hytech.core.transport.BlockFaceConfig;
import at.rasebdon.hytech.core.transport.BlockFaceConfigType;
import at.rasebdon.hytech.energy.HytechEnergyContainer;
import at.rasebdon.hytech.energy.events.EnergyContainerChangedEvent;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

/// An energy conduit. A holder for its network's container, not a container itself.
public class EnergyPipeComponent extends AbstractScalarPipeComponent<HytechEnergyContainer> {

    @Nonnull
    public static final BuilderCodec<EnergyPipeComponent> CODEC =
            BuilderCodec.builder(EnergyPipeComponent.class, EnergyPipeComponent::new,
                            AbstractScalarPipeComponent.CODEC)
                    // Kept as "SavedEnergy" for the same compatibility reason the block keeps
                    // "Energy": existing worlds already store it under this key.
                    .append(new KeyedCodec<>("SavedEnergy", Codec.LONG),
                            (c, v) -> c.savedAmount = v,
                            (c) -> c.savedAmount)
                    .addValidator(Validators.greaterThanOrEqual(0L))
                    .documentation("Energy held by this segment when its chunk was last saved")
                    .add()
                    .build();

    public EnergyPipeComponent() {
        this(new BlockFaceConfig(), LogisticPipeComponent.DEFAULT_CONNECTION_MODEL_ASSETS, 0L, 0L, 0L);
    }

    public EnergyPipeComponent(
            BlockFaceConfig blockFaceConfig,
            Map<BlockFaceConfigType, String> connectionModelAssetNames,
            long savedEnergy,
            long pipeCapacity,
            long pipeTransferSpeed
    ) {
        super(blockFaceConfig, connectionModelAssetNames, savedEnergy, pipeCapacity, pipeTransferSpeed);
    }

    @Override
    @Nonnull
    public Component<ChunkStore> clone() {
        return new EnergyPipeComponent(this.blockFaceConfig.clone(), this.connectionModelAssetNames,
                this.savedAmount, this.pipeCapacity, this.pipeTransferSpeed);
    }

    @Override
    protected LogisticComponentChangedEvent<HytechEnergyContainer> createContainerChangedEvent(
            LogisticChangeType type, LogisticComponent<HytechEnergyContainer> component) {
        return new EnergyContainerChangedEvent(type, component);
    }

    @Override
    @Nullable
    public HytechEnergyContainer getContainer() {
        return this.network == null ? null : this.network.getContainer();
    }

    @Override
    public String toString() {
        var container = getContainer();

        if (container != null) {
            return String.format("(EnergyPipe): [NET] %d/%d RF | Sides: [%s]",
                    container.getAmount(), container.getTotalCapacity(), describeFaces());
        }

        return String.format("(EnergyPipe): %d/%d RF | Sides: [%s]",
                this.savedAmount, this.pipeCapacity, describeFaces());
    }
}
