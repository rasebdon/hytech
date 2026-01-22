package com.rasebdon.hytech.energy.container;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.energy.EnergyModule;
import com.rasebdon.hytech.energy.util.EnergyUtils;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

public class EnergyContainerComponent implements Component<ChunkStore>, IEnergyContainer {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int DEFAULT_SIDE_CONFIG = 0b11_11_11_11_11_11_11;

    public static final BuilderCodec<EnergyContainerComponent> CODEC =
            BuilderCodec.builder(EnergyContainerComponent.class, EnergyContainerComponent::new)
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
                    .append(new KeyedCodec<>("SideConfigBitmap", Codec.INTEGER),
                            (c, v) -> c.sideConfigBitmap = v,
                            (c) -> c.sideConfigBitmap)
                    .documentation("Side configuration for Input/Output sides").add()
                    .append(new KeyedCodec<>("TransferPriority", Codec.INTEGER),
                            (c, v) -> c.transferPriority = v,
                            (c) -> c.transferPriority)
                    .addValidator(Validators.greaterThanOrEqual(0))
                    .documentation("Priority for energy transfer, lower means energy is extracted first").add()
                    .append(new KeyedCodec<>("CanReceiveEnergy", Codec.BOOLEAN),
                            (c, v) -> c.canReceiveEnergy = v,
                            (c) -> c.canReceiveEnergy)
                    .documentation("Global override for block so that it can never receive energy externally").add()
                    .append(new KeyedCodec<>("CanExtractEnergy", Codec.BOOLEAN),
                            (c, v) -> c.canExtractEnergy = v,
                            (c) -> c.canExtractEnergy)
                    .documentation("Global override for block so that it can never extract energy externally").add()
                    .build();

    private long energy;
    private long totalCapacity;
    private long transferSpeed;

    private boolean canReceiveEnergy;
    private boolean canExtractEnergy;

    private int sideConfigBitmap;
    private int transferPriority;

    private ArrayList<IEnergyContainer> extractTargets;

    public EnergyContainerComponent(long energy, long totalCapacity, long maxTransfer,
                                    int sideConfigBitmap, int simulationPriority,
                                    boolean canReceiveEnergy, boolean canExtractEnergy) {
        if (simulationPriority < 0) {
            throw new IllegalArgumentException("simulationPriority must be >= 0");
        } else if (totalCapacity < 0L) {
            throw new IllegalArgumentException("totalCapacity must be >= 0");
        } else if (energy < 0L) {
            throw new IllegalArgumentException("energy must be >= 0");
        } else if (maxTransfer < 0L) {
            throw new IllegalArgumentException("maxTransfer must be >= 0");
        }

        this.sideConfigBitmap = sideConfigBitmap;
        this.transferPriority = simulationPriority;
        this.totalCapacity = totalCapacity;
        this.energy = Math.min(energy, this.totalCapacity);
        this.transferSpeed = maxTransfer;
        this.canReceiveEnergy = canReceiveEnergy;
        this.canExtractEnergy = canExtractEnergy;
    }

    public EnergyContainerComponent() {
        this(0L, 0L, 0L, DEFAULT_SIDE_CONFIG, EnergyTransferSimulationPriority.BATTERY,
                false, false);
    }

    // IEnergyContainer Methods

    public long getEnergy() {
        return this.energy;
    }

    public long getTotalCapacity() {
        return this.totalCapacity;
    }

    public boolean canReceiveEnergy() {
        return this.canReceiveEnergy;
    }

    public boolean canExtractEnergy() {
        return this.canExtractEnergy;
    }

    public long getRemainingCapacity() {
        return this.totalCapacity - this.energy;
    }

    public long getTransferSpeed() {
        return this.transferSpeed;
    }

    public int getTransferPriority() {
        return transferPriority;
    }

    public boolean canReceiveFromFace(BlockFace face) {
        return this.canReceiveEnergy && this.transferSpeed > 0 && this.getSideConfig(face).canReceive();
    }

    public boolean canExtractFromFace(BlockFace face) {
        return this.canExtractEnergy && this.transferSpeed > 0 && this.getSideConfig(face).canExtract();
    }

    public void transferEnergyTo(IEnergyContainer other) {
        if (!other.canReceiveEnergy() || !this.canExtractEnergy) return;

        // Calculate energy to transfer (always try to transfer at max speed)
        var maxTransferSpeed = Math.min(this.transferSpeed, other.getTransferSpeed());
        var maxEnergyToTransfer = Math.min(this.energy, other.getRemainingCapacity());
        var energyToTransfer = Math.min(maxEnergyToTransfer, maxTransferSpeed);

        // Transfer
        other.addEnergy(energyToTransfer);
        this.reduceEnergy(energyToTransfer);
    }

    public void transferEnergyTo(Collection<IEnergyContainer> targets) {
        if (!this.canExtractEnergy || targets.isEmpty() || this.energy <= 0) {
            return;
        }

        // Filter containers that can actually receive energy
        var validTargets = targets.stream()
                .filter(t -> t.getRemainingCapacity() > 0 && t.canReceiveEnergy())
                .toList();

        if (validTargets.isEmpty()) {
            return;
        }

        // Total transferable energy this tick (limited by source)
        var targetCount = validTargets.size();
        var totalTransferable = Math.min(this.energy, this.transferSpeed * targetCount);

        var energyPerTarget = totalTransferable / targetCount;

        if (energyPerTarget <= 0) {
            return;
        }

        var transferredTotal = 0L;

        for (var target : validTargets) {
            var maxTransferSpeed = Math.min(this.transferSpeed, target.getTransferSpeed());
            var maxEnergyToTransfer = Math.min(target.getRemainingCapacity(), maxTransferSpeed);

            var energyToTransfer = Math.min(energyPerTarget, maxEnergyToTransfer);
            if (energyToTransfer <= 0) {
                continue;
            }

            target.addEnergy(energyToTransfer);
            transferredTotal += energyToTransfer;
        }

        this.reduceEnergy(transferredTotal);
    }

