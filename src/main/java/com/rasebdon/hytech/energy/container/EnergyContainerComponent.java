package com.rasebdon.hytech.energy.container;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.stream.Collectors;

public class EnergyContainerComponent implements Component<ChunkStore> {
    private long energyStored;
    private long maxEnergy;
    private long maxReceive;
    private long maxExtract;

    // Priority: 0 = Cable, 25 = Producer, 50 = Storage, 75 = Consumer
    private int priority = 2;

    // TODO : Persistence not working
    private SideConfig[] sideConfigs;
    private boolean isMultiblockStorage;

    @Nonnull
    public static final BuilderCodec<EnergyContainerComponent> CODEC;

    public EnergyContainerComponent(long energyStored, long maxEnergy, long maxReceive, long maxExtract,
                                    SideConfig[] sideConfigs, int priority) {
        if (sideConfigs.length != 7) {
            throw new IllegalArgumentException("sideConfigs must have length of 7");
        } else if (priority < 0) {
            throw new IllegalArgumentException("priority must be >= 0");
        } else if (maxEnergy <= 0L) {
            throw new IllegalArgumentException("maxEnergy must be > 0");
        } else if (energyStored < 0L) {
            throw new IllegalArgumentException("energyStored must be >= 0");
        } else {
            this.sideConfigs = sideConfigs;
            this.priority = priority;
            this.energyStored = Math.min(energyStored, maxEnergy);
            this.maxEnergy = maxEnergy;
            this.maxReceive = Math.max(0L, maxReceive);
            this.maxExtract = Math.max(0L, maxExtract);
        }
    }

    public EnergyContainerComponent() {
        this(0L, 10000L, 1000L, 1000L, SideConfig.getDefault(), 50);
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

    public SideConfig getSideConfig(BlockFace side) {
        return sideConfigs[side.getValue()];
    }

    public void cycleSideConfig(BlockFace side) {
        var index = side.getValue();
        this.sideConfigs[index] = this.sideConfigs[index].next();
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean canReceive(BlockFace face) {
        return maxExtract > 0 && sideConfigs[face.getValue()].canReceive();
    }

    public boolean canExtract(BlockFace face) {
        return maxReceive > 0 && sideConfigs[face.getValue()].canExtract();
    }

    public long receiveEnergy(BlockFace face, long amount, boolean simulate) {
        if (this.canReceive(face) && amount > 0L) {
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

    public long extractEnergy(BlockFace face, long amount, boolean simulate) {
        if (this.canExtract(face) && amount > 0L) {
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
        return (float) this.energyStored / (float) this.maxEnergy;
    }

    public boolean isFull() {
        return this.energyStored >= this.maxEnergy;
    }

    public boolean isEmpty() {
        return this.energyStored <= 0L;
    }

    @Nullable
    public Component<ChunkStore> clone() {
        return new EnergyContainerComponent(this.energyStored, this.maxEnergy, this.maxReceive,
                this.maxExtract, this.sideConfigs, this.priority);
    }

    public String toString() {
        String sides = Arrays.stream(sideConfigs).map(Enum::name).collect(Collectors.joining(", "));
        return String.format("Energy: %d/%d RF (Prio: %d) | Sides: [%s]",
                energyStored, maxEnergy, priority, sides);
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
                .append(new KeyedCodec<>("SideConfigs", Codec.INT_ARRAY),
                        (c, v) -> c.sideConfigs = Arrays.stream(v)
                                .mapToObj(SideConfig::fromType)
                                .toArray(SideConfig[]::new),
                        (c) -> Arrays.stream(c.sideConfigs)
                                .mapToInt(SideConfig::getType).toArray())
                .addValidator(Validators.intArraySize(7))
                .documentation("Side configuration for Input/Output sides").add()
                .append(new KeyedCodec<>("Priority", Codec.INTEGER),
                        (c, v) -> c.priority = v,
                        (c) -> c.priority)
                .addValidator(Validators.greaterThanOrEqual(0))
                .documentation("Priority for energy transfer, lower means energy is transferred first").add()
                .build();
    }
}
