package at.rasebdon.hytech.energy.components;

import at.rasebdon.hytech.core.components.LogisticComponent;
import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticComponentChangedEvent;
import at.rasebdon.hytech.core.transport.BlockFaceConfig;
import at.rasebdon.hytech.core.transport.BlockFaceConfigType;
import at.rasebdon.hytech.core.util.Validation;
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

/// An energy pipe is a *holder*, not a container.
///
/// It reports the network's container as its own, which is all the framework ever asks for.
/// It used to also implement the container interface itself and forward every method to the
/// network, which bought nothing and meant an unnetworked pipe threw on any read.
public class EnergyPipeComponent extends LogisticPipeComponent<HytechEnergyContainer> {

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
                LogisticPipeComponent.DEFAULT_CONNECTION_MODEL_ASSETS);
    }

    @Override
    public boolean isAvailable() {
        return this.network != null;
    }

    @Override
    @Nullable
    public HytechEnergyContainer getContainer() {
        return getNetworkContainer();
    }

    @Override
    @Nonnull
    public Component<ChunkStore> clone() {
        return new EnergyPipeComponent(this.savedEnergy, this.pipeCapacity,
                this.pipeTransferSpeed, this.blockFaceConfig.clone(), this.connectionModelAssetNames);
    }

    @Override
    protected LogisticComponentChangedEvent<HytechEnergyContainer> createContainerChangedEvent(LogisticChangeType type, LogisticComponent<HytechEnergyContainer> component) {
        return new EnergyContainerChangedEvent(type, component);
    }

    /// The network's container, or null while this pipe is not yet part of one.
    ///
    /// Returns null rather than throwing: pipes are routinely read during placement and
    /// teardown, before a network exists, and the transfer system tolerates a null container.
    @Nullable
    private HytechEnergyContainer getNetworkContainer() {
        return network == null ? null : network.getContainer();
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
        var container = getNetworkContainer();

        if (container != null) {
            return String.format("(EnergyPipe): [NET] %d/%d RF | Sides: [%s]",
                    container.getAmount(), container.getTotalCapacity(), describeFaces());
        }

        return String.format("(EnergyPipe): %d/%d RF | Sides: [%s]",
                savedEnergy, pipeCapacity, describeFaces());
    }
}
