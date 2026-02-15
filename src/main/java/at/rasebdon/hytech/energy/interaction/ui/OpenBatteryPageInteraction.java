package at.rasebdon.hytech.energy.interaction.ui;

import at.rasebdon.hytech.core.interactions.ui.OpenPageBlockInteraction;
import at.rasebdon.hytech.core.util.HytechUtil;
import at.rasebdon.hytech.energy.EnergyModule;
import at.rasebdon.hytech.energy.IEnergyContainer;
import au.ellie.hyui.builders.*;
import au.ellie.hyui.events.PageRefreshResult;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.universe.world.World;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.function.Function;

public class OpenBatteryPageInteraction extends OpenPageBlockInteraction {
    @Nonnull
    public static final BuilderCodec<OpenBatteryPageInteraction> CODEC =
            BuilderCodec.builder(
                            OpenBatteryPageInteraction.class,
                            OpenBatteryPageInteraction::new,
                            OpenPageBlockInteraction.CODEC)
                    .build();

    @Override
    protected PageBuilder getPageBuilder(@NotNull InteractionContext context,
                        @NotNull World world,
                        @NotNull Vector3i blockPos) {
        var containerComponent = HytechUtil.getComponentAtBlock(
                world,
                blockPos,
                EnergyModule.get().getBlockEnergyContainerComponentType());
        assert containerComponent != null;

        if (!containerComponent.isAvailable()) return null;

        var energyContainer = containerComponent.getContainer();
        var blockName = getBlockName(world, blockPos);

        var pageBuilder = PageBuilder.detachedPage()
                .loadHtml("Energy/Storage/BatteryPage.html")
                .withLifetime(CustomPageLifetime.CanDismiss)
                .withRefreshRate(1000)
                .onRefresh(this.onPageRefresh(energyContainer, blockName))
                .addEventListener("exit-button", CustomUIEventBindingType.Activating,
                        (_, ctx) -> ctx.getPage().ifPresent(HyUIPage::close));

        initPageBuilder(pageBuilder, energyContainer, blockName);
        return pageBuilder;
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
