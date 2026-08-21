package at.rasebdon.hytech.core.components;

import at.rasebdon.hytech.core.containers.ScalarContainer;
import at.rasebdon.hytech.core.transport.BlockFaceConfig;
import at.rasebdon.hytech.core.util.Validation;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;

/// A storage block holding one scalar resource.
///
/// Energy, heat, fluid and gas blocks are the same block with a different label on the
/// number, so everything except the resource's name lives here.
///
/// The *amount* key is not declared here on purpose. Capacity and transfer speed are already
/// spelled the same way in every asset, but the stored amount is not -- energy shipped with
/// `Energy`, and renaming it would silently zero every battery in an existing world. So each
/// subclass appends its own amount key and new types can simply use `Amount`.
public abstract class AbstractScalarBlockComponent<TContainer>
        extends LogisticBlockComponent<TContainer>
        implements ScalarContainer {

    @SuppressWarnings("rawtypes")
    public static final BuilderCodec<AbstractScalarBlockComponent> CODEC =
            BuilderCodec.abstractBuilder(AbstractScalarBlockComponent.class, LogisticBlockComponent.CODEC)
                    .append(new KeyedCodec<>("TotalCapacity", Codec.LONG),
                            (c, v) -> c.totalCapacity = v,
                            (c) -> c.totalCapacity)
                    .addValidator(Validators.greaterThanOrEqual(0L))
                    .documentation("Maximum amount this block can hold").add()
                    .append(new KeyedCodec<>("MaxTransfer", Codec.LONG),
                            (c, v) -> c.transferSpeed = v,
                            (c) -> c.transferSpeed)
                    .addValidator(Validators.greaterThanOrEqual(0L))
                    .documentation("Maximum amount transferred per transfer pass").add()
                    .build();

    protected long amount;
    protected long totalCapacity;
    protected long transferSpeed;

    private long lastPassAmount;

    protected AbstractScalarBlockComponent(
            BlockFaceConfig blockFaceConfig,
            int transferPriority,
            boolean isExtracting,
            long amount,
            long totalCapacity,
            long transferSpeed) {
        super(blockFaceConfig, transferPriority, isExtracting);

        Validation.requireNonNegative(amount, "amount");
        Validation.requireNonNegative(totalCapacity, "totalCapacity");
        Validation.requireNonNegative(transferSpeed, "transferSpeed");

        this.totalCapacity = totalCapacity;
        this.amount = Math.min(amount, totalCapacity);
        this.lastPassAmount = this.amount;
        this.transferSpeed = transferSpeed;
    }

    @Override
    public long getAmount() {
        return this.amount;
    }

    @Override
    public long getTotalCapacity() {
        return this.totalCapacity;
    }

    @Override
    public long getTransferSpeed() {
        return this.transferSpeed;
    }

    @Override
    public long getDelta() {
        return this.amount - this.lastPassAmount;
    }

    @Override
    public void add(long amount) {
        if (amount <= 0) return;

        this.amount = Math.min(this.totalCapacity, this.amount + amount);
    }

    @Override
    public void reduce(long amount) {
        if (amount <= 0) return;

        this.amount = Math.max(0, this.amount - amount);
    }

    @Override
    public void updateDelta() {
        this.lastPassAmount = this.amount;
    }

    /// A storage block is always usable; it does not depend on a network the way a pipe does.
    @Override
    public boolean isAvailable() {
        return true;
    }
}
