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
                    // wants a chestful. Enforced on read by ensureDeclaredCapacity rather than here,
                    // so a block saved before this key existed is corrected when it loads.
                    .append(new KeyedCodec<>("Slots", Codec.SHORT),
                            (c, v) -> c.declaredSlots = v,
                            (c) -> c.declaredSlots)
                    // Zero, not one, is the floor: it is the documented "leave the container
                    // alone" value and it is also what the field defaults to. BuilderCodec
                    // validates a field's *default* at registration, so a stricter bound here
                    // fails the whole component and takes the plugin down with it.
                    .addValidator(Validators.greaterThanOrEqual((short) 0))
                    .documentation("Number of item slots this block holds, or 0 to keep the current size").add()
                    .append(new KeyedCodec<>("MaxTransfer", Codec.LONG),
                            (c, v) -> c.transferSpeed = v,
                            (c) -> c.transferSpeed)
                    .addValidator(Validators.greaterThanOrEqual(0L))
                    .documentation("Maximum items transferred per transfer tick").add()
                    .build();

    private ItemContainer itemContainer;

    /// Slot count the asset asks for, or 0 to keep whatever the container already has.
    private short declaredSlots;
    private long transferSpeed;

    /// Brings the container to the size the asset declares, keeping what fits.
    ///
    /// Enforced on read rather than only at decode, because the two do not coincide. A block placed
    /// before `Slots` existed has a saved sixteen-slot container and no `Slots` key of its own, so a
    /// decode-time resize would never run for it and the burner would keep showing sixteen slots.
    /// Vanilla does the same thing for `ItemContainerBlock`, resizing to the asset capacity when the
    /// block loads.
    ///
    /// Idempotent and a single comparison in the common case, so calling it from the accessor costs
    /// nothing once the sizes agree.
    private void ensureDeclaredCapacity() {
        short slots = this.declaredSlots;

        if (slots <= 0 || this.itemContainer == null) return;
        if (this.itemContainer.getCapacity() == slots) return;

        var resized = SimpleItemContainer.getNewContainer(slots);

        for (short slot = 0; slot < this.itemContainer.getCapacity(); slot++) {
            var stack = this.itemContainer.getItemStack(slot);
            if (!ItemStack.isEmpty(stack)) {
                // Overflow when shrinking is dropped: there is no world reference here to eject
                // into, and the alternative is refusing to shrink at all.
                resized.addItemStack(stack, false, false, false);
            }
        }

        this.itemContainer = resized;
    }

    public ItemBlockComponent() {
        this(new BlockFaceConfig(), 0, false, 0L,
                SimpleItemContainer.getNewContainer(DEFAULT_SLOTS), (short) 0);
    }

    /// `declaredSlots` is a constructor parameter rather than a field set afterwards so that
    /// `clone` cannot silently drop it. It did, and since placing a block clones the asset's
    /// template component, every burner was placed with a declared size of zero -- which reads as
    /// "leave the container alone" and left the fuel window showing sixteen slots.
    public ItemBlockComponent(
            BlockFaceConfig blockFaceConfig,
            int transferPriority,
            boolean isExtracting,
            long transferSpeed,
            ItemContainer itemContainer,
            short declaredSlots) {
        super(blockFaceConfig, transferPriority, isExtracting);

        Validation.requireNonNegative(transferSpeed, "transferSpeed");

        this.transferSpeed = transferSpeed;
        this.itemContainer = itemContainer;
        this.declaredSlots = declaredSlots;
    }

    @Override
    @Nonnull
    public Component<ChunkStore> clone() {
        return new ItemBlockComponent(
                this.blockFaceConfig.clone(),
                this.transferPriority,
                this.isExtracting,
                this.transferSpeed,
                this.itemContainer == null ? null : this.itemContainer.clone(),
                this.declaredSlots);
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
        ensureDeclaredCapacity();

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
