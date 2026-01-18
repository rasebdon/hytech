package com.rasebdon.hytech.energy;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.PickBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.energy.container.EnergyContainerComponent;
import com.rasebdon.hytech.energy.container.EnergyContainerTransferSystem;
import com.rasebdon.hytech.energy.generator.EnergyGenerationSystem;
import com.rasebdon.hytech.energy.generator.EnergyGeneratorComponent;
import com.rasebdon.hytech.energy.multimeter.ReadEnergyContainerBlockInteraction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

// Thanks to notnotnotswipez for supporting on the official Hytale Discord

public class EnergyModule
{
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    @Nullable
    private static EnergyModule INSTANCE;

    private final ComponentType<ChunkStore, EnergyContainerComponent> energyContainerComponentType;
    public ComponentType<ChunkStore, EnergyContainerComponent> getEnergyContainerComponentType() {
        return this.energyContainerComponentType;
    }

    private EnergyModule(@Nonnull ComponentRegistryProxy<ChunkStore> registry) {
        energyContainerComponentType = registry.registerComponent(
                EnergyContainerComponent.class, "hytech:energy:container", EnergyContainerComponent.CODEC);

        ComponentType<ChunkStore, EnergyGeneratorComponent> energyGeneratorType = registry.registerComponent(
                EnergyGeneratorComponent.class, "hytech:energy:generator", EnergyGeneratorComponent.CODEC);

        registry.registerSystem(new EnergyContainerTransferSystem(energyContainerComponentType));
        registry.registerSystem(new EnergyGenerationSystem(energyGeneratorType, energyContainerComponentType));

        Interaction.CODEC.register(
                "ReadEnergyContainer",
                ReadEnergyContainerBlockInteraction.class,
                ReadEnergyContainerBlockInteraction.CODEC);

        LOGGER.atInfo().log("Energy Module Systems Registered");
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

    public static void reset() {
        INSTANCE = null;
    }
}
