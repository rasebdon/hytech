package at.rasebdon.hytech.energy.interaction.ui;

import at.rasebdon.hytech.core.interactions.ui.OpenPageBlockInteraction;
import at.rasebdon.hytech.core.util.HytechUtil;
import at.rasebdon.hytech.energy.EnergyModule;
import au.ellie.hyui.builders.HyUIPage;
import au.ellie.hyui.builders.PageBuilder;
import au.ellie.hyui.events.PageRefreshResult;
import au.ellie.hyui.html.TemplateProcessor;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.universe.world.World;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;

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
        var containerComponent = HytechUtil.getBlockComponent(
                world,
                blockPos,
                EnergyModule.get().getBlockComponentType());
        assert containerComponent != null;

        if (!containerComponent.isAvailable()) return null;

        var energyContainer = containerComponent.getContainer();

        var template = new TemplateProcessor()
                .setVariable("blockName", getBlockName(world, blockPos))
                .setVariable("currentEnergy", energyContainer::getEnergy)
                .setVariable("maxEnergy", energyContainer::getTotalCapacity)
                .setVariable("energyFillRatio", energyContainer::getFillRatio)
                .setVariable("energyDelta", energyContainer::getEnergyDelta)
                .setVariable("energyDeltaSymbol", () -> getPrefix(energyContainer.getEnergyDelta()))
                .setVariable("energyDeltaColor", () -> getValueColor(energyContainer.getEnergyDelta()));

        return PageBuilder.detachedPage()
                .loadHtml("Energy/Storage/BatteryPage.html", template)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .withRefreshRate(1000)
                .onRefresh(_ -> PageRefreshResult.UPDATE)
                .enableRuntimeTemplateUpdates(true)
                .addEventListener("exit-button", CustomUIEventBindingType.Activating,
                        (_, ctx) -> ctx.getPage().ifPresent(HyUIPage::close));
    }
}
