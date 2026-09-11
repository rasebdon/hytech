package at.rasebdon.hytech.content;

import at.rasebdon.hytech.content.generators.BurnerBlockStateSystem;
import at.rasebdon.hytech.content.generators.EnergyGenerationSystem;
import at.rasebdon.hytech.content.generators.EnergyGeneratorComponent;
import at.rasebdon.hytech.content.generators.FuelBurnerComponent;
import at.rasebdon.hytech.content.generators.OpenGeneratorPageInteraction;
import at.rasebdon.hytech.content.storage.EnergyBlockStateSystem;
import at.rasebdon.hytech.content.storage.OpenBatteryPageInteraction;
import at.rasebdon.hytech.energy.EnergyModule;
import at.rasebdon.hytech.items.ItemModule;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;

/// Hytech's own blocks, on top of HytechCore's resource types.
///
/// Content, not framework: a generator kind can't be added without editing the closed
/// `GeneratorType` enum, so it lives here rather than in the library. Components keep the ids they
/// were registered under (`hytech:energy:generator`, `hytech:energy:fuel_burner`), since
/// `registerComponent` keys persistence by id, not by class.
///
/// Registers through this plugin's own registry while reading component types the library
/// registered through its own; the manifest dependency on `Technic:HytechCore` guarantees the
/// library is set up and started first, so `EnergyModule.get()` can't be premature.
public final class HytechContentModule {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static HytechContentModule INSTANCE;

    private final ComponentType<ChunkStore, EnergyGeneratorComponent> generatorComponentType;
    private final ComponentType<ChunkStore, FuelBurnerComponent> fuelBurnerComponentType;

    private HytechContentModule(@Nonnull ComponentRegistryProxy<ChunkStore> registry) {
        generatorComponentType = registry.registerComponent(
                EnergyGeneratorComponent.class,
                "hytech:energy:generator",
                EnergyGeneratorComponent.CODEC
        );

        fuelBurnerComponentType = registry.registerComponent(
                FuelBurnerComponent.class,
                "hytech:energy:fuel_burner",
                FuelBurnerComponent.CODEC
        );

        registry.registerSystem(
                new EnergyGenerationSystem(
                        generatorComponentType,
                        EnergyModule.get().getBlockComponentType(),
                        fuelBurnerComponentType,
                        // Burner reads fuel from an item container, so items must init before energy.
                        ItemModule.get().getBlockComponentType())
        );
        registry.registerSystem(
                new EnergyBlockStateSystem(EnergyModule.get().getBlockComponentType())
        );
        registry.registerSystem(
                new BurnerBlockStateSystem(fuelBurnerComponentType)
        );

        Interaction.CODEC.register(
                "Hytech_OpenGeneratorPage",
                OpenGeneratorPageInteraction.class,
                OpenGeneratorPageInteraction.CODEC);
        Interaction.CODEC.register(
                "Hytech_OpenBatteryPage",
                OpenBatteryPageInteraction.class,
                OpenBatteryPageInteraction.CODEC);

        LOGGER.atInfo().log("Hytech Content Module initialized");
    }

    public static void init(@Nonnull ComponentRegistryProxy<ChunkStore> registry) {
        if (INSTANCE != null) throw new IllegalStateException("Already initialized");
        INSTANCE = new HytechContentModule(registry);
    }

    public static HytechContentModule get() {
        if (INSTANCE == null) throw new IllegalStateException("Not initialized");
        return INSTANCE;
    }

    public ComponentType<ChunkStore, EnergyGeneratorComponent> getGeneratorComponentType() {
        return generatorComponentType;
    }

    public ComponentType<ChunkStore, FuelBurnerComponent> getFuelBurnerComponentType() {
        return fuelBurnerComponentType;
    }
}
