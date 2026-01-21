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
import java.util.stream.Collectors;

public class EnergyContainerComponent implements Component<ChunkStore>, IEnergyContainer {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static final BuilderCodec<EnergyContainerComponent> CODEC =
            BuilderCodec.builder(EnergyContainerComponent.class, EnergyContainerComponent::new)
                    .append(new KeyedCodec<>("EnergyStored", Codec.LONG),
                            (c, v) -> c.energy = v,
                            (c) -> c.energy)
                    .addValidator(Validators.greaterThanOrEqual(0L))
                    .documentation("Currently stored energy")
                    .add()
                    .append(new KeyedCodec<>("MaxEnergy", Codec.LONG),
                            (c, v) -> c.totalCapacity = v,
                            (c) -> c.totalCapacity)
                    .addValidator(Validators.greaterThan(0L))
                    .documentation("Maximum energy capacity").add()
                    .append(new KeyedCodec<>("MaxTransfer", Codec.LONG),
                            (c, v) -> c.transferSpeed = v,
                            (c) -> c.transferSpeed)
                    .addValidator(Validators.greaterThanOrEqual(0L))
                    .documentation("Maximum energy transferred per tick").add()
                    .append(new KeyedCodec<>("SideConfigs", Codec.INT_ARRAY),
                            EnergyContainerComponent::setSideConfigs,
                            EnergyContainerComponent::getSideConfigsAsIntArray)
                    .addValidator(Validators.intArraySize(7))
                    .documentation("Side configuration for Input/Output sides").add()
                    .append(new KeyedCodec<>("TransferPriority", Codec.INTEGER),
                            (c, v) -> c.transferPriority = v,
                            (c) -> c.transferPriority)
                    .addValidator(Validators.greaterThanOrEqual(0))
                    .documentation("Priority for energy transfer, lower means energy is extracted first").add()
                    .build();
    private long energy;
    private long totalCapacity;
    private long transferSpeed;

    private SideConfig[] sideConfigs;
    private int transferPriority;

    private boolean isMultiblockStorage;
    private ArrayList<IEnergyContainer> extractTargets;

    public EnergyContainerComponent(long energy, long totalCapacity, long maxTransfer,
                                    SideConfig[] sideConfigs, int simulationPriority) {
        if (sideConfigs.length != 7) {
            throw new IllegalArgumentException("sideConfigs must have length of 7");
        } else if (simulationPriority < 0) {
            throw new IllegalArgumentException("simulationPriority must be >= 0");
        } else if (totalCapacity < 0L) {
            throw new IllegalArgumentException("totalCapacity must be >= 0");
        } else if (energy < 0L) {
            throw new IllegalArgumentException("energy must be >= 0");
        } else if (maxTransfer < 0L) {
            throw new IllegalArgumentException("maxTransfer must be >= 0");
        }

        this.sideConfigs = sideConfigs;
        this.transferPriority = simulationPriority;
        this.totalCapacity = totalCapacity;
        this.energy = Math.min(energy, this.totalCapacity);
        this.transferSpeed = maxTransfer;
    }

    // IEnergyContainer Methods

    public EnergyContainerComponent() {
        this(0L, 0L, 0L, SideConfig.getDefault(), EnergyTransferSimulationPriority.CABLE);
    }

    public long getEnergy() {
        return this.energy;
    }

    public long getTotalCapacity() {
        return this.totalCapacity;
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
        return transferSpeed > 0 && getSideConfig(face).canReceive();
    }

    public boolean canTransferFromFace(BlockFace face) {
        return transferSpeed > 0 && getSideConfig(face).canExtract();
    }

    public void transferEnergyTo(IEnergyContainer other) {
        // Calculate energy to transfer (always try to transfer at max speed)
        var maxTransferSpeed = Math.min(transferSpeed, other.getTransferSpeed());
        var maxEnergyToTransfer = Math.min(this.energy, other.getRemainingCapacity());
        var energyToTransfer = Math.min(maxEnergyToTransfer, maxTransferSpeed);

        // Transfer
        other.addEnergy(energyToTransfer);
        this.reduceEnergy(energyToTransfer);
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

    public void cycleSideConfig(BlockFace face) {
        var index = face.getValue();
        var oldValue = getSideConfig(face);
        this.sideConfigs[index] = oldValue.next();
    }

    // Side Config Methods
    public SideConfig getSideConfig(BlockFace face) {
        return sideConfigs[face.getValue()];
    }

    @Nonnull
    @Override
    public Component<ChunkStore> clone() {
        return new EnergyContainerComponent(this.energy, this.totalCapacity,
                this.transferSpeed, this.sideConfigs.clone(), this.transferPriority);
    }

    public String toString() {
        var sides = Arrays.stream(this.sideConfigs).map(Enum::name).collect(Collectors.joining(", "));
        return String.format("Energy: %d/%d RF (Prio: %d) | Sides: [%s]",
                energy, totalCapacity, transferPriority, sides);
    }

    private void setSideConfigs(int[] v) {
        this.sideConfigs = Arrays.stream(v).mapToObj(SideConfig::fromType).toArray(SideConfig[]::new);
    }

    private int[] getSideConfigsAsIntArray() {
        return Arrays.stream(this.sideConfigs).mapToInt(SideConfig::getType).toArray();
    }

    public void reloadTransferTargets(Ref<ChunkStore> blockRef, Store<ChunkStore> store, boolean triggerReloadInTargets) {
        this.extractTargets = new ArrayList<>();

        var world = store.getExternalData().getWorld();
        var blockLocation = EnergyUtils.getBlockTransform(blockRef, store);
        if (blockLocation == null) return;

        for (var worldSide : Vector3i.BLOCK_SIDES) {
            // Get local face of current block
            var localFace = EnergyUtils.getLocalFace(worldSide, blockLocation.rotation());

            // Ensure this face is allowed to extract (Logic inside your component)
            if (!canTransferFromFace(localFace) && !canReceiveFromFace(localFace)) continue;

            var neighborWorldPos = worldSide.clone().add(blockLocation.worldPos());
            var neighborRef = EnergyUtils.getBlockEntityRef(world, neighborWorldPos);
            if (neighborRef == null) continue;

            var neighborLoc = EnergyUtils.getBlockTransform(neighborRef, store);
            var neighborContainer = store.getComponent(neighborRef, EnergyModule.get().getEnergyContainerComponentType());

            if (neighborContainer != null && neighborLoc != null) {
                // Get local face of neighbor (hit from opposite world direction)
                var oppositeWorldDir = worldSide.clone().negate();
                var neighborLocalFace = EnergyUtils.getLocalFace(oppositeWorldDir, neighborLoc.rotation());

                if (triggerReloadInTargets) {
                    neighborContainer.reloadTransferTargets(neighborRef, store, false);
                }

                // If neighbor can receive -> Add to extract targets
                if (canTransferFromFace(localFace) && neighborContainer.canReceiveFromFace(neighborLocalFace)) {
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

        for (var targetContainer : extractTargets) {
            if (targetContainer == null || targetContainer.isFull()) {
                continue;
            }

            this.transferEnergyTo(targetContainer);
        }
    }
}
