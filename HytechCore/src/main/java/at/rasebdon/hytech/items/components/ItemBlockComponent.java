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
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.container.filter.SlotFilter;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

public class ItemBlockComponent extends LogisticBlockComponent<HytechItemContainer> implements HytechItemContainer {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final short DEFAULT_SLOTS = 16;

    public static final BuilderCodec<ItemBlockComponent> CODEC =
            BuilderCodec.builder(ItemBlockComponent.class, ItemBlockComponent::new, LogisticBlockComponent.CODEC)
                    .append(new KeyedCodec<>("ItemContainer", ItemContainer.CODEC),
                            (state, o) -> state.itemContainer = o,
                            (state) -> state.itemContainer).add()
                    // Enforced on read by ensureDeclaredCapacity, not here, so a block saved before
                    // this key existed still gets corrected on load.
                    .append(new KeyedCodec<>("Slots", Codec.SHORT),
                            (c, v) -> c.declaredSlots = v,
                            (c) -> c.declaredSlots)
                    // 0 is the floor, not 1: it's both the "leave alone" value and the field's
                    // default, and BuilderCodec validates defaults at registration.
                    .addValidator(Validators.greaterThanOrEqual((short) 0))
                    .documentation("Number of item slots this block holds, or 0 to keep the current size").add()
                    .append(new KeyedCodec<>("InputSlots", Codec.SHORT),
                            (c, v) -> c.inputSlots = v,
                            (c) -> c.inputSlots)
                    .addValidator(Validators.greaterThanOrEqual((short) 0))
                    .documentation("Leading slots that accept ingredients, or 0 for an undivided container").add()
                    .append(new KeyedCodec<>("OutputSlots", Codec.SHORT),
                            (c, v) -> c.outputSlots = v,
                            (c) -> c.outputSlots)
                    .addValidator(Validators.greaterThanOrEqual((short) 0))
                    .documentation("Trailing slots that hold results; nothing outside the block may insert into them").add()
                    .append(new KeyedCodec<>("MaxTransfer", Codec.LONG),
                            (c, v) -> c.transferSpeed = v,
                            (c) -> c.transferSpeed)
                    .addValidator(Validators.greaterThanOrEqual(0L))
                    .documentation("Maximum items transferred per transfer tick").add()
                    .build();

    private ItemContainer itemContainer;

    private short declaredSlots;

    private short inputSlots;
    private short outputSlots;

    private long transferSpeed;

    // Filters aren't part of the codec, so this tracks whether they've been re-applied to the
    // live container after a decode or resize.
    private transient boolean filtersApplied;

    private short requestedSlots() {
        int split = this.inputSlots + this.outputSlots;

        return split > 0 ? (short) Math.min(Short.MAX_VALUE, split) : this.declaredSlots;
    }

    /// Enforced on read, not only at decode: a block placed before `Slots` existed has no `Slots`
    /// key to trigger a decode-time resize, so it would never get corrected otherwise.
    private void ensureDeclaredCapacity() {
        short slots = requestedSlots();

        if (slots <= 0 || this.itemContainer == null) return;

        if (this.itemContainer.getCapacity() != slots) {
            // No world reference to eject overflow into when shrinking; log it instead of
            // losing items silently.
            var overflow = new ArrayList<ItemStack>();

            this.itemContainer = ItemContainer.ensureContainerCapacity(
                    this.itemContainer, slots, SimpleItemContainer::getNewContainer, overflow);

            if (!overflow.isEmpty()) {
                LOGGER.atWarning().log("Resizing an item container to %d slots dropped %d stack(s)",
                        slots, overflow.size());
            }

            this.filtersApplied = false;
        }

        if (!this.filtersApplied) {
            applyOutputFilters();
            this.filtersApplied = true;
        }
    }

    /// Denies ADD on output slots, covering both pipes and players; the machine writes results with
    /// filtering off. Removal stays open -- keeping pipes out is [HytechItemContainer#canExtractFrom].
    private void applyOutputFilters() {
        if (this.itemContainer == null || this.outputSlots <= 0) return;

        short capacity = this.itemContainer.getCapacity();

        for (short slot = this.inputSlots; slot < capacity; slot++) {
            this.itemContainer.setSlotFilter(FilterActionType.ADD, slot, SlotFilter.DENY);
        }
    }

    public ItemBlockComponent() {
        this(new BlockFaceConfig(), 0, false, 0L,
                SimpleItemContainer.getNewContainer(DEFAULT_SLOTS), (short) 0, (short) 0, (short) 0);
    }

    /// `declaredSlots` is a constructor param, not set afterwards, so `clone` can't drop it --
    /// it did once, and a dropped value reads as "leave the container alone".
    public ItemBlockComponent(
            BlockFaceConfig blockFaceConfig,
            int transferPriority,
            boolean isExtracting,
            long transferSpeed,
            ItemContainer itemContainer,
            short declaredSlots,
            short inputSlots,
            short outputSlots) {
        super(blockFaceConfig, transferPriority, isExtracting);

        Validation.requireNonNegative(transferSpeed, "transferSpeed");

        this.transferSpeed = transferSpeed;
        this.itemContainer = itemContainer;
        this.declaredSlots = declaredSlots;
        this.inputSlots = inputSlots;
        this.outputSlots = outputSlots;
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
                this.declaredSlots,
                this.inputSlots,
                this.outputSlots);
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

    public short getInputSlots() {
        ensureDeclaredCapacity();

        return inputSlots;
    }

    public short getOutputSlots() {
        ensureDeclaredCapacity();

        return outputSlots;
    }

    @Override
    public boolean canExtractFrom(short slot) {
        return this.outputSlots <= 0 || slot >= this.inputSlots;
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
