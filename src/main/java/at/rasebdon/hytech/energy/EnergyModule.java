package at.rasebdon.hytech.energy;

import at.rasebdon.hytech.core.systems.PipeRenderModule;
import at.rasebdon.hytech.energy.components.EnergyBlockComponent;
import at.rasebdon.hytech.energy.components.EnergyGeneratorComponent;
import at.rasebdon.hytech.energy.components.EnergyPipeComponent;
import at.rasebdon.hytech.energy.interaction.BatteryPageInteraction;
import at.rasebdon.hytech.energy.interaction.ReadEnergyContainerBlockInteraction;
import at.rasebdon.hytech.energy.interaction.SolarPanelPageInteraction;
import at.rasebdon.hytech.energy.interaction.WrenchInteraction;
import at.rasebdon.hytech.energy.networks.EnergyNetworkSystem;
import at.rasebdon.hytech.energy.systems.EnergyContainerRegistrationSystem;
import at.rasebdon.hytech.energy.systems.EnergyGenerationSystem;
import at.rasebdon.hytech.energy.systems.EnergyNetworkSaveSystem;
import at.rasebdon.hytech.energy.systems.EnergyTransferSystem;
import at.rasebdon.hytech.energy.systems.visual.EnergyBlockStateSystem;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class EnergyModule {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Nullable
    private static EnergyModule INSTANCE;

    private final ComponentType<ChunkStore, EnergyBlockComponent> blockEnergyContainerComponentType;
    private final ComponentType<ChunkStore, EnergyPipeComponent> energyPipeComponentType;

    private EnergyModule(@Nonnull ComponentRegistryProxy<ChunkStore> chunkStoreRegistry, @Nonnull IEventRegistry eventRegistry) {
        blockEnergyContainerComponentType = chunkStoreRegistry.registerComponent(
                EnergyBlockComponent.class,
                "hytech:energy:container",
                EnergyBlockComponent.CODEC);
        energyPipeComponentType = chunkStoreRegistry.registerComponent(
                EnergyPipeComponent.class,
                "hytech:energy:pipe",
                EnergyPipeComponent.CODEC);

        var energyNetworkSystem = new EnergyNetworkSystem();

        PipeRenderModule.registerPipe(chunkStoreRegistry, energyPipeComponentType);

        chunkStoreRegistry.registerSystem(new EnergyTransferSystem(eventRegistry));
        chunkStoreRegistry.registerSystem(new EnergyContainerRegistrationSystem(
                blockEnergyContainerComponentType, energyPipeComponentType, eventRegistry, energyNetworkSystem));
        chunkStoreRegistry.registerSystem(new EnergyNetworkSaveSystem(energyNetworkSystem));
        chunkStoreRegistry.registerSystem(new EnergyBlockStateSystem(blockEnergyContainerComponentType));

        ComponentType<ChunkStore, EnergyGeneratorComponent> energyGeneratorType = chunkStoreRegistry.registerComponent(
                EnergyGeneratorComponent.class, "hytech:energy:generator", EnergyGeneratorComponent.CODEC);
        chunkStoreRegistry.registerSystem(new EnergyGenerationSystem(energyGeneratorType, blockEnergyContainerComponentType));

        Interaction.CODEC.register(
                "ReadEnergyContainer",
                ReadEnergyContainerBlockInteraction.class,
                ReadEnergyContainerBlockInteraction.CODEC);
        Interaction.CODEC.register(
                "Wrench",
                WrenchInteraction.class,
                WrenchInteraction.CODEC);
        Interaction.CODEC.register(
                "SolarPanelPage",
                SolarPanelPageInteraction.class,
                SolarPanelPageInteraction.CODEC);
        Interaction.CODEC.register(
                "BatteryPage",
                BatteryPageInteraction.class,
                BatteryPageInteraction.CODEC);

        LOGGER.atInfo().log("Energy Module Systems Registered");
    }

    public static void init(@Nonnull ComponentRegistryProxy<ChunkStore> chunkStoreRegistry, @Nonnull IEventRegistry eventRegistry) {
        if (INSTANCE != null) {
            throw new IllegalStateException("Energy Module already initialized.");
        } else {
            INSTANCE = new EnergyModule(chunkStoreRegistry, eventRegistry);
        }
    }

    @Nonnull
    public static EnergyModule get() {
        if (INSTANCE == null) {
            throw new IllegalStateException("Energy Module not initialized.");
        } else {
            return INSTANCE;
        }
    }

    public ComponentType<ChunkStore, EnergyBlockComponent> getBlockEnergyContainerComponentType() {
        return this.blockEnergyContainerComponentType;
    }

    public ComponentType<ChunkStore, EnergyPipeComponent> getEnergyPipeComponentType() {
        return energyPipeComponentType;
    }
}
