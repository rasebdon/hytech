package at.rasebdon.hytech.energy.interaction;

import at.rasebdon.hytech.core.components.LogisticComponent;
import at.rasebdon.hytech.core.util.HytechUtil;
import at.rasebdon.hytech.energy.EnergyModule;
import at.rasebdon.hytech.energy.HytechEnergyContainer;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.Interaction;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ReadEnergyContainerBlockInteraction extends SimpleBlockInteraction {
    @Nonnull
    public static final BuilderCodec<ReadEnergyContainerBlockInteraction> CODEC =
            BuilderCodec.builder(
                            ReadEnergyContainerBlockInteraction.class,
                            ReadEnergyContainerBlockInteraction::new,
                            SimpleBlockInteraction.CODEC)
                    .documentation("Attempts to read the target blocks energy container.").build();

    private static void doInteraction(
            @Nonnull InteractionContext context,
            @Nonnull World world,
            @Nonnull Vector3i targetBlock) {

        var energyBlock = HytechUtil.getBlockComponent(
                world,
                targetBlock,
                EnergyModule.get().getBlockComponentType()
        );

        var energyPipe = HytechUtil.getBlockComponent(
                world,
                targetBlock,
                EnergyModule.get().getPipeComponentType()
        );

        var component = energyBlock == null ? energyPipe : energyBlock;
        if (component != null) {
            sendEnergyMessageToPlayer(context.getEntity(), component);
        }
    }

    private static void sendEnergyMessageToPlayer(Ref<EntityStore> playerRef, LogisticComponent<HytechEnergyContainer> component) {
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
        return "ReadEnergyContainerBlockInteraction{} " + super.toString();
    }
}
