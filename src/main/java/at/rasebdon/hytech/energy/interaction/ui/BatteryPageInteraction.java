package at.rasebdon.hytech.energy.interaction.ui;

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
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.MessageUtil;
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

            var blockName = getBlockName(world, blockPos);

            var entityStore = world.getEntityStore().getStore();
            var playerRef = entityStore.getComponent(context.getEntity(), PlayerRef.getComponentType());
            if (playerRef != null && containerComponent.isAvailable()) {
                var energyContainer = containerComponent.getContainer();
                var pageBuilder = PageBuilder.detachedPage()
                        .loadHtml("Energy/Storage/BatteryPage.html")
                        .withLifetime(CustomPageLifetime.CanDismiss)
                        .withRefreshRate(1000)
                        .onRefresh(this.onPageRefresh(energyContainer, blockName))
                        .addEventListener("exit-button", CustomUIEventBindingType.Activating,
                                (_, ctx) -> ctx.getPage().ifPresent(HyUIPage::close));
                initPageBuilder(pageBuilder, energyContainer, blockName);
                pageBuilder.open(playerRef, entityStore);
            }
        });
    }

    private String getBlockName(@NotNull World world, @NotNull Vector3i blockPos) {
        var blockType = HytechUtil.getBlockType(world, blockPos);
        assert blockType != null;

        var blockItem = blockType.getItem();
        assert blockItem != null;

        var translationKey = blockItem.getTranslationProperties().getName();
        assert translationKey != null;

        return MessageUtil.toAnsiString(Message.translation(translationKey)).toString();
    }

    private void setEnergyDeltaText(LabelBuilder labelBuilder, IEnergyContainer energyContainer) {
        var energyDelta = energyContainer.getEnergyDelta();
        var energyDeltaStyle = new HyUIStyle();
        energyDeltaStyle.setAlignment(Alignment.Center);
        String energyDeltaSymbol;

        if (energyDelta < 0) {
            energyDeltaStyle.setTextColor("#fc2e23");
            energyDeltaSymbol = "-";
        } else if (energyDelta > 0) {
            energyDeltaStyle.setTextColor("#23fc31");
            energyDeltaSymbol = "+";
        } else {
            energyDeltaSymbol = "";
        }

        labelBuilder.withStyle(energyDeltaStyle);
        labelBuilder.withText(energyDeltaSymbol + energyDelta + " RF/t");
    }

    private void setEnergyText(LabelBuilder labelBuilder, IEnergyContainer container) {
        labelBuilder.withText(container.getEnergy() + " / " + container.getTotalCapacity() + " RF");
    }

    private Function<HyUIPage, PageRefreshResult> onPageRefresh(IEnergyContainer energyContainer, String blockName) {
        return (HyUIPage page) -> {
            page.getById("base-container", ContainerBuilder.class)
                    .ifPresent(containerBuilder ->
                            containerBuilder.withTitleText(blockName));
            page.getById("energy-label", LabelBuilder.class)
                    .ifPresent(label -> setEnergyText(label, energyContainer));
            page.getById("energy-change-label", LabelBuilder.class)
                    .ifPresent(label -> setEnergyDeltaText(label, energyContainer));

            page.getById("energy-bar", ProgressBarBuilder.class)
                    .ifPresent(bar -> bar.withValue(energyContainer.getFillRatio()));

            return PageRefreshResult.UPDATE;
        };
    }

    private void initPageBuilder(PageBuilder pageBuilder, IEnergyContainer energyContainer, String blockName) {
        pageBuilder.getById("base-container", ContainerBuilder.class)
                .ifPresent(containerBuilder ->
                        containerBuilder.withTitleText(blockName));
        pageBuilder.getById("energy-label", LabelBuilder.class)
                .ifPresent(label -> setEnergyText(label, energyContainer));
        pageBuilder.getById("energy-change-label", LabelBuilder.class)
                .ifPresent(label -> setEnergyDeltaText(label, energyContainer));
        pageBuilder.getById("energy-bar", ProgressBarBuilder.class)
                .ifPresent(bar -> bar.withValue(energyContainer.getFillRatio()));
    }
}
