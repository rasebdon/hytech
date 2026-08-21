package at.rasebdon.hytech.energy.interaction.ui;

import at.rasebdon.hytech.core.interactions.ui.HytechPage;
import at.rasebdon.hytech.core.interactions.ui.OpenPageBlockInteraction;
import at.rasebdon.hytech.core.util.HytechUtil;
import at.rasebdon.hytech.energy.EnergyModule;
import au.ellie.hyui.builders.PageBuilder;
import au.ellie.hyui.html.TemplateProcessor;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.universe.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;

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
    @Nullable
    protected PageBuilder getPageBuilder(@NotNull InteractionContext context,
                                         @NotNull World world,
                                         @NotNull Vector3i blockPos) {
        var containerComponent = HytechUtil.getBlockComponent(
                world,
                blockPos,
                EnergyModule.get().getBlockComponentType());

        if (containerComponent == null || !containerComponent.isAvailable()) return null;

        var energy = containerComponent.getContainer();

        var template = new TemplateProcessor()
                .setVariable("blockName", getBlockName(world, blockPos))
                .setVariable("currentEnergy", energy::getAmount)
                .setVariable("maxEnergy", energy::getTotalCapacity)
                .setVariable("energyFillRatio", energy::getFillRatio)
                .setVariable("fillPercent", () -> Math.round(energy.getFillRatio() * 100f))
                .setVariable("transferSpeed", energy::getTransferSpeed)
                .setVariable("energyDelta", energy::getDelta)
                .setVariable("energyDeltaSymbol", () -> getPrefix(energy.getDelta()))
                .setVariable("energyDeltaColor", () -> getValueColor(energy.getDelta()));

        return HytechPage.of("Energy/Storage/BatteryPage.html", template);
    }
}
