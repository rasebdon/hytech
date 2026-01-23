package com.rasebdon.hytech.energy.components;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.energy.core.BlockFaceConfig;

import javax.annotation.Nonnull;

public class SingleBlockEnergyContainerComponent extends EnergyContainerComponentBase {
    public static final BuilderCodec<SingleBlockEnergyContainerComponent> CODEC =
            BuilderCodec.builder(
                            SingleBlockEnergyContainerComponent.class,
                            SingleBlockEnergyContainerComponent::new,
                            EnergyContainerComponentBase.CODEC)
                    .build();

    public SingleBlockEnergyContainerComponent() {
        super();
    }

    public SingleBlockEnergyContainerComponent(
            long energy,
            long totalCapacity,
            long transferSpeed,
            BlockFaceConfig blockFaceConfig,
            int transferPriority
    ) {
        super(energy, totalCapacity, transferSpeed, blockFaceConfig, transferPriority);
    }

    @Override
    @Nonnull
    public Component<ChunkStore> clone() {
        return new SingleBlockEnergyContainerComponent(this.energy, this.totalCapacity,
                this.transferSpeed, this.currentBlockFaceConfig.clone(), this.transferPriority);
    }
}
