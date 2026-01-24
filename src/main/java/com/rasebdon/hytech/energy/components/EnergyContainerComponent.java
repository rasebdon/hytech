package com.rasebdon.hytech.energy.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.rasebdon.hytech.core.components.LogisticContainerComponent;
import com.rasebdon.hytech.core.face.BlockFaceConfig;
import com.rasebdon.hytech.energy.EnergyContainer;

import java.util.Arrays;
import java.util.stream.Collectors;

public abstract class EnergyContainerComponent extends LogisticContainerComponent<EnergyContainer> implements EnergyContainer {

    public static final BuilderCodec<EnergyContainerComponent> CODEC =
            BuilderCodec.abstractBuilder(EnergyContainerComponent.class, LogisticContainerComponent.CODEC)
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

    public EnergyContainerComponent(
            long energy,
            long totalCapacity,
            long transferSpeed,
            BlockFaceConfig blockFaceConfig,
            int transferPriority
    ) {
        super(blockFaceConfig, transferPriority);

        requireNonNegative(energy, "energy");
        requireNonNegative(totalCapacity, "totalCapacity");
        requireNonNegative(transferSpeed, "transferSpeed");

        this.totalCapacity = totalCapacity;
        this.energy = Math.min(energy, totalCapacity);
        this.transferSpeed = transferSpeed;
    }

    public EnergyContainerComponent() {
        this(0L, 0L, 0L, new BlockFaceConfig(), 0);
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " must be >= 0");
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

    public BlockFaceConfig getCurrentBlockFaceConfig() {
        return this.currentBlockFaceConfig;
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
    public EnergyContainer getContainer() {
        return this;
    }
}
