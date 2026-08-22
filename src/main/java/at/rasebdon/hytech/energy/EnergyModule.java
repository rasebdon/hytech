package at.rasebdon.hytech.energy;

import at.rasebdon.hytech.core.AbstractLogisticModule;
import at.rasebdon.hytech.core.networks.LogisticNetworkSystem;
import at.rasebdon.hytech.core.systems.LogisticTransferSystem;
import at.rasebdon.hytech.energy.components.EnergyBlockComponent;
import at.rasebdon.hytech.energy.components.EnergyGeneratorComponent;
import at.rasebdon.hytech.energy.components.FuelBurnerComponent;
import at.rasebdon.hytech.items.ItemModule;
import at.rasebdon.hytech.energy.components.EnergyPipeComponent;
import at.rasebdon.hytech.energy.interaction.ui.OpenBatteryPageInteraction;
import at.rasebdon.hytech.energy.interaction.ui.OpenGeneratorPageInteraction;
import at.rasebdon.hytech.energy.networks.EnergyNetworkSystem;
import at.rasebdon.hytech.energy.systems.EnergyComponentRegistrationSystem;
import at.rasebdon.hytech.energy.systems.EnergyGenerationSystem;
import at.rasebdon.hytech.energy.systems.EnergyNetworkSaveSystem;
import at.rasebdon.hytech.energy.systems.EnergyTransferSystem;
import at.rasebdon.hytech.energy.systems.visual.BurnerBlockStateSystem;
import at.rasebdon.hytech.energy.systems.visual.EnergyBlockStateSystem;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public final class EnergyModule extends AbstractLogisticModule<
        EnergyBlockComponent,
        EnergyPipeComponent,
        EnergyComponentRegistrationSystem,
        HytechEnergyContainer
        > {

    private static EnergyModule INSTANCE;

    private ComponentType<ChunkStore, EnergyGeneratorComponent> generatorComponentType;
    private ComponentType<ChunkStore, FuelBurnerComponent> fuelBurnerComponentType;

    private EnergyModule(
            ComponentRegistryProxy<ChunkStore> registry,
            IEventRegistry eventRegistry
    ) {
        super(
                registry,
                eventRegistry,
                EnergyBlockComponent.class,
                "hytech:energy:container",
                EnergyBlockComponent.CODEC,
                EnergyPipeComponent.class,
                "hytech:energy:pipe",
                EnergyPipeComponent.CODEC
        );
    }

    public static void init(ComponentRegistryProxy<ChunkStore> registry, IEventRegistry eventRegistry) {
        if (INSTANCE != null) throw new IllegalStateException("Already initialized");
        INSTANCE = new EnergyModule(registry, eventRegistry);
    }

    public static EnergyModule get() {
        if (INSTANCE == null) throw new IllegalStateException("Not initialized");
        return INSTANCE;
    }

    @Override
    protected void registerAdditionalSystems(ComponentRegistryProxy<ChunkStore> registry, IEventRegistry eventRegistry) {
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
                        getBlockComponentType(),
                        fuelBurnerComponentType,
                        // Energy initialises after items precisely so this is available: a
                        // burner reads its fuel from an item container the pipes can fill.
                        ItemModule.get().getBlockComponentType())
        );
        registry.registerSystem(
                new EnergyNetworkSaveSystem(getNetworkSystem())
        );
        registry.registerSystem(
                new EnergyBlockStateSystem(blockComponentType)
        );
        registry.registerSystem(
                new BurnerBlockStateSystem(fuelBurnerComponentType)
        );

        Interaction.CODEC.register(
                "OpenGeneratorPage",
                OpenGeneratorPageInteraction.class,
                OpenGeneratorPageInteraction.CODEC);
        Interaction.CODEC.register(
                "OpenBatteryPage",
                OpenBatteryPageInteraction.class,
                OpenBatteryPageInteraction.CODEC);
    }

    @Override
    protected String getResourceId() {
        return "energy";
    }

    @Override
    protected String getResourceLabel() {
        return "Energy";
    }

    @Override
    protected String getModuleName() {
        return "Energy Module";
    }

    @Override
    protected LogisticNetworkSystem<HytechEnergyContainer> createNetworkSystem() {
        return new EnergyNetworkSystem();
    }

    @Override
    protected LogisticTransferSystem<HytechEnergyContainer> createTransferSystem(IEventRegistry eventRegistry) {
        return new EnergyTransferSystem(eventRegistry);
    }

    @Override
    protected EnergyComponentRegistrationSystem createContainerRegistrationSystem(
            ComponentType<ChunkStore, EnergyBlockComponent> blockType,
            ComponentType<ChunkStore, EnergyPipeComponent> pipeType,
            IEventRegistry eventRegistry,
            LogisticNetworkSystem<HytechEnergyContainer> networkSystem
    ) {
        return new EnergyComponentRegistrationSystem(
                blockType,
                pipeType,
                eventRegistry,
                networkSystem
        );
    }

    public ComponentType<ChunkStore, EnergyGeneratorComponent> getGeneratorComponentType() {
        return generatorComponentType;
    }

    public ComponentType<ChunkStore, FuelBurnerComponent> getFuelBurnerComponentType() {
        return fuelBurnerComponentType;
    }
}
