package com.rasebdon.hytech.energy.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.builder.BuilderField;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.energy.EnergyModule;
import com.rasebdon.hytech.energy.core.BlockFaceConfig;
import com.rasebdon.hytech.energy.core.BlockFaceConfigType;
import com.rasebdon.hytech.energy.core.IEnergyContainer;
import com.rasebdon.hytech.energy.util.EnergyUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

// TODO : Move base container out

public abstract class EnergyContainerComponentBase implements Component<ChunkStore>, IEnergyContainer {
    public static final BuilderCodec<EnergyContainerComponentBase> CODEC =
            BuilderCodec.abstractBuilder(EnergyContainerComponentBase.class)
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
                    .append(new KeyedCodec<>("TransferPriority", Codec.INTEGER),
                            (c, v) -> c.transferPriority = v,
                            (c) -> c.transferPriority)
                    .addValidator(Validators.greaterThanOrEqual(0))
                    .documentation("Priority for energy transfer, lower means energy is extracted first").add()
                    .append(new KeyedCodec<>("BlackFaceConfig", BlockFaceConfigOverride.CODEC),
                            (c, v) -> c.staticBlockFaceConfigOverride = v,
                            (c) -> c.staticBlockFaceConfigOverride)
                    .documentation("Side configuration for Input/Output sides").add()
                    .build();
    protected static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    protected final List<IEnergyContainer> extractTargets = new ArrayList<>();
    protected final BlockFaceConfig currentBlockFaceConfig;
    protected long energy;
    protected long totalCapacity;
    protected long transferSpeed;
    protected int transferPriority;
    protected BlockFaceConfigOverride staticBlockFaceConfigOverride;

    public EnergyContainerComponentBase(
            long energy,
            long totalCapacity,
            long transferSpeed,
            BlockFaceConfig blockFaceConfig,
            int transferPriority
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
        this.currentBlockFaceConfig = blockFaceConfig;
        this.transferPriority = transferPriority;
    }

    public EnergyContainerComponentBase() {
        this(0L, 0L, 0L, new BlockFaceConfig(), 0);
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " must be >= 0");
    }

    public abstract Component<ChunkStore> clone();

    public long getEnergy() {
        return this.energy;
    }

    public long getTotalCapacity() {
        return this.totalCapacity;
    }

    public long getTransferSpeed() {
        return this.transferSpeed;
    }

    public int getTransferPriority() {
        return transferPriority;
    }

    public BlockFaceConfig getCurrentBlockFaceConfig() {
        return this.currentBlockFaceConfig;
    }

    public boolean canReceiveFromFace(BlockFace face) {
        return this.transferSpeed > 0 && this.currentBlockFaceConfig.getFaceConfigType(face).canReceive();
    }

    public boolean canExtractFromFace(BlockFace face) {
        return this.transferSpeed > 0 && this.currentBlockFaceConfig.canExtractFromFace(face);
    }

    @Override
    public long transferEnergyTo(IEnergyContainer other) {
        if (energy <= 0 || transferSpeed <= 0) {
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

    @Override
    public long transferEnergyTo(Collection<? extends IEnergyContainer> targets) {
        if (energy <= 0 || transferSpeed <= 0) return 0;

        var validTargets = targets.stream()
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

    public String toString() {
        var sides = Arrays.stream(this.currentBlockFaceConfig.toArray())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
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
            var neighborContainer = store.getComponent(neighborRef, EnergyModule.get().getSingleBlockEnergyContainerComponentType());

            if (neighborContainer != null && neighborLoc != null) {
                // Get local face of neighbor (hit from opposite world direction)
                var oppositeWorldDir = worldSide.clone().negate();
                var neighborLocalFace = EnergyUtils.getLocalFace(oppositeWorldDir, neighborLoc.rotation());

                if (triggerReloadInTargets) {
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

    public void tryTransferToTargets() {
        if (extractTargets.isEmpty()) return;
        this.transferEnergyTo(extractTargets);
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
            var neighborContainer = store.getComponent(neighborRef, EnergyModule.get().getSingleBlockEnergyContainerComponentType());

            if (neighborContainer != null && neighborLoc != null) {
                LOGGER.atInfo().log("%s removing %s as extract target", toString(), neighborContainer.toString());
                neighborContainer.extractTargets.remove(this);
            }
        }
    }

    /// Override that can be set in json/block data component for statically overriding possible side configs
    public static class BlockFaceConfigOverride {
        public static final BuilderCodec<BlockFaceConfigOverride> CODEC;

        static {
            var builder = BuilderCodec.builder(BlockFaceConfigOverride.class, BlockFaceConfigOverride::new);

            for (BlockFace face : BlockFace.values()) {
                createKeyedConfigCodec(builder, face)
                        .documentation("Defines that this face can be configured for all given states " +
                                "(0 = No I/O, 1 = Only Input/None, 2 = Only Output/None, 3 = I/O or None)")
                        .addValidator(Validators.range(0b00, 0b11))
                        .add();
            }

            CODEC = builder.build();
        }

        private final BlockFaceConfig config;

        public BlockFaceConfigOverride() {
            this.config = new BlockFaceConfig();
        }

        private static BuilderField.FieldBuilder<BlockFaceConfigOverride, Integer, BuilderCodec.Builder<BlockFaceConfigOverride>>
        createKeyedConfigCodec(
                BuilderCodec.Builder<BlockFaceConfigOverride> builder,
                BlockFace face
        ) {
            return builder.append(
                    new KeyedCodec<>(face.name(), Codec.INTEGER),
                    (c, v) -> c.config.setFaceConfigType(face, BlockFaceConfigType.fromBits(v)),
                    (c) -> c.config.getFaceConfigType(face).getBits()
            );
        }

        public BlockFaceConfig getConfig() {
            return config;
        }
    }

}
