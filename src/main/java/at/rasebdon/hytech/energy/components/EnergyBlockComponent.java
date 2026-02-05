package at.rasebdon.hytech.energy.components;

import at.rasebdon.hytech.core.components.LogisticBlockComponent;
import at.rasebdon.hytech.core.components.LogisticContainerComponent;
import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticContainerChangedEvent;
import at.rasebdon.hytech.core.transport.BlockFaceConfig;
import at.rasebdon.hytech.core.util.Validation;
import at.rasebdon.hytech.energy.IEnergyContainer;
import at.rasebdon.hytech.energy.events.EnergyContainerChangedEvent;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.codecs.simple.FloatCodec;
import com.hypixel.hytale.codec.schema.SchemaContext;
import com.hypixel.hytale.codec.schema.config.Schema;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.bson.BsonValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

public class EnergyBlockComponent extends LogisticBlockComponent<IEnergyContainer> implements IEnergyContainer {

    public static final EnergyStateCodec ENERGY_STATE_CODEC = new EnergyStateCodec();
    public static final ArrayCodec<EnergyState> ENERGY_STATE_ARRAY_CODEC = new ArrayCodec<>(ENERGY_STATE_CODEC, EnergyState[]::new);
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
                    .append(new KeyedCodec<>("EnergyStates", ENERGY_STATE_ARRAY_CODEC),
                            (c, v) -> c.energyStates = v,
                            (c) -> c.energyStates)
                    .documentation("Block states that are set whenever the given energy percentage is crossed").add()
                    .build();
    protected EnergyState[] energyStates;
    protected long energy;
    protected long totalCapacity;
    protected long transferSpeed;
    public EnergyBlockComponent(
            long energy,
            long totalCapacity,
            long transferSpeed,
            BlockFaceConfig blockFaceConfig,
            int transferPriority,
            boolean isExtracting,
            ArrayList<EnergyState> energyStates
    ) {
        super(blockFaceConfig, transferPriority, isExtracting);

        Validation.requireNonNegative(energy, "energy");
        Validation.requireNonNegative(totalCapacity, "totalCapacity");
        Validation.requireNonNegative(transferSpeed, "transferSpeed");

        this.totalCapacity = totalCapacity;
        this.energy = Math.min(energy, totalCapacity);
        this.transferSpeed = transferSpeed;
        this.energyStates = energyStates;
    }

    public EnergyBlockComponent() {
        this(0L, 0L, 0L, new BlockFaceConfig(), 0, false);
    }

    @Nonnull
    public Component<ChunkStore> clone() {
        return new EnergyBlockComponent(this.energy, this.totalCapacity,
                this.transferSpeed, this.currentBlockFaceConfig.clone(),
                this.transferPriority, this.isExtracting, this.energyStates);
    }

    public record EnergyState(String blockState, float energyThreshold) {
    }

    public static class EnergyStateCodec implements Codec<EnergyState> {
        @Override
        public @Nullable EnergyState decode(BsonValue bsonValue, ExtraInfo info) {
            var doc = bsonValue.asDocument();

            return new EnergyState(
                    doc.getString("BlockState").getValue(),
                    FloatCodec.decodeFloat(doc.get("EnergyThreshold"))
            );
        }

        @Override
        public BsonValue encode(EnergyState var1, ExtraInfo var2) {
            return null;
        }

        @Override
        public @NotNull Schema toSchema(@NotNull SchemaContext var1) {
            return null;
        }
    }

    @Override
    protected LogisticContainerChangedEvent<IEnergyContainer> createContainerChangedEvent(LogisticChangeType type, LogisticContainerComponent<IEnergyContainer> component) {
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
    public IEnergyContainer getContainer() {
        return this;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
