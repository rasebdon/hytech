package com.rasebdon.hytech.energy.container;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.stream.Collectors;

public class EnergyContainerComponent implements Component<ChunkStore> {
    private long energyStored;
    private long maxEnergy;
    private long maxReceive;
    private long maxExtract;

    // Priority: 0 = Cable, 25 = Producer, 50 = Storage, 75 = Consumer
    private int priority = 2;

    private SideConfig[] sideConfigs;
    private boolean isMultiblockStorage;

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
        }

        this.sideConfigs = sideConfigs;
        this.priority = priority;
        this.energyStored = Math.min(energyStored, maxEnergy);
        this.maxEnergy = maxEnergy;
        this.maxReceive = Math.max(0L, maxReceive);
        this.maxExtract = Math.max(0L, maxExtract);
    }

    public EnergyContainerComponent() {
        this(0L, 10000L, 1000L, 1000L, getDefaultSideConfig(), 50);
    }

    private static SideConfig[] getDefaultSideConfig() {
        return new SideConfig[]
                {
                        SideConfig.BOTH,
                        SideConfig.BOTH,
                        SideConfig.BOTH,
                        SideConfig.BOTH,
                        SideConfig.BOTH,
                        SideConfig.BOTH,
                        SideConfig.BOTH,
                };
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

    public SideConfig getSideConfig(BlockFace face) {
        return sideConfigs[face.getValue()];
    }

    public void cycleSideConfig(BlockFace face) {
        var index = face.getValue();
        var oldValue = getSideConfig(face);
        this.sideConfigs[index] = oldValue.next();
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean canReceive(BlockFace face) {
        return maxExtract > 0 && getSideConfig(face).canReceive();
    }

    public boolean canExtract(BlockFace face) {
        return maxReceive > 0 && getSideConfig(face).canExtract();
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

    @Nonnull
    @Override
    public Component<ChunkStore> clone() {
        return new EnergyContainerComponent(this.energyStored, this.maxEnergy, this.maxReceive,
                this.maxExtract, this.sideConfigs.clone(), this.priority);
    }

    public String toString() {
        var sides = Arrays.stream(this.sideConfigs).map(Enum::name).collect(Collectors.joining(", "));
        return String.format("Energy: %d/%d RF (Prio: %d) | Sides: [%s]",
                energyStored, maxEnergy, priority, sides);
    }

    public static final BuilderCodec<EnergyContainerComponent> CODEC =
            BuilderCodec.builder(EnergyContainerComponent.class, EnergyContainerComponent::new)
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
                            EnergyContainerComponent::setSideConfigs,
                            EnergyContainerComponent::getSideConfigsAsIntArray)
                    .addValidator(Validators.intArraySize(7))
                    .documentation("Side configuration for Input/Output sides").add()
                    .append(new KeyedCodec<>("Priority", Codec.INTEGER),
                            (c, v) -> c.priority = v,
                            (c) -> c.priority)
                    .addValidator(Validators.greaterThanOrEqual(0))
                    .documentation("Priority for energy transfer, lower means energy is transferred first").add()
                    .build();

    private void setSideConfigs(int[] v) {
        this.sideConfigs = Arrays.stream(v).mapToObj(SideConfig::fromType).toArray(SideConfig[]::new);
    }

    private int[] getSideConfigsAsIntArray() {
        return Arrays.stream(this.sideConfigs).mapToInt(SideConfig::getType).toArray();
    }
}
