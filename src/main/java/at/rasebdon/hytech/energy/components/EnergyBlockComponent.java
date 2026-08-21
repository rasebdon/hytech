package at.rasebdon.hytech.energy.components;

import at.rasebdon.hytech.core.components.AbstractScalarBlockComponent;
import at.rasebdon.hytech.core.components.LogisticComponent;
import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticComponentChangedEvent;
import at.rasebdon.hytech.core.transport.BlockFaceConfig;
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
import java.util.HashMap;
import java.util.Map;

/// An energy store: battery, generator buffer, machine buffer.
///
/// Adds only the charge-level block states to [AbstractScalarBlockComponent]; the amount,
/// capacity and transfer bookkeeping is shared with every other scalar resource.
public class EnergyBlockComponent extends AbstractScalarBlockComponent<HytechEnergyContainer>
        implements HytechEnergyContainer {

    private static final MapCodec<Integer, Map<String, Integer>> INTEGER_MAP_CODEC =
            new MapCodec<>(Codec.INTEGER, HashMap::new);

    @Nonnull
    public static final BuilderCodec<EnergyBlockComponent> CODEC =
            BuilderCodec.builder(EnergyBlockComponent.class, EnergyBlockComponent::new,
                            AbstractScalarBlockComponent.CODEC)
                    // "Energy" rather than the generic "Amount": shipped assets and existing
                    // worlds already use this key, and renaming it would zero every battery.
                    .append(new KeyedCodec<>("Energy", Codec.LONG),
                            (c, v) -> c.amount = v,
                            (c) -> c.amount)
                    .addValidator(Validators.greaterThanOrEqual(0L))
                    .documentation("Currently stored energy")
                    .add()
                    .append(new KeyedCodec<>("EnergyLevelBlockStates", INTEGER_MAP_CODEC),
                            (c, v) -> c.energyLevelStates = v,
                            (c) -> c.energyLevelStates)
                    .documentation("Block states that are set whenever the given energy percentage is crossed")
                    .add()
                    .build();

    protected Map<String, Integer> energyLevelStates;

    public EnergyBlockComponent() {
        this(new BlockFaceConfig(), 0, false, 0L, 0L, 0L, new HashMap<>());
    }

    public EnergyBlockComponent(
            BlockFaceConfig blockFaceConfig,
            int transferPriority,
            boolean isExtracting,
            long energy,
            long totalCapacity,
            long transferSpeed,
            Map<String, Integer> energyLevelStates
    ) {
        super(blockFaceConfig, transferPriority, isExtracting, energy, totalCapacity, transferSpeed);
        this.energyLevelStates = energyLevelStates;
    }

    @Override
    @Nonnull
    public Component<ChunkStore> clone() {
        return new EnergyBlockComponent(this.blockFaceConfig.clone(), this.transferPriority,
                this.isExtracting, this.amount, this.totalCapacity, this.transferSpeed,
                this.energyLevelStates);
    }

    @Override
    protected LogisticComponentChangedEvent<HytechEnergyContainer> createContainerChangedEvent(
            LogisticChangeType type, LogisticComponent<HytechEnergyContainer> component) {
        return new EnergyContainerChangedEvent(type, component);
    }

    @Override
    public HytechEnergyContainer getContainer() {
        return this;
    }

    /// The highest declared charge-level state at or below the current fill, or null when the
    /// block declares none.
    @Nullable
    public String getEnergyLevelBlockState() {
        int percent = (int) (getFillRatio() * 100);

        String bestKey = null;
        int bestValue = Integer.MIN_VALUE;

        for (var entry : this.energyLevelStates.entrySet()) {
            int value = entry.getValue();

            if (value <= percent && value > bestValue) {
                bestValue = value;
                bestKey = entry.getKey();
            }
        }

        return bestKey;
    }

    @Override
    public String toString() {
        return String.format("Energy: %d/%d RF (Prio: %d) | Sides: [%s]",
                this.amount, this.totalCapacity, this.transferPriority, describeFaces());
    }
}
