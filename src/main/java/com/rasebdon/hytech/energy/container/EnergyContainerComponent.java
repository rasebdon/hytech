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
import java.util.List;
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

    private static final int SIDES = 7;
    private static final int BITS_PER_SIDE = 2;
    private static final int SIDE_MASK = 0b11;

    private final List<IEnergyContainer> extractTargets = new ArrayList<>();

    private long energy;
    private long totalCapacity;
    private long transferSpeed;

    private boolean canReceiveEnergy;
    private boolean canExtractEnergy;

    private int sideConfigBitmap;
    private int transferPriority;

    public EnergyContainerComponent(
            long energy,
            long totalCapacity,
            long transferSpeed,
            int sideConfigBitmap,
            int transferPriority,
            boolean canReceiveEnergy,
            boolean canExtractEnergy
    ) {
        requireNonNegative(energy, "energy");
        requireNonNegative(totalCapacity, "totalCapacity");
        requireNonNegative(transferSpeed, "transferSpeed");

        if (transferPriority < 0) {
            throw new IllegalArgumentException("transferPriority must be >= 0");
        }

        this.totalCapacity = totalCapacity;
        this.energy = Math.min(energy, totalCapacity);
        this.transferSpeed = transferSpeed;
        this.sideConfigBitmap = sideConfigBitmap;
        this.transferPriority = transferPriority;
        this.canReceiveEnergy = canReceiveEnergy;
        this.canExtractEnergy = canExtractEnergy;
    }

    public EnergyContainerComponent() {
        this(0L, 0L, 0L, DEFAULT_SIDE_CONFIG, EnergyTransferSimulationPriority.BATTERY,
                false, false);
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

    public boolean canReceiveEnergy() {
        return this.canReceiveEnergy;
    }

    public boolean canExtractEnergy() {
        return this.canExtractEnergy;
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

    private static int sideShift(BlockFace face) {
        return face.getValue() * BITS_PER_SIDE;
    }

    @Override
    public long transferEnergyTo(IEnergyContainer other) {
        if (!canExtractEnergy || !other.canReceiveEnergy() ||
                energy <= 0 || transferSpeed <= 0) {
            return 0;
        }

        long transferable = Math.min(
                Math.min(transferSpeed, other.getTransferSpeed()),
                Math.min(energy, other.getRemainingCapacity())
        );

        if (transferable <= 0) return 0;

        other.addEnergy(transferable);
        reduceEnergy(transferable);
        return transferable;
    }

    public void addEnergy(long amount) {
        if (amount <= 0) return;
        this.energy = Math.min(this.totalCapacity, this.energy + amount);
    }

    public void reduceEnergy(long amount) {
        if (amount <= 0) return;
        this.energy = Math.max(0, this.energy - amount);
    }

    public SideConfig[] getSideConfigsAsArray() {
        SideConfig[] result = new SideConfig[7];

        for (int i = 0; i < 7; i++) {
            int bits = (sideConfigBitmap >> (i * 2)) & 0b11;
            result[i] = SideConfig.fromBits(bits);
        }

        return result;
    }

    @Override
    public long transferEnergyTo(Collection<? extends IEnergyContainer> targets) {
        if (!canExtractEnergy || energy <= 0 || transferSpeed <= 0) return 0;

        var validTargets = targets.stream()
                .filter(IEnergyContainer::canReceiveEnergy)
                .filter(t -> t.getRemainingCapacity() > 0)
                .toList();

        if (validTargets.isEmpty()) return 0;

        long totalTransferred = 0;
        long maxPerTarget = transferSpeed;

        for (var target : validTargets) {
            if (energy <= 0) break;

            long transferable = Math.min(
                    Math.min(maxPerTarget, target.getTransferSpeed()),
                    Math.min(energy, target.getRemainingCapacity())
            );

            if (transferable > 0) {
                target.addEnergy(transferable);
                energy -= transferable;
                totalTransferred += transferable;
            }
        }

        return totalTransferred;
    }

    public SideConfig getSideConfig(BlockFace face) {
        int shift = sideShift(face);
        return SideConfig.fromBits((sideConfigBitmap >> shift) & SIDE_MASK);
    }

    public void setSideConfig(BlockFace face, SideConfig config) {
        int shift = sideShift(face);
        sideConfigBitmap =
                (sideConfigBitmap & ~(SIDE_MASK << shift))
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
        this.extractTargets.clear();

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
