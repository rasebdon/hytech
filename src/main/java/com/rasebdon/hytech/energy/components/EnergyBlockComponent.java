package com.rasebdon.hytech.energy.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.core.components.LogisticBlockComponent;
import com.rasebdon.hytech.core.transport.BlockFaceConfig;
import com.rasebdon.hytech.core.util.Validation;
import com.rasebdon.hytech.energy.IEnergyContainer;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.stream.Collectors;

public class EnergyBlockComponent extends LogisticBlockComponent<IEnergyContainer> implements IEnergyContainer {

    public static final BuilderCodec<EnergyBlockComponent> CODEC =
            BuilderCodec.builder(EnergyBlockComponent.class, EnergyBlockComponent::new, LogisticBlockComponent.CODEC)
                    .append(new KeyedCodec<>("Energy", Codec.LONG),
                            (c, v) -> c.energy = v,
                            (c) -> c.energy)
                    .addValidator(Validators.greaterThanOrEqual(0L))
                    .documentation("Currently stored energy")
                    .add()
                    .append(new KeyedCodec<>("TotalCapacity", Codec.LONG),
                            (c, v) -> c.totalCapacity = v,
                            (c) -> c.totalCapacity)
                    .addValidator(Validators.greaterThanOrEqual(0L))
                    .documentation("Maximum energy capacity").add()
                    .append(new KeyedCodec<>("MaxTransfer", Codec.LONG),
                            (c, v) -> c.transferSpeed = v,
                            (c) -> c.transferSpeed)
                    .addValidator(Validators.greaterThanOrEqual(0L))
                    .documentation("Maximum energy transferred per tick").add()
                    .build();

    protected long energy;
    protected long totalCapacity;
    protected long transferSpeed;

    public EnergyBlockComponent(
            long energy,
            long totalCapacity,
            long transferSpeed,
            BlockFaceConfig blockFaceConfig,
            int transferPriority
    ) {
        super(blockFaceConfig, transferPriority);

        Validation.requireNonNegative(energy, "energy");
        Validation.requireNonNegative(totalCapacity, "totalCapacity");
        Validation.requireNonNegative(transferSpeed, "transferSpeed");

        this.totalCapacity = totalCapacity;
        this.energy = Math.min(energy, totalCapacity);
        this.transferSpeed = transferSpeed;
    }

    public EnergyBlockComponent() {
        this(0L, 0L, 0L, new BlockFaceConfig(), 0);
    }

    @Nonnull
    public Component<ChunkStore> clone() {
        return new EnergyBlockComponent(this.energy, this.totalCapacity,
                this.transferSpeed, this.currentBlockFaceConfig.clone(), this.transferPriority);
    }

    public long getEnergy() {
        return this.energy;
    }

    public long getTotalCapacity() {
        return this.totalCapacity;
    }

    public long getTransferSpeed() {
        return this.transferSpeed;
    }

    public void addEnergy(long amount) {
        if (amount <= 0) return;
        this.energy = Math.min(this.totalCapacity, this.energy + amount);
    }

    public void reduceEnergy(long amount) {
        if (amount <= 0) return;
        this.energy = Math.max(0, this.energy - amount);
    }

    public String toString() {
        var sides = Arrays.stream(this.currentBlockFaceConfig.toArray())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
        return String.format("Energy: %d/%d RF (Prio: %d) | Sides: [%s]",
                energy, totalCapacity, transferPriority, sides);
    }

    @Override
    public IEnergyContainer getContainer() {
        return this;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
