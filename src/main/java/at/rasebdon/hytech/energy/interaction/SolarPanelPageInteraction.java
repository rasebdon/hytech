package at.rasebdon.hytech.energy.interaction;

import au.ellie.hyui.builders.PageBuilder;
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

public class SolarPanelPageInteraction extends SimpleBlockInteraction {
    @Nonnull
    public static final BuilderCodec<SolarPanelPageInteraction> CODEC =
            BuilderCodec.builder(
                            SolarPanelPageInteraction.class,
                            SolarPanelPageInteraction::new,
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
        openUi(context, world);
    }

    @Override
    protected void simulateInteractWithBlock(
            @NotNull InteractionType type,
            @NotNull InteractionContext context,
            @Nullable ItemStack item,
            @NotNull World world,
            @NotNull Vector3i blockPos) {
        openUi(context, world);
    }

    private void openUi(@NotNull InteractionContext context, @NotNull World world) {
        var page = PageBuilder.detachedPage()
                .withLifetime(CustomPageLifetime.CanDismiss)
                .loadHtml("Energy/Generators/SolarPanelPage.html");

        var entityStore = world.getEntityStore().getStore();
        var playerRef = entityStore.getComponent(context.getEntity(), PlayerRef.getComponentType());
        if (playerRef != null) {
            page.open(playerRef, entityStore);
        }
    }
}
