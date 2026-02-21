package at.rasebdon.hytech.energy;

import at.rasebdon.hytech.core.AbstractLogisticModule;
import at.rasebdon.hytech.core.networks.LogisticNetworkSystem;
import at.rasebdon.hytech.core.systems.LogisticTransferSystem;
import at.rasebdon.hytech.energy.components.EnergyBlockComponent;
import at.rasebdon.hytech.energy.components.EnergyGeneratorComponent;
import at.rasebdon.hytech.energy.components.EnergyPipeComponent;
import at.rasebdon.hytech.energy.interaction.ReadEnergyContainerBlockInteraction;
import at.rasebdon.hytech.energy.interaction.WrenchInteraction;
import at.rasebdon.hytech.energy.interaction.ui.OpenBatteryPageInteraction;
import at.rasebdon.hytech.energy.interaction.ui.OpenGeneratorPageInteraction;
import at.rasebdon.hytech.energy.networks.EnergyNetworkSystem;
import at.rasebdon.hytech.energy.systems.EnergyComponentRegistrationSystem;
import at.rasebdon.hytech.energy.systems.EnergyGenerationSystem;
import at.rasebdon.hytech.energy.systems.EnergyNetworkSaveSystem;
import at.rasebdon.hytech.energy.systems.EnergyTransferSystem;
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

        registry.registerSystem(
                new EnergyGenerationSystem(generatorComponentType, getBlockComponentType())
        );
        registry.registerSystem(
                new EnergyNetworkSaveSystem(getNetworkSystem())
        );

        Interaction.CODEC.register(
                "ReadEnergyContainer",
                ReadEnergyContainerBlockInteraction.class,
                ReadEnergyContainerBlockInteraction.CODEC);
        Interaction.CODEC.register(
                "Wrench",
                WrenchInteraction.class,
                WrenchInteraction.CODEC);
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
}