    public void addEnergy(long amount) {
        if (amount <= 0) return;
        this.energy = Math.min(this.totalCapacity, this.energy + amount);
    }

    public void reduceEnergy(long amount) {
        if (amount <= 0) return;
        this.energy = Math.max(0, this.energy - amount);
    }

    public float getFillRatio() {
        return (float) this.energy / (float) this.totalCapacity;
    }

    public boolean isFull() {
        return this.energy >= this.totalCapacity;
    }

    public boolean isEmpty() {
        return this.energy == 0L;
    }

    private static int shiftForSide(BlockFace face) {
        return face.getValue() * 2;
    }

    public SideConfig getSideConfig(BlockFace face) {
        int shift = shiftForSide(face);
        int bits = (sideConfigBitmap >> shift) & 0b11;
        return SideConfig.fromBits(bits);
    }

    public SideConfig[] getSideConfigsAsArray() {
        SideConfig[] result = new SideConfig[7];

        for (int i = 0; i < 7; i++) {
            int bits = (sideConfigBitmap >> (i * 2)) & 0b11;
            result[i] = SideConfig.fromBits(bits);
        }

        return result;
    }

    public void setSideConfig(BlockFace face, SideConfig config) {
        int shift = shiftForSide(face);
        sideConfigBitmap =
                (sideConfigBitmap & ~(0b11 << shift))
                        | (config.getBits() << shift);
    }

    public void cycleSideConfig(BlockFace face) {
        setSideConfig(face, getSideConfig(face).next());
    }

    @Nonnull
    @Override
    public Component<ChunkStore> clone() {
        return new EnergyContainerComponent(this.energy, this.totalCapacity,
                this.transferSpeed, this.sideConfigBitmap, this.transferPriority,
                this.canReceiveEnergy, this.canExtractEnergy);
    }

    public String toString() {
        var sideConfigs = getSideConfigsAsArray();
        var sides = Arrays.stream(sideConfigs).map(Enum::name).collect(Collectors.joining(", "));
        return String.format("Energy: %d/%d RF (Prio: %d) | Sides: [%s]",
                energy, totalCapacity, transferPriority, sides);
    }

    // TODO : Will need refactoring with cable networks
    public void reloadTransferTargets(Ref<ChunkStore> blockRef, Store<ChunkStore> store, boolean triggerReloadInTargets) {
        this.extractTargets = new ArrayList<>();

        var world = store.getExternalData().getWorld();
        var blockLocation = EnergyUtils.getBlockTransform(blockRef, store);
        if (blockLocation == null) return;

        for (var worldSide : Vector3i.BLOCK_SIDES) {
            // Get local face of current block
            var localFace = EnergyUtils.getLocalFace(worldSide, blockLocation.rotation());

            // Ensure this face is allowed to extract (Logic inside your component)
            if (!canExtractFromFace(localFace) && !canReceiveFromFace(localFace)) continue;

            var neighborWorldPos = worldSide.clone().add(blockLocation.worldPos());
            var neighborRef = EnergyUtils.getBlockEntityRef(world, neighborWorldPos);
            if (neighborRef == null) continue;

            var neighborLoc = EnergyUtils.getBlockTransform(neighborRef, store);
            var neighborContainer = store.getComponent(neighborRef, EnergyModule.get().getEnergyContainerComponentType());

            if (neighborContainer != null && neighborLoc != null) {
                // Get local face of neighbor (hit from opposite world direction)
                var oppositeWorldDir = worldSide.clone().negate();
                var neighborLocalFace = EnergyUtils.getLocalFace(oppositeWorldDir, neighborLoc.rotation());

                if (canReceiveEnergy && triggerReloadInTargets) {
                    neighborContainer.reloadTransferTargets(neighborRef, store, false);
                }

                // If neighbor can receive -> Add to extract targets
                if (this.canExtractFromFace(localFace) && neighborContainer.canReceiveFromFace(neighborLocalFace)) {
                    LOGGER.atInfo().log("%s adding %s as extract target", toString(), neighborContainer.toString());

                    this.extractTargets.add(neighborContainer);
                }
            }
        }
    }

    public void removeAsTransferTargetFromNeighbors(@NotNull Ref<ChunkStore> blockRef, @NotNull Store<ChunkStore> store) {
        var world = store.getExternalData().getWorld();
        var blockLocation = EnergyUtils.getBlockTransform(blockRef, store);
        if (blockLocation == null) return;

        for (var worldSide : Vector3i.BLOCK_SIDES) {
            var neighborWorldPos = worldSide.clone().add(blockLocation.worldPos());
            var neighborRef = EnergyUtils.getBlockEntityRef(world, neighborWorldPos);
            if (neighborRef == null) continue;

            var neighborLoc = EnergyUtils.getBlockTransform(neighborRef, store);
            var neighborContainer = store.getComponent(neighborRef, EnergyModule.get().getEnergyContainerComponentType());

            if (neighborContainer != null && neighborLoc != null) {
                LOGGER.atInfo().log("%s removing %s as extract target", toString(), neighborContainer.toString());
                neighborContainer.extractTargets.remove(this);
            }
        }
    }

    public void tryTransferToTargets() {
        if (extractTargets.isEmpty()) return;
        this.transferEnergyTo(extractTargets);
    }
}
