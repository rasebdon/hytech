package at.rasebdon.hytech.energy.components;

import at.rasebdon.hytech.core.components.LogisticContainerComponent;
import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticContainerChangedEvent;
import at.rasebdon.hytech.core.systems.PipeRenderHelper;
import at.rasebdon.hytech.core.transport.BlockFaceConfig;
import at.rasebdon.hytech.core.transport.BlockFaceConfigState;
import at.rasebdon.hytech.core.transport.BlockFaceConfigType;
import at.rasebdon.hytech.core.util.Validation;
import at.rasebdon.hytech.energy.EnergyContainer;
import at.rasebdon.hytech.energy.events.EnergyContainerChangedEvent;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class EnergyPipeComponent extends LogisticPipeComponent<EnergyContainer> implements EnergyContainer {

    public static final BuilderCodec<EnergyPipeComponent> CODEC =
            BuilderCodec.builder(EnergyPipeComponent.class, EnergyPipeComponent::new, LogisticPipeComponent.CODEC)
                    .append(new KeyedCodec<>("SavedEnergy", Codec.LONG),
                            (c, v) -> c.savedEnergy = v,
                            (c) -> c.savedEnergy)
                    .addValidator(Validators.greaterThanOrEqual(0L))
                    .documentation("Last saved stored energy of pipe")
                    .add()
                    .append(new KeyedCodec<>("PipeCapacity", Codec.LONG),
                            (c, v) -> c.pipeCapacity = v,
                            (c) -> c.pipeCapacity)
                    .addValidator(Validators.greaterThanOrEqual(0L))
                    .documentation("Maximum energy capacity per pipe segment").add()
                    .append(new KeyedCodec<>("PipeTransferSpeed", Codec.LONG),
                            (c, v) -> c.pipeTransferSpeed = v,
                            (c) -> c.pipeTransferSpeed)
                    .addValidator(Validators.greaterThanOrEqual(0L))
                    .documentation("Maximum energy transferred per tick").add()
                    .build();
    private long savedEnergy;
    private long pipeCapacity;
    private long pipeTransferSpeed;

    public EnergyPipeComponent(
            long savedEnergy,
            long pipeCapacity,
            long pipeTransferSpeed,
            BlockFaceConfig blockFaceConfig,
            Map<BlockFaceConfigType, String> connectionModelAssetNames
    ) {
        super(blockFaceConfig, connectionModelAssetNames);

        Validation.requireNonNegative(savedEnergy, "savedEnergy");
        Validation.requireNonNegative(pipeCapacity, "pipeCapacity");
        Validation.requireNonNegative(pipeTransferSpeed, "pipeTransferSpeed");

        this.pipeCapacity = pipeCapacity;
        this.savedEnergy = Math.min(savedEnergy, pipeCapacity);
        this.pipeTransferSpeed = pipeTransferSpeed;
    }

    public EnergyPipeComponent() {
        this(0L, 0L, 0L, new BlockFaceConfig(),
                PipeRenderHelper.DEFAULT_CONNECTION_MODEL_ASSETS);
    }

    @Override
    public boolean isAvailable() {
        return this.network != null;
    }

    @Override
    public EnergyContainer getContainer() {
        return getNetworkContainer();
    }

    @Override
    @Nonnull
    public Component<ChunkStore> clone() {
        return new EnergyPipeComponent(this.savedEnergy, this.pipeCapacity,
                this.pipeTransferSpeed, this.blockFaceConfig.clone(), this.connectionModelAssetNames);
    }

    @Override
    protected LogisticContainerChangedEvent<EnergyContainer> createContainerChangedEvent(LogisticChangeType type, LogisticContainerComponent<EnergyContainer> component) {
        return new EnergyContainerChangedEvent(type, component);
    }

    @Override
    public long getEnergy() {
        return getNetworkContainer().getEnergy();
    }

    @Override
    public long getTotalCapacity() {
        return getNetworkContainer().getTotalCapacity();
    }

    @Override
    public long getTransferSpeed() {
        return getNetworkContainer().getTransferSpeed();
    }

    @Override
    public long getEnergyDelta() {
        return this.getNetworkContainer().getEnergyDelta();
    }

    @Override
    public void addEnergy(long amount) {
        getNetworkContainer().addEnergy(amount);
    }

    @Override
    public void reduceEnergy(long amount) {
        getNetworkContainer().reduceEnergy(amount);
    }

    @Override
    public void updateEnergyDelta() {
    }

    private EnergyContainer getNetworkContainer() {
        if (network == null) {
            throw new IllegalStateException("EnergyPipe has no network");
        }
        return network.getContainer();
    }

    public long getSavedEnergy() {
        return savedEnergy;
    }

    public void setSavedEnergy(long energy) {
        this.savedEnergy = Math.max(0, Math.min(energy, this.pipeCapacity));
    }

    public long getPipeCapacity() {
        return this.pipeCapacity;
    }

    public long getPipeTransferSpeed() {
        return this.pipeTransferSpeed;
    }


    public String toString() {
        var sides = Arrays.stream(this.blockFaceConfig.getCurrentStates())
                .map(BlockFaceConfigState::toString)
                .collect(Collectors.joining(", "));

        if (isAvailable()) {
            var container = getNetworkContainer();
            return String.format("(EnergyPipe): [NET] %d/%d RF | Sides: [%s]",
                    container.getEnergy(), container.getTotalCapacity(), sides);
        } else {
            return String.format("(EnergyPipe): %d/%d RF | Sides: [%s]",
                    savedEnergy, pipeCapacity, sides);
        }
    }
}
