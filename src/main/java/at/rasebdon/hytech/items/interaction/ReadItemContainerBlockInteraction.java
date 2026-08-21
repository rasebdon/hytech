package at.rasebdon.hytech.items.interaction;

import at.rasebdon.hytech.core.components.LogisticComponent;
import at.rasebdon.hytech.core.util.HytechUtil;
import at.rasebdon.hytech.items.HytechItemContainer;
import at.rasebdon.hytech.items.ItemModule;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.Interaction;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/// Item-side counterpart to
/// [at.rasebdon.hytech.energy.interaction.ReadEnergyContainerBlockInteraction]: reports
/// what an item block or pipe segment is currently holding.
public class ReadItemContainerBlockInteraction extends SimpleBlockInteraction {
    @Nonnull
    public static final BuilderCodec<ReadItemContainerBlockInteraction> CODEC =
            BuilderCodec.builder(
                            ReadItemContainerBlockInteraction.class,
                            ReadItemContainerBlockInteraction::new,
                            SimpleBlockInteraction.CODEC)
                    .documentation("Attempts to read the target blocks item container.").build();

    private static void doInteraction(
            @Nonnull InteractionContext context,
            @Nonnull World world,
            @Nonnull Vector3i targetBlock) {

        var itemBlock = HytechUtil.getBlockComponent(
                world,
                targetBlock,
                ItemModule.get().getBlockComponentType()
        );

        var itemPipe = HytechUtil.getBlockComponent(
                world,
                targetBlock,
                ItemModule.get().getPipeComponentType()
        );

        var component = itemBlock == null ? itemPipe : itemBlock;
        if (component != null) {
            sendItemMessageToPlayer(context.getEntity(), component);
        }
    }

    private static void sendItemMessageToPlayer(
            Ref<EntityStore> playerRef, LogisticComponent<HytechItemContainer> component) {
        HytechUtil.sendPlayerMessage(playerRef, component.toString());
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
        doInteraction(context, world, targetBlock);
    }

    @Override
    protected void simulateInteractWithBlock(
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nullable ItemStack itemInHand,
            @Nonnull World world,
            @Nonnull Vector3i targetBlock) {
        doInteraction(context, world, targetBlock);
    }

    @Nonnull
    @Override
    protected Interaction generatePacket() {
        return new com.hypixel.hytale.protocol.UseBlockInteraction();
    }

    @Nonnull
    @Override
    public String toString() {
        return "ReadItemContainerBlockInteraction{} " + super.toString();
    }
}
