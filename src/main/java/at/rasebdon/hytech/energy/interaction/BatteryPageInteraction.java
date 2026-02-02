package at.rasebdon.hytech.energy.interaction;

import at.rasebdon.hytech.core.util.HytechUtil;
import at.rasebdon.hytech.energy.EnergyModule;
import au.ellie.hyui.builders.PageBuilder;
import au.ellie.hyui.html.TemplateProcessor;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;

public class BatteryPageInteraction extends SimpleBlockInteraction {
    @Nonnull
    public static final BuilderCodec<BatteryPageInteraction> CODEC =
            BuilderCodec.builder(
                            BatteryPageInteraction.class,
                            BatteryPageInteraction::new,
                            SimpleBlockInteraction.CODEC)
                    .build();

    @Override
    protected void interactWithBlock(
            @NotNull World world,
            @NotNull CommandBuffer<EntityStore> commandBuffer,
            @NotNull InteractionType type,
            @NotNull InteractionContext context,
            @Nullable ItemStack item,
            @NotNull Vector3i blockPos,
            @NotNull CooldownHandler cooldownHandler) {
        openUi(context, world, blockPos);
    }

    @Override
    protected void simulateInteractWithBlock(
            @NotNull InteractionType type,
            @NotNull InteractionContext context,
            @Nullable ItemStack item,
            @NotNull World world,
            @NotNull Vector3i blockPos) {
        openUi(context, world, blockPos);
    }

    private void openUi(@NotNull InteractionContext context,
                        @NotNull World world,
                        @NotNull Vector3i blockPos) {
        var containerComponent = HytechUtil.getComponentAtBlock(
                world,
                blockPos,
                EnergyModule.get().getBlockEnergyContainerComponentType());

        if (containerComponent == null) {
            return;
        }


        var entityStore = world.getEntityStore().getStore();
        var playerRef = entityStore.getComponent(context.getEntity(), PlayerRef.getComponentType());
        if (playerRef != null && containerComponent.isAvailable()) {
            var container = containerComponent.getContainer();
            var template = new TemplateProcessor()
                    .setVariable("energyRatio", container.getFillRatio())
                    .setVariable("currentEnergy", container.getEnergy())
                    .setVariable("maxEnergy", container.getTotalCapacity());

            var page = PageBuilder.detachedPage()
                    .withLifetime(CustomPageLifetime.CanDismiss)
                    .loadHtml("Energy/BatteryPage.html", template);

            page.open(playerRef, entityStore);
        }
    }
}
