package at.rasebdon.hytech.energy.components;

import at.rasebdon.hytech.core.components.LogisticBlockComponent;
import at.rasebdon.hytech.core.components.LogisticComponent;
import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticContainerChangedEvent;
import at.rasebdon.hytech.core.transport.BlockFaceConfig;
import at.rasebdon.hytech.core.transport.BlockFaceConfigState;
import at.rasebdon.hytech.core.util.Validation;
import at.rasebdon.hytech.energy.HytechEnergyContainer;
import at.rasebdon.hytech.energy.events.EnergyContainerChangedEvent;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class EnergyBlockComponent extends LogisticBlockComponent<HytechEnergyContainer> implements HytechEnergyContainer {

    public static final MapCodec<Integer, Map<String, Integer>> INTEGER_MAP_CODEC = new MapCodec<>(Codec.INTEGER, HashMap::new);

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
                    .append(new KeyedCodec<>("EnergyLevelBlockStates", INTEGER_MAP_CODEC),
                            (c, v) -> c.energyLevelStates = v,
                            (c) -> c.energyLevelStates)
                    .documentation("Block states that are set whenever the given energy percentage is crossed").add()
                    .build();

    protected Map<String, Integer> energyLevelStates;
    protected long energy;
    protected long totalCapacity;
    protected long transferSpeed;

    private long lastTickEnergy;

    public EnergyBlockComponent(
            long energy,
            long totalCapacity,
            long transferSpeed,
            BlockFaceConfig blockFaceConfig,
            int transferPriority,
            boolean isExtracting,
            Map<String, Integer> energyLevelStates
    ) {
        super(blockFaceConfig, transferPriority, isExtracting);

        Validation.requireNonNegative(energy, "energy");
        Validation.requireNonNegative(totalCapacity, "totalCapacity");
        Validation.requireNonNegative(transferSpeed, "transferSpeed");

        this.totalCapacity = totalCapacity;
        this.energy = Math.min(energy, totalCapacity);
        this.lastTickEnergy = this.energy;
        this.transferSpeed = transferSpeed;
        this.energyLevelStates = energyLevelStates;
    }

    public EnergyBlockComponent() {
        this(0L, 0L, 0L, new BlockFaceConfig(),
                0, false, new HashMap<>());
    }

    @Nonnull
    public Component<ChunkStore> clone() {
        return new EnergyBlockComponent(this.energy, this.totalCapacity,
                this.transferSpeed, this.blockFaceConfig.clone(),
                this.transferPriority, this.isExtracting, this.energyLevelStates);
    }

    @Override
    protected LogisticContainerChangedEvent<HytechEnergyContainer> createContainerChangedEvent(LogisticChangeType type, LogisticComponent<HytechEnergyContainer> component) {
        return new EnergyContainerChangedEvent(type, component);
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

    @Override
    public long getEnergyDelta() {
        return this.energy - this.lastTickEnergy;
    }

    public void addEnergy(long amount) {
        if (amount <= 0) return;

        this.lastTickEnergy = energy;
        this.energy = Math.min(this.totalCapacity, this.energy + amount);
    }

    public void reduceEnergy(long amount) {
        if (amount <= 0) return;

        this.lastTickEnergy = energy;
        this.energy = Math.max(0, this.energy - amount);
    }

    @Override
    public void updateEnergyDelta() {
        this.lastTickEnergy = this.energy;
    }

    public String toString() {
        var sides = Arrays.stream(this.blockFaceConfig.getCurrentStates())
                .map(BlockFaceConfigState::toString)
                .collect(Collectors.joining(", "));
        return String.format("Energy: %d/%d RF (Prio: %d) | Sides: [%s]",
                energy, totalCapacity, transferPriority, sides);
    }

    @Nullable
    public String getEnergyLevelBlockState() {
        int energyPercent = (int) (this.getContainer().getFillRatio() * 100);

        String bestKey = null;
        int bestValue = Integer.MIN_VALUE;

        for (var entry : energyLevelStates.entrySet()) {
            int value = entry.getValue();

            if (value <= energyPercent && value > bestValue) {
                bestValue = value;
                bestKey = entry.getKey();
            }
        }

        return bestKey;
    }

    @Override
    public HytechEnergyContainer getContainer() {
        return this;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
