package at.rasebdon.hytech.core.interactions;

import at.rasebdon.hytech.core.containers.TypedScalarContainer;
import at.rasebdon.hytech.core.util.LogisticLookup;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.UseBlockInteraction;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/// A carried item that holds a fixed measure of one typed resource -- a bucket of molten iron, a
/// canister of gas -- and empties it into a Hytech container on right-click.
///
/// The item is content and this is the library, so nothing here names a fluid: the item's own
/// asset declares what it holds, how much, and what is left in the hand afterwards.
///
/// Two paths reach [#pour]. Clicking a block with no `Use` interaction of its own runs this
/// interaction the ordinary way. Clicking a Hytech block runs *that block's* `Use` instead -- the
/// same rule that makes the wrench invisible to a machine -- so
/// [at.rasebdon.hytech.core.interactions.ui.OpenPageBlockInteraction] looks for a bucket in hand
/// with [#heldBy] and pours it before opening its page.
public class FluidBucketInteraction extends SimpleBlockInteraction {

    @Nonnull
    public static final BuilderCodec<FluidBucketInteraction> CODEC =
            BuilderCodec.builder(
                            FluidBucketInteraction.class,
                            FluidBucketInteraction::new,
                            SimpleBlockInteraction.CODEC)
                    .documentation("Empties a measure of one resource from the held item into the "
                            + "target block's container.")
                    .append(new KeyedCodec<>("Resource", Codec.STRING),
                            (interaction, value) -> interaction.resource = value,
                            (interaction) -> interaction.resource)
                    .documentation("Resource id this item holds, as a tank spells it (\"Molten_Iron\")")
                    .add()
                    .append(new KeyedCodec<>("Amount", Codec.LONG),
                            (interaction, value) -> interaction.amount = value,
                            (interaction) -> interaction.amount)
                    .documentation("How much one item empties into the container")
                    .addValidator(Validators.greaterThan(0L))
                    .add()
                    .append(new KeyedCodec<>("EmptyItem", Codec.STRING),
                            (interaction, value) -> interaction.emptyItem = value,
                            (interaction) -> interaction.emptyItem)
                    .documentation("Item left in the hand once poured; omit to consume the item outright")
                    .add()
                    .build();

    /// Anything but 0 would fail `greaterThan` against a fresh instance at registration, taking
    /// the plugin down with it -- validators run over the default, not just over asset values.
    private long amount;

    @Nullable
    private String resource;

    @Nullable
    private String emptyItem;

    /// The bucket the player is holding, or null when they are holding anything else. Resolves the
    /// item's declared interaction chain, the way `DoorBlockUtils` reads a door's.
    @Nullable
    public static FluidBucketInteraction heldBy(@Nullable ItemStack stack) {
        if (ItemStack.isEmpty(stack)) return null;

        var rootId = stack.getItem().getInteractions().get(InteractionType.Secondary);
        if (rootId == null) return null;

        var root = RootInteraction.getAssetMap().getAsset(rootId);
        if (root == null) return null;

        for (var interactionId : root.getInteractionIds()) {
            var interaction = Interaction.getAssetMap().getAsset(interactionId);

            if (interaction instanceof FluidBucketInteraction bucket) {
                return bucket;
            }
        }

        return null;
    }

    /// Empties one item into the block, all or nothing: a bucket that will not fit stays a bucket.
    /// False when nothing there could take it, which is what lets the caller carry on and open its
    /// page instead.
    public boolean pour(@Nonnull World world,
                        @Nonnull CommandBuffer<EntityStore> commandBuffer,
                        @Nonnull InteractionContext context,
                        @Nonnull Vector3i blockPos) {

        if (this.resource == null || this.amount <= 0L) return false;

        var container = acceptorAt(world, blockPos);
        if (container == null) return false;

        var held = context.getHeldItem();
        if (ItemStack.isEmpty(held)) return false;

        var itemContainer = context.getHeldItemContainer();
        if (itemContainer == null) return false;

        var removed = itemContainer
                .removeItemStackFromSlot(context.getHeldItemSlot(), held, 1);
        if (!removed.succeeded()) return false;

        context.setHeldItem(removed.getSlotAfter());

        if (container.getResourceType() == null) {
            container.setResourceType(this.resource);
        }
        container.add(this.amount);

        if (this.emptyItem != null) {
            var ref = context.getEntity();
            var inventory = InventoryComponent.getCombined(
                    commandBuffer, ref, InventoryComponent.HOTBAR_STORAGE_BACKPACK);

            SimpleItemContainer.addOrDropItemStack(
                    commandBuffer, ref, inventory, new ItemStack(this.emptyItem, 1));
        }

        return true;
    }

    /// The first container on the block that holds this resource, or none yet, and has room for a
    /// whole measure. Typed containers only -- energy and items have no resource identity to pour.
    @Nullable
    private TypedScalarContainer<String> acceptorAt(@Nonnull World world, @Nonnull Vector3i blockPos) {
        for (var component : LogisticLookup.allBlockComponentsAt(world, blockPos)) {
            if (!(component.getContainer() instanceof TypedScalarContainer<?> typed)) continue;

            // Every typed resource in the mod parameterizes on String; the wildcard hides that.
            @SuppressWarnings("unchecked")
            var container = (TypedScalarContainer<String>) typed;

            if (container.canAccept(this.resource)
                    && container.getRemainingCapacity() >= this.amount) {
                return container;
            }
        }

        return null;
    }

    @Override
    protected void interactWithBlock(
            @Nonnull World world,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nullable ItemStack itemInHand,
            @Nonnull Vector3i targetBlock,
            @Nonnull CooldownHandler cooldownHandler) {
        pour(world, commandBuffer, context, targetBlock);
    }

    @Override
    protected void simulateInteractWithBlock(
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nullable ItemStack itemInHand,
            @Nonnull World world,
            @Nonnull Vector3i targetBlock) {
        // Client-side prediction has no inventory to move; the server run does the work.
    }

    @Nonnull
    @Override
    protected com.hypixel.hytale.protocol.Interaction generatePacket() {
        return new UseBlockInteraction();
    }

    @Nonnull
    @Override
    public String toString() {
        return "FluidBucketInteraction{resource=" + this.resource
                + ", amount=" + this.amount
                + ", emptyItem=" + this.emptyItem + "} " + super.toString();
    }
}
