package at.rasebdon.hytech.core.interactions;

import at.rasebdon.hytech.core.components.LogisticComponent;
import at.rasebdon.hytech.core.util.HytechUtil;
import at.rasebdon.hytech.core.util.LogisticLookup;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.protocol.Interaction;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.UseBlockInteraction;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/// Prints whatever logistic component(s) the targeted block carries.
public class ReadLogisticContainerInteraction extends SimpleBlockInteraction {

    @Nonnull
    public static final BuilderCodec<ReadLogisticContainerInteraction> CODEC =
            BuilderCodec.builder(
                            ReadLogisticContainerInteraction.class,
                            ReadLogisticContainerInteraction::new,
                            SimpleBlockInteraction.CODEC)
                    .documentation("Reports every Hytech logistic container on the target block.")
                    .build();

    private static void doInteraction(
            @Nonnull InteractionContext context,
            @Nonnull World world,
            @Nonnull Vector3i targetBlock) {

        for (var component : LogisticLookup.allComponentsAt(world, targetBlock)) {
            report(context, component);
        }
    }

    private static void report(@Nonnull InteractionContext context, @Nullable LogisticComponent<?> component) {
        if (component == null) return;

        HytechUtil.sendPlayerMessage(context.getEntity(), component.toString());
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
        return new UseBlockInteraction();
    }

    @Nonnull
    @Override
    public String toString() {
        return "ReadLogisticContainerInteraction{} " + super.toString();
    }
}
