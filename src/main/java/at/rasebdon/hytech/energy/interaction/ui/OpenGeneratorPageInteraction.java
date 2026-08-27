package at.rasebdon.hytech.energy.interaction.ui;

import at.rasebdon.hytech.core.interactions.ui.OpenPageBlockInteraction;
import at.rasebdon.hytech.core.ui.HytechCustomPage;
import at.rasebdon.hytech.core.ui.MachinePage;
import at.rasebdon.hytech.core.ui.MachineView;
import at.rasebdon.hytech.core.util.HytechUtil;
import at.rasebdon.hytech.energy.EnergyModule;
import at.rasebdon.hytech.energy.HytechEnergyContainer;
import at.rasebdon.hytech.energy.components.EnergyGeneratorComponent;
import at.rasebdon.hytech.energy.components.FuelBurnerComponent;
import at.rasebdon.hytech.energy.util.FuelUtil;
import at.rasebdon.hytech.items.ItemModule;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;

import javax.annotation.Nonnull;

/// Opens the page for whichever kind of generator this block is.
///
/// All three share one document; they differ only in what fills the secondary bar -- sunlight for
/// solar, altitude for wind, burn time for the burner -- and whether there are fuel slots.
public class OpenGeneratorPageInteraction extends OpenPageBlockInteraction {

    /// Wind output ramps between these heights, matching `EnergyGenerationSystem`.
    private static final int WIND_MIN_HEIGHT = 64;
    private static final int WIND_MAX_HEIGHT = 160;

    @Nonnull
    public static final BuilderCodec<OpenGeneratorPageInteraction> CODEC =
            BuilderCodec.builder(
                            OpenGeneratorPageInteraction.class,
                            OpenGeneratorPageInteraction::new,
                            OpenPageBlockInteraction.CODEC)
                    .build();

    /// Float rather than the double `WorldTimeResource` reports, since a bar takes a float.
    private static float sunlight(World world) {
        var time = world.getEntityStore().getStore().getResource(WorldTimeResource.getResourceType());

        return (float) time.getSunlightFactor();
    }

    @Override
    @Nullable
    protected HytechCustomPage createPage(@NotNull World world,
                                          @NotNull Vector3i blockPos,
                                          @NotNull PlayerRef playerRef) {

        var generator = HytechUtil.getBlockComponent(
                world, blockPos, EnergyModule.get().getGeneratorComponentType());
        if (generator == null) return null;

        var energyComponent = HytechUtil.getBlockComponent(
                world, blockPos, EnergyModule.get().getBlockComponentType());
        if (energyComponent == null) return null;

        var burner = HytechUtil.getBlockComponent(
                world, blockPos, EnergyModule.get().getFuelBurnerComponentType());

        var fuelComponent = HytechUtil.getBlockComponent(
                world, blockPos, ItemModule.get().getBlockComponentType());

        // Non-null only for a burner, which is what makes its page open with the player's
        // inventory while a solar panel's does not.
        ItemContainer fuel = burner == null || fuelComponent == null
                ? null
                : fuelComponent.getItemContainer();

        return new MachinePage(playerRef, world, blockPos, fuel,
                (_, view) -> fill(view, world, blockPos, generator,
                        energyComponent.getContainer(), burner, fuel));
    }

    private static String burnStatus(FuelBurnerComponent burner) {
        if (!burner.isBurning()) return "No fuel";

        return String.format("%.0fs remaining", Math.ceil(burner.getBurnTimeRemaining()));
    }

    private void fill(MachineView view,
                      World world,
                      Vector3i blockPos,
                      EnergyGeneratorComponent generator,
                      HytechEnergyContainer energy,
                      @Nullable FuelBurnerComponent burner,
                      @Nullable ItemContainer fuel) {

        view.primary(
                String.format("%,d / %,d RF", energy.getAmount(), energy.getTotalCapacity()),
                energy.getFillRatio(),
                signed(generator.getCurrentRate()) + " RF/t");

        // Only the burner has slots. The others say nothing, and the contents column and the
        // player's inventory disappear with them -- the remaining panels flex to fill the row.
        switch (generator.getGeneratorType()) {
            case SOLAR -> {
                float sunlight = sunlight(world);
                view.secondary("Sunlight", sunlight, percent(sunlight) + "% daylight");
            }
            case WIND -> {
                float altitude = altitude(blockPos);
                view.secondary("Wind", altitude, "Y " + blockPos.y + " - " + percent(altitude) + "% exposure");
            }
            case FUEL_SOLID -> {
                // An undivided grid: fuel goes in and ash does not come out, so there is no
                // ingredient/result split to draw.
                view.slots("Fuel", fuel, 0, 0, stack -> !FuelUtil.isFuel(stack));

                // The burn is a countdown, not a level, so it renders as progress beside the fuel
                // it is consuming -- the same bar and the same "left" wording a crusher uses.
                if (burner != null) {
                    view.progress(burner.getBurnRatio(), burner.getBurnTimeRemaining(),
                            burnStatus(burner));
                }
            }
            // Wiring these to the fluid module is not done; say so rather than showing an
            // empty bar that looks broken.
            case FUEL_LIQUID -> view.secondary("Fuel", 0f, "Liquid fuel not implemented");
        }

        view.detail("Output", generator.getCurrentRate() + " RF/t");
        view.detail("Maximum", generator.getBaseRate() + " RF/t");
        view.detail("Max transfer", energy.getTransferSpeed() + " RF/t");
    }

    private static float altitude(Vector3i blockPos) {
        float ramp = (blockPos.y - WIND_MIN_HEIGHT) / (float) (WIND_MAX_HEIGHT - WIND_MIN_HEIGHT);

        return Math.max(0f, Math.min(1f, ramp));
    }
}
