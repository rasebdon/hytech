package at.rasebdon.hytech.items.components;

import at.rasebdon.hytech.core.components.LogisticBlockComponent;
import at.rasebdon.hytech.core.components.LogisticComponent;
import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticComponentChangedEvent;
import at.rasebdon.hytech.core.transport.BlockFaceConfig;
import at.rasebdon.hytech.core.transport.BlockFaceConfigState;
import at.rasebdon.hytech.core.util.Validation;
import at.rasebdon.hytech.items.HytechItemContainer;
import at.rasebdon.hytech.items.events.ItemContainerChangedEvent;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.stream.Collectors;

public class ItemBlockComponent extends LogisticBlockComponent<HytechItemContainer> implements HytechItemContainer {

    private static final short DEFAULT_SLOTS = 16;

    public static final BuilderCodec<ItemBlockComponent> CODEC =
            BuilderCodec.builder(ItemBlockComponent.class, ItemBlockComponent::new, LogisticBlockComponent.CODEC)
                    .append(new KeyedCodec<>("ItemContainer", ItemContainer.CODEC),
                            (state, o) -> state.itemContainer = o,
                            (state) -> state.itemContainer).add()
                    // Lets a block size its own container: a burner wants one fuel slot, a buffer
                    // wants a chestful. Applied after the container is decoded, so an existing
                    // world keeps whatever it saved.
                    .append(new KeyedCodec<>("Slots", Codec.SHORT),
                            (c, v) -> c.resize(v),
                            (c) -> (short) c.getSlotCount())
                    .addValidator(Validators.greaterThan((short) 0))
                    .documentation("Number of item slots this block holds").add()
                    .append(new KeyedCodec<>("MaxTransfer", Codec.LONG),
                            (c, v) -> c.transferSpeed = v,
                            (c) -> c.transferSpeed)
                    .addValidator(Validators.greaterThanOrEqual(0L))
                    .documentation("Maximum items transferred per transfer tick").add()
                    .build();

    private ItemContainer itemContainer;
    private long transferSpeed;

    /// Grows or shrinks the container to `slots`, keeping what fits.
    ///
    /// Shrinking drops the overflow rather than ejecting it: this only runs while an asset is being
    /// decoded, so there is no world to eject into yet.
    private void resize(short slots) {
        if (slots <= 0 || this.itemContainer == null) return;
        if (this.itemContainer.getCapacity() == slots) return;

        var resized = SimpleItemContainer.getNewContainer(slots);

        for (short slot = 0; slot < Math.min(slots, this.itemContainer.getCapacity()); slot++) {
            var stack = this.itemContainer.getItemStack(slot);
            if (!ItemStack.isEmpty(stack)) {
                resized.addItemStack(stack, false, false, false);
            }
        }

        this.itemContainer = resized;
    }

    public ItemBlockComponent() {
        this(new BlockFaceConfig(), 0, false, 0L, SimpleItemContainer.getNewContainer(DEFAULT_SLOTS));
    }

    public ItemBlockComponent(
            BlockFaceConfig blockFaceConfig,
            int transferPriority,
            boolean isExtracting,
            long transferSpeed,
            ItemContainer itemContainer) {
        super(blockFaceConfig, transferPriority, isExtracting);

        Validation.requireNonNegative(transferSpeed, "transferSpeed");

        this.transferSpeed = transferSpeed;
        this.itemContainer = itemContainer;
    }

    @Override
    @Nonnull
    public Component<ChunkStore> clone() {
        return new ItemBlockComponent(
                this.blockFaceConfig.clone(),
                this.transferPriority,
                this.isExtracting,
                this.transferSpeed,
                this.itemContainer == null ? null : this.itemContainer.clone());
    }

    @Override
    protected LogisticComponentChangedEvent<HytechItemContainer> createContainerChangedEvent(
            LogisticChangeType type, LogisticComponent<HytechItemContainer> component) {
        return new ItemContainerChangedEvent(type, component);
    }

    @Override
    public HytechItemContainer getContainer() {
        return this;
    }

    @Override
    public boolean isAvailable() {
        return this.itemContainer != null;
    }

    @Override
    public ItemContainer getItemContainer() {
        return itemContainer;
    }

    @Override
    public long getTransferSpeed() {
        return transferSpeed;
    }

    @Override
    public String toString() {
        var sides = Arrays.stream(this.blockFaceConfig.getCurrentStates())
                .map(BlockFaceConfigState::toString)
                .collect(Collectors.joining(", "));
        return String.format("Items: %d in %d/%d slots (Prio: %d) | Sides: [%s]",
                getItemCount(), getUsedSlots(), getSlotCount(), transferPriority, sides);
    }
}
