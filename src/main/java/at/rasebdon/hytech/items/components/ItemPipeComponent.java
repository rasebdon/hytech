package at.rasebdon.hytech.items.components;

import at.rasebdon.hytech.core.components.LogisticComponent;
import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticComponentChangedEvent;
import at.rasebdon.hytech.core.transport.BlockFaceConfig;
import at.rasebdon.hytech.core.transport.BlockFaceConfigType;
import at.rasebdon.hytech.core.util.Validation;
import at.rasebdon.hytech.items.HytechItemContainer;
import at.rasebdon.hytech.items.events.ItemContainerChangedEvent;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

/// An item pipe segment.
///
/// Unlike the energy side -- where the network pools a scalar and a save system pushes it
/// back onto the pipes -- each pipe owns its buffer container outright. That container is
/// part of this component's codec, so items in transit survive a chunk unload with no
/// separate save system and no rounding loss.
public class ItemPipeComponent extends LogisticPipeComponent<HytechItemContainer> implements HytechItemContainer {

    private static final short DEFAULT_SLOTS = 4;
    public static final BuilderCodec<ItemPipeComponent> CODEC =
            BuilderCodec.builder(ItemPipeComponent.class, ItemPipeComponent::new, LogisticPipeComponent.CODEC)
                    .append(new KeyedCodec<>("ItemContainer", ItemContainer.CODEC),
                            (c, v) -> c.itemContainer = v,
                            (c) -> c.itemContainer)
                    .documentation("Items currently held by this pipe segment").add()
                    .append(new KeyedCodec<>("PipeCapacity", Codec.INTEGER),
                            (c, v) -> c.pipeCapacity = v,
                            (c) -> c.pipeCapacity)
                    .addValidator(Validators.greaterThanOrEqual(0))
                    .documentation("Buffer slots per pipe segment").add()
                    .append(new KeyedCodec<>("PipeTransferSpeed", Codec.LONG),
                            (c, v) -> c.pipeTransferSpeed = v,
                            (c) -> c.pipeTransferSpeed)
                    .addValidator(Validators.greaterThanOrEqual(0L))
                    .documentation("Maximum items transferred per transfer tick").add()
                    .build();
    /// Item pipes use a chunkier hub than the default geometry; keep in step with the
    /// "Items" entry in scripts/generate-pipe-assets.py.
    private static final int HUB_UNITS = 12;
    private ItemContainer itemContainer;
    private int pipeCapacity;
    private long pipeTransferSpeed;

    public ItemPipeComponent() {
        this(new BlockFaceConfig(), LogisticPipeComponent.DEFAULT_CONNECTION_MODEL_ASSETS,
                DEFAULT_SLOTS, 0L, SimpleItemContainer.getNewContainer(DEFAULT_SLOTS));
    }

    public ItemPipeComponent(
            BlockFaceConfig blockFaceConfig,
            Map<BlockFaceConfigType, String> connectionModelAssetNames,
            int pipeCapacity,
            long pipeTransferSpeed,
            ItemContainer itemContainer) {
        super(blockFaceConfig, connectionModelAssetNames);

        Validation.requireNonNegative(pipeCapacity, "pipeCapacity");
        Validation.requireNonNegative(pipeTransferSpeed, "pipeTransferSpeed");

        this.pipeCapacity = pipeCapacity;
        this.pipeTransferSpeed = pipeTransferSpeed;
        this.itemContainer = itemContainer;
    }

    @Override
    @Nonnull
    public Component<ChunkStore> clone() {
        return new ItemPipeComponent(
                this.blockFaceConfig.clone(),
                this.connectionModelAssetNames,
                this.pipeCapacity,
                this.pipeTransferSpeed,
                this.itemContainer == null ? null : this.itemContainer.clone());
    }

    @Override
    protected LogisticComponentChangedEvent<HytechItemContainer> createContainerChangedEvent(
            LogisticChangeType type, LogisticComponent<HytechItemContainer> component) {
        return new ItemContainerChangedEvent(type, component);
    }

    /// Routing goes through the network, so callers see the whole run's buffer rather
    /// than this one segment. Falls back to the segment while unassigned.
    @Override
    public HytechItemContainer getContainer() {
        return this.network instanceof HytechItemContainer networkContainer ? networkContainer : this;
    }

    @Override
    public boolean isAvailable() {
        return this.network != null && this.itemContainer != null;
    }

    @Override
    @Nullable
    public ItemContainer getItemContainer() {
        return this.itemContainer;
    }

    @Override
    public int getHubSize() {
        return HUB_UNITS;
    }

    @Override
    public long getTransferSpeed() {
        return pipeTransferSpeed;
    }
}
