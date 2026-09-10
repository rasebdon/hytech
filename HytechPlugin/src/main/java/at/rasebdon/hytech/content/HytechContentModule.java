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

/// Hytech's own blocks, registered on top of HytechCore's resource types.
///
/// These used to be part of `EnergyModule.registerAdditionalSystems`, which put a closed
/// `GeneratorType` enum and a battery's fill states inside the library. They are content: a
/// generator kind cannot be added without editing the enum, so a second mod could never have
/// contributed one, and a battery is only an `EnergyBlockComponent` with an asset around it.
///
/// The components keep the ids they were registered under -- `hytech:energy:generator` and
/// `hytech:energy:fuel_burner`. `registerComponent` keys persistence by that id rather than by the
/// class, so moving the classes into this plugin is invisible to existing worlds and to every item
/// JSON naming them.
///
/// Registration goes through *this* plugin's registries, while the components these systems read
/// (`EnergyBlockComponent`, `ItemBlockComponent`) were registered by the library through its own.
/// The manifest dependency on `Technic:HytechCore` is what makes that safe: the whole load order is
/// walked in order, so the library is set up and started before this runs and `EnergyModule.get()`
/// cannot be premature.
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
                        // A burner reads its fuel from an item container the pipes can fill, which
                        // is why the library initialises items before energy.
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
