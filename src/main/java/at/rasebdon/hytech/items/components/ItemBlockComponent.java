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
                    // A machine splits its container: ingredients in the first slots, results in
                    // the last. Both default to 0, which means "undivided" -- what a chest-like
                    // buffer or the burner's fuel slot wants.
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

    /// Slot count the asset asks for, or 0 to keep whatever the container already has.
    private short declaredSlots;

    /// Machine split: `inputSlots` leading slots take ingredients, the `outputSlots` after them
    /// hold results. Both zero means the container is undivided.
    private short inputSlots;
    private short outputSlots;

    private long transferSpeed;

    /// Whether the output slots' insertion filters have been applied to the live container.
    ///
    /// Filters are behaviour, not data: they are not part of the codec, so they have to be
    /// re-applied to every container this component decodes or resizes into.
    private transient boolean filtersApplied;

    /// The size the asset asks for: the machine split when there is one, `Slots` otherwise.
    private short requestedSlots() {
        int split = this.inputSlots + this.outputSlots;

        return split > 0 ? (short) Math.min(Short.MAX_VALUE, split) : this.declaredSlots;
    }

    /// Brings the container to the size the asset declares, keeping what fits, and re-applies the
    /// output slots' insertion filters.
    ///
    /// Enforced on read rather than only at decode, because the two do not coincide. A block placed
    /// before `Slots` existed has a saved sixteen-slot container and no `Slots` key of its own, so a
    /// decode-time resize would never run for it and the burner would keep showing sixteen slots.
    /// Vanilla does the same thing for `ItemContainerBlock`, resizing to the asset capacity when the
    /// block loads.
    ///
    /// Idempotent and a couple of comparisons in the common case, so calling it from the accessor
    /// costs nothing once the container already agrees.
    private void ensureDeclaredCapacity() {
        short slots = requestedSlots();

        if (slots <= 0 || this.itemContainer == null) return;

        if (this.itemContainer.getCapacity() != slots) {
            // Overflow when shrinking has nowhere to go: a component holds no world reference to
            // eject into, and the alternative is refusing to shrink at all. Say so rather than
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

    /// Closes the output slots to outside insertion, the way vanilla closes a bench's output.
    ///
    /// A denied `ADD` covers pipes and players in one stroke, since both go through the container's
    /// filtered path; the machine writes its own results with filtering off. Removal stays open, so
    /// a player can still empty the slots by hand -- keeping a pipe out of them is
    /// [HytechItemContainer#canExtractFrom]'s job, not a filter's.
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

    /// How many leading slots take ingredients; 0 when the container is undivided.
    public short getInputSlots() {
        ensureDeclaredCapacity();

        return inputSlots;
    }

    /// How many trailing slots hold results; 0 when the container is undivided.
    public short getOutputSlots() {
        ensureDeclaredCapacity();

        return outputSlots;
    }

    /// Only the result slots are the network's to empty; see [HytechItemContainer#canExtractFrom].
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
