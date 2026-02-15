package at.rasebdon.hytech.energy.interaction.ui;

import at.rasebdon.hytech.core.interactions.ui.OpenPageBlockInteraction;
import at.rasebdon.hytech.core.util.HytechUtil;
import at.rasebdon.hytech.energy.EnergyModule;
import au.ellie.hyui.builders.HyUIPage;
import au.ellie.hyui.builders.PageBuilder;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.universe.world.World;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;

public class OpenGeneratorPageInteraction extends OpenPageBlockInteraction {
    @Nonnull
    public static final BuilderCodec<OpenGeneratorPageInteraction> CODEC =
            BuilderCodec.builder(
                            OpenGeneratorPageInteraction.class,
                            OpenGeneratorPageInteraction::new,
                            OpenPageBlockInteraction.CODEC)
                    .build();

    @Override
    protected PageBuilder getPageBuilder(@NotNull InteractionContext context, @NotNull World world, @NotNull Vector3i blockPos) {
        var generatorComponent = HytechUtil.getComponentAtBlock(
                world,
                blockPos,
                EnergyModule.get().getEnergyGeneratorComponentType());
        assert generatorComponent != null;

        var blockName = getBlockName(world, blockPos);

        return switch (generatorComponent.getGeneratorType()) {
            case SOLAR -> getSolarPanelPage();
            case WIND, FUEL_LIQUID, FUEL_SOLID -> null;
        };
    }

    private PageBuilder getSolarPanelPage() {
        return PageBuilder.detachedPage()
                .loadHtml("Energy/Generators/SolarPanelPage.html")
                .withLifetime(CustomPageLifetime.CanDismiss)
                .addEventListener("exit-button", CustomUIEventBindingType.Activating,
                        (_, ctx) -> ctx.getPage().ifPresent(HyUIPage::close));
    }
}
