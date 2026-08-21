package at.rasebdon.hytech.core.interactions;

import at.rasebdon.hytech.core.HytechCoreModule;
import at.rasebdon.hytech.core.components.LogisticComponent;
import at.rasebdon.hytech.core.util.HytechUtil;
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

/// Prints whatever logistic component the targeted block carries.
///
/// Energy and items each had their own copy of this that differed only in which module it
/// asked for the component type. Since every component already describes itself through
/// `toString`, one interaction that walks the registered component types covers every
/// resource type -- including a block that carries several, such as the burner generator
/// holding both energy and items.
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

        var core = HytechCoreModule.get();

        for (var blockType : core.getBlockComponents()) {
            report(context, HytechUtil.getBlockComponent(world, targetBlock, blockType));
        }

        for (var pipeType : core.getPipeComponents()) {
            report(context, HytechUtil.getBlockComponent(world, targetBlock, pipeType));
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
