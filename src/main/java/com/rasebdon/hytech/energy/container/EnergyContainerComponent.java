package com.rasebdon.hytech.energy.container;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class EnergyContainerComponent implements Component<ChunkStore>, IEnergyContainer {
    private long energyStored;
    private long maxEnergy;
    private long maxReceive;
    private long maxExtract;

    @Nonnull
    public static final BuilderCodec<EnergyContainerComponent> CODEC;

    public EnergyContainerComponent(long energyStored, long maxEnergy, long maxReceive, long maxExtract) {
        if (maxEnergy <= 0L) {
            throw new IllegalArgumentException("maxEnergy must be > 0");
        } else if (energyStored < 0L) {
            throw new IllegalArgumentException("energyStored must be >= 0");
        } else {
            this.energyStored = Math.min(energyStored, maxEnergy);
            this.maxEnergy = maxEnergy;
            this.maxReceive = Math.max(0L, maxReceive);
            this.maxExtract = Math.max(0L, maxExtract);
        }
    }

    public EnergyContainerComponent(long maxEnergy, long maxTransfer) {
        this(0L, maxEnergy, maxTransfer, maxTransfer);
    }

    public EnergyContainerComponent(long maxEnergy) {
        this(0L, maxEnergy, maxEnergy, maxEnergy);
    }

    public EnergyContainerComponent() {
        this(0L, 10000L, 1000L, 1000L);
    }

    public long getEnergyStored() {
        return this.energyStored;
    }

    public long getMaxEnergyStored() {
        return this.maxEnergy;
    }

    public long getMaxReceive() {
        return this.maxReceive;
    }

    public long getMaxExtract() {
        return this.maxExtract;
    }

    public boolean canReceive() {
        return this.maxReceive > 0L;
    }

    public boolean canExtract() {
        return this.maxExtract > 0L;
    }

    public long receiveEnergy(long amount, boolean simulate) {
        if (this.canReceive() && amount > 0L) {
            long accepted = Math.min(amount, this.maxReceive);
            long space = this.maxEnergy - this.energyStored;
            long inserted = Math.min(space, accepted);
            if (!simulate && inserted > 0L) {
                this.energyStored += inserted;
            }

            return inserted;
        } else {
            return 0L;
        }
    }

    public long extractEnergy(long amount, boolean simulate) {
        if (this.canExtract() && amount > 0L) {
            long extracted = Math.min(amount, Math.min(this.maxExtract, this.energyStored));
            if (!simulate && extracted > 0L) {
                this.energyStored -= extracted;
            }

            return extracted;
        } else {
            return 0L;
        }
    }

    public float getFillRatio() {
        return (float)this.energyStored / (float)this.maxEnergy;
    }

    public boolean isFull() {
        return this.energyStored >= this.maxEnergy;
    }

    public boolean isEmpty() {
        return this.energyStored <= 0L;
    }

    @Nullable
    public Component<ChunkStore> clone() {
        return new EnergyContainerComponent(this.energyStored, this.maxEnergy, this.maxReceive, this.maxExtract);
    }

    public String toString() {
        return String.format("EnergyContainerComponent{%d/%d RF (recv=%d, ext=%d)}", this.energyStored, this.maxEnergy, this.maxReceive, this.maxExtract);
    }

    static {
        CODEC = BuilderCodec.builder(EnergyContainerComponent.class, EnergyContainerComponent::new)
                .append(new KeyedCodec<>("EnergyStored", Codec.LONG),
                        (c, v) -> c.energyStored = v,
                        (c) -> c.energyStored)
                .addValidator(Validators.greaterThanOrEqual(0L))
                .documentation("Current stored energy")
                .add()
                .append(new KeyedCodec<>("MaxEnergy", Codec.LONG),
                        (c, v) -> c.maxEnergy = v,
                        (c) -> c.maxEnergy)
                .addValidator(Validators.greaterThan(0L))
                .documentation("Maximum energy capacity").add()
                .append(new KeyedCodec<>("MaxReceive", Codec.LONG),
                        (c, v) -> c.maxReceive = v,
                        (c) -> c.maxReceive)
                .addValidator(Validators.greaterThanOrEqual(0L))
                .documentation("Maximum energy accepted per receive call").add()
                .append(new KeyedCodec<>("MaxExtract", Codec.LONG),
                        (c, v) -> c.maxExtract = v,
                        (c) -> c.maxExtract)
                .addValidator(Validators.greaterThanOrEqual(0L))
                .documentation("Maximum energy extracted per extract call").add()
                .build();
    }
}
