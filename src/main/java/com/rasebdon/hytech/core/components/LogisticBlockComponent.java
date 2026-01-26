package com.rasebdon.hytech.core.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.rasebdon.hytech.core.face.BlockFaceConfig;

public abstract class LogisticBlockComponent<TContainer> extends LogisticContainerComponent<TContainer> {
    @SuppressWarnings("rawtypes")
    public static final BuilderCodec<LogisticBlockComponent> CODEC =
            BuilderCodec.abstractBuilder(LogisticBlockComponent.class, LogisticContainerComponent.CODEC)
                    .append(new KeyedCodec<>("TransferPriority", Codec.INTEGER),
                            (c, v) -> c.transferPriority = v,
                            (c) -> c.transferPriority)
                    .addValidator(Validators.greaterThanOrEqual(0))
                    .documentation("Priority for energy transfer, lower means resource is extracted first").add()
                    .build();

    protected int transferPriority;

    protected LogisticBlockComponent(BlockFaceConfig blockFaceConfig, int transferPriority) {
        super(blockFaceConfig);
        this.transferPriority = transferPriority;
    }

    public int getTransferPriority() {
        return transferPriority;
    }
}
