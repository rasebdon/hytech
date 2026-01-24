package com.rasebdon.hytech.energy.components;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.core.face.BlockFaceConfig;

import javax.annotation.Nonnull;

public class BlockEnergyContainerComponent extends EnergyContainerComponent {
    public static final BuilderCodec<BlockEnergyContainerComponent> CODEC =
            BuilderCodec.builder(
                            BlockEnergyContainerComponent.class,
                            BlockEnergyContainerComponent::new,
                            EnergyContainerComponent.CODEC)
                    .build();

    public BlockEnergyContainerComponent() {
        super();
    }

    public BlockEnergyContainerComponent(
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
        return new BlockEnergyContainerComponent(this.energy, this.totalCapacity,
                this.transferSpeed, this.currentBlockFaceConfig.clone(), this.transferPriority);
    }
}
