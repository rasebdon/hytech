package at.rasebdon.hytech.energy.interaction;

import at.rasebdon.hytech.core.util.HytechUtil;
import at.rasebdon.hytech.energy.EnergyModule;
import at.rasebdon.hytech.energy.IEnergyContainer;
import au.ellie.hyui.builders.*;
import au.ellie.hyui.events.PageRefreshResult;
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
import java.util.function.Function;

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
        world.execute(() -> {

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
                createBatteryPage()
                        .withLifetime(CustomPageLifetime.CanDismiss)
                        .withRefreshRate(1000)
                        .onRefresh(this.onPageRefresh(containerComponent.getContainer()))
                        .open(playerRef, entityStore);
            }
        });
    }

    private Function<HyUIPage, PageRefreshResult> onPageRefresh(IEnergyContainer container) {
        return (HyUIPage page) -> {
            page.getById("energylabel", LabelBuilder.class)
                    .ifPresent(label -> label.withText(container.getEnergy() + " / " + container.getTotalCapacity() + " RF"));
            page.getById("energybar", ProgressBarBuilder.class)
                    .ifPresent(bar -> bar.withValue(container.getFillRatio()));

            return PageRefreshResult.UPDATE;
        };
    }

    private PageBuilder createBatteryPage() {
        return PageBuilder.detachedPage()
                .addElement(
                        ContainerBuilder.container()
                                .withLayoutMode("Top")
                                .addTitleChild(
                                        LabelBuilder.label()
                                                .withText("Battery")
                                )
                                .addContentChild(
                                        ProgressBarBuilder.progressBar()
                                                .withId("energybar")
                                                .withValue(0.5f)
                                                .withAlignment("Horizontal")
                                                .withAnchor(new HyUIAnchor().setWidth(200).setHeight(10))
                                )
                                .addContentChild(
                                        GroupBuilder.group()
                                                .withLayoutMode("Middle")
                                                .addChild(
                                                        LabelBuilder.label()
                                                                .withId("energylabel")
                                                                .withText("-/- RF")

                                                )
                                )
                );
    }
}
