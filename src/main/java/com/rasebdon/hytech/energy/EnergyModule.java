package com.rasebdon.hytech.energy;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.energy.components.EnergyGeneratorComponent;
import com.rasebdon.hytech.energy.components.SingleBlockEnergyContainerComponent;
import com.rasebdon.hytech.energy.generator.EnergyGenerationSystem;
import com.rasebdon.hytech.energy.interaction.ReadEnergyContainerBlockInteraction;
import com.rasebdon.hytech.energy.interaction.WrenchBlockInteraction;
import com.rasebdon.hytech.energy.systems.EnergyContainerRefSystem;
import com.rasebdon.hytech.energy.systems.EnergyContainerTransferSystem;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

// Thanks to notnotnotswipez for supporting on the official Hytale Discord

public class EnergyModule {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Nullable
    private static EnergyModule INSTANCE;

    private final ComponentType<ChunkStore, SingleBlockEnergyContainerComponent> singleBlockEnergyContainerComponentType;

    private EnergyModule(@Nonnull ComponentRegistryProxy<ChunkStore> registry) {
        singleBlockEnergyContainerComponentType = registry.registerComponent(
                SingleBlockEnergyContainerComponent.class,
                "hytech:energy:single_block",
                SingleBlockEnergyContainerComponent.CODEC);

        ComponentType<ChunkStore, EnergyGeneratorComponent> energyGeneratorType = registry.registerComponent(
                EnergyGeneratorComponent.class, "hytech:energy:generator", EnergyGeneratorComponent.CODEC);

        registry.registerSystem(new EnergyContainerRefSystem(singleBlockEnergyContainerComponentType));
        registry.registerSystem(new EnergyContainerTransferSystem(singleBlockEnergyContainerComponentType));
        registry.registerSystem(new EnergyGenerationSystem(energyGeneratorType, singleBlockEnergyContainerComponentType));

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

    public ComponentType<ChunkStore, SingleBlockEnergyContainerComponent> getSingleBlockEnergyContainerComponentType() {
        return this.singleBlockEnergyContainerComponentType;
    }

    public static void init(@Nonnull ComponentRegistryProxy<ChunkStore> registry) {
        if (INSTANCE != null) {
            throw new IllegalStateException("Energy Module already initialized.");
        } else {
            INSTANCE = new EnergyModule(registry);
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
