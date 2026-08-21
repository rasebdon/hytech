package at.rasebdon.hytech.core.components;

import at.rasebdon.hytech.core.transport.BlockFaceConfig;
import at.rasebdon.hytech.core.transport.BlockFaceConfigType;
import at.rasebdon.hytech.core.util.Validation;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;

import java.util.Map;

/// A pipe segment carrying one scalar resource.
///
/// A pipe is a *holder*, not a container: it reports its network's container as its own, and
/// deliberately does not implement the container interface itself. An earlier version of the
/// energy pipe did, forwarding every method to the network, which bought nothing and made an
/// unnetworked pipe throw on any read.
///
/// What it does own is its share of the network's capacity, plus whatever was in it when the
/// chunk was last saved -- see [at.rasebdon.hytech.core.systems.ScalarNetworkSaveSystem].
public abstract class AbstractScalarPipeComponent<TContainer> extends LogisticPipeComponent<TContainer> {

    @SuppressWarnings("rawtypes")
    public static final BuilderCodec<AbstractScalarPipeComponent> CODEC =
            BuilderCodec.abstractBuilder(AbstractScalarPipeComponent.class, LogisticPipeComponent.CODEC)
                    .append(new KeyedCodec<>("PipeCapacity", Codec.LONG),
                            (c, v) -> c.pipeCapacity = v,
                            (c) -> c.pipeCapacity)
                    .addValidator(Validators.greaterThanOrEqual(0L))
                    .documentation("Amount this pipe segment contributes to its network's capacity").add()
                    .append(new KeyedCodec<>("PipeTransferSpeed", Codec.LONG),
                            (c, v) -> c.pipeTransferSpeed = v,
                            (c) -> c.pipeTransferSpeed)
                    .addValidator(Validators.greaterThanOrEqual(0L))
                    .documentation("Maximum amount this segment moves per transfer pass").add()
                    .build();

    protected long savedAmount;
    protected long pipeCapacity;
    protected long pipeTransferSpeed;

    protected AbstractScalarPipeComponent(
            BlockFaceConfig blockFaceConfig,
            Map<BlockFaceConfigType, String> connectionModelAssetNames,
            long savedAmount,
            long pipeCapacity,
            long pipeTransferSpeed) {
        super(blockFaceConfig, connectionModelAssetNames);

        Validation.requireNonNegative(savedAmount, "savedAmount");
        Validation.requireNonNegative(pipeCapacity, "pipeCapacity");
        Validation.requireNonNegative(pipeTransferSpeed, "pipeTransferSpeed");

        this.pipeCapacity = pipeCapacity;
        this.savedAmount = Math.min(savedAmount, pipeCapacity);
        this.pipeTransferSpeed = pipeTransferSpeed;
    }

    public long getSavedAmount() {
        return this.savedAmount;
    }

    public void setSavedAmount(long amount) {
        this.savedAmount = Math.max(0, Math.min(amount, this.pipeCapacity));
    }

    public long getPipeCapacity() {
        return this.pipeCapacity;
    }

    public long getPipeTransferSpeed() {
        return this.pipeTransferSpeed;
    }

    @Override
    public boolean isAvailable() {
        return this.network != null;
    }
}
