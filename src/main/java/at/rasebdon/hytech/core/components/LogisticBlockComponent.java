package at.rasebdon.hytech.core.components;

import at.rasebdon.hytech.core.transport.BlockFaceConfig;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;

public abstract class LogisticBlockComponent<TContainer> extends LogisticContainerComponent<TContainer> {
    @SuppressWarnings("rawtypes")
    public static final BuilderCodec<LogisticBlockComponent> CODEC =
            BuilderCodec.abstractBuilder(LogisticBlockComponent.class, LogisticContainerComponent.CODEC)
                    .append(new KeyedCodec<>("TransferPriority", Codec.INTEGER),
                            (c, v) -> c.transferPriority = v,
                            (c) -> c.transferPriority)
                    .addValidator(Validators.greaterThanOrEqual(0))
                    .documentation("Priority for energy transfer, lower means resource is extracted first").add()
                    .append(new KeyedCodec<>("IsExtracting", Codec.BOOLEAN),
                            (c, v) -> c.isExtracting = v,
                            (c) -> c.isExtracting)
                    .documentation("Defines if the block is actively extracting to its output sides").add()
                    .build();

    protected int transferPriority;
    protected boolean isExtracting;

    protected LogisticBlockComponent(BlockFaceConfig blockFaceConfig,
                                     int transferPriority,
                                     boolean isExtracting) {
        super(blockFaceConfig);
        this.transferPriority = transferPriority;
        this.isExtracting = isExtracting;
    }

    public int getTransferPriority() {
        return transferPriority;
    }

    public boolean isExtracting() {
        return this.isExtracting;
    }
}
