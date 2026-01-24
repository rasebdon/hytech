package com.rasebdon.hytech.energy;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.energy.components.BlockEnergyContainerComponent;
import com.rasebdon.hytech.energy.components.EnergyGeneratorComponent;
import com.rasebdon.hytech.energy.events.EnergyContainerChangedEvent;
import com.rasebdon.hytech.energy.interaction.ReadEnergyContainerBlockInteraction;
import com.rasebdon.hytech.energy.interaction.WrenchBlockInteraction;
import com.rasebdon.hytech.energy.systems.EnergyContainerRegistrationSystem;
import com.rasebdon.hytech.energy.systems.EnergyGenerationSystem;
import com.rasebdon.hytech.energy.systems.EnergyTransferSystem;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class EnergyModule {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Nullable
    private static EnergyModule INSTANCE;

    private final ComponentType<ChunkStore, BlockEnergyContainerComponent> blockEnergyContainerComponentType;

    private EnergyModule(@Nonnull ComponentRegistryProxy<ChunkStore> chunkStoreRegistry, @Nonnull IEventRegistry eventRegistry) {
        blockEnergyContainerComponentType = chunkStoreRegistry.registerComponent(
                BlockEnergyContainerComponent.class,
                "hytech:energy:container",
                BlockEnergyContainerComponent.CODEC);

        ComponentType<ChunkStore, EnergyGeneratorComponent> energyGeneratorType = chunkStoreRegistry.registerComponent(
                EnergyGeneratorComponent.class, "hytech:energy:generator", EnergyGeneratorComponent.CODEC);

        chunkStoreRegistry.registerSystem(new EnergyTransferSystem(eventRegistry, EnergyContainerChangedEvent.class));
        chunkStoreRegistry.registerSystem(new EnergyContainerRegistrationSystem(blockEnergyContainerComponentType));
        chunkStoreRegistry.registerSystem(new EnergyGenerationSystem(energyGeneratorType, blockEnergyContainerComponentType));

        Interaction.CODEC.register(
                "ReadEnergyContainer",
                ReadEnergyContainerBlockInteraction.class,
                ReadEnergyContainerBlockInteraction.CODEC);
        Interaction.CODEC.register(
                "Wrench",
                WrenchBlockInteraction.class,
                WrenchBlockInteraction.CODEC);

        LOGGER.atInfo().log("Energy Module Systems Registered");
    }

    public ComponentType<ChunkStore, BlockEnergyContainerComponent> getBlockEnergyContainerComponentType() {
        return this.blockEnergyContainerComponentType;
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
}
