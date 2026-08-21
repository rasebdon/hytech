package at.rasebdon.hytech.heat.components;

import at.rasebdon.hytech.core.components.AbstractScalarBlockComponent;
import at.rasebdon.hytech.core.components.LogisticComponent;
import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticComponentChangedEvent;
import at.rasebdon.hytech.core.transport.BlockFaceConfig;
import at.rasebdon.hytech.heat.HytechHeatContainer;
import at.rasebdon.hytech.heat.events.HeatContainerChangedEvent;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;

/// A heat sink or buffer. All the behaviour is in [AbstractScalarBlockComponent].
public class HeatBlockComponent extends AbstractScalarBlockComponent<HytechHeatContainer>
        implements HytechHeatContainer {

    @Nonnull
    public static final BuilderCodec<HeatBlockComponent> CODEC =
            BuilderCodec.builder(HeatBlockComponent.class, HeatBlockComponent::new,
                            AbstractScalarBlockComponent.CODEC)
                    // New resource types use the generic key. Only energy carries a
                    // resource-specific one, for backwards compatibility with shipped assets.
                    .append(new KeyedCodec<>("Amount", Codec.LONG),
                            (c, v) -> c.amount = v,
                            (c) -> c.amount)
                    .addValidator(Validators.greaterThanOrEqual(0L))
                    .documentation("Currently stored heat").add()
                    .build();

    public HeatBlockComponent() {
        this(new BlockFaceConfig(), 0, false, 0L, 0L, 0L);
    }

    public HeatBlockComponent(
            BlockFaceConfig blockFaceConfig,
            int transferPriority,
            boolean isExtracting,
            long amount,
            long totalCapacity,
            long transferSpeed) {
        super(blockFaceConfig, transferPriority, isExtracting, amount, totalCapacity, transferSpeed);
    }

    @Override
    @Nonnull
    public Component<ChunkStore> clone() {
        return new HeatBlockComponent(this.blockFaceConfig.clone(), this.transferPriority,
                this.isExtracting, this.amount, this.totalCapacity, this.transferSpeed);
    }

    @Override
    protected LogisticComponentChangedEvent<HytechHeatContainer> createContainerChangedEvent(
            LogisticChangeType type, LogisticComponent<HytechHeatContainer> component) {
        return new HeatContainerChangedEvent(type, component);
    }

    @Override
    public HytechHeatContainer getContainer() {
        return this;
    }

    @Override
    public String toString() {
        return String.format("Heat: %d/%d HU (Prio: %d) | Sides: [%s]",
                this.amount, this.totalCapacity, this.transferPriority, describeFaces());
    }
}
