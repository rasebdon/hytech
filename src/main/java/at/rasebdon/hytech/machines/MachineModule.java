package at.rasebdon.hytech.machines;

import at.rasebdon.hytech.energy.EnergyModule;
import at.rasebdon.hytech.items.ItemModule;
import at.rasebdon.hytech.machines.components.MachineProcessorComponent;
import at.rasebdon.hytech.machines.interaction.ui.OpenMachinePageInteraction;
import at.rasebdon.hytech.machines.systems.MachineProcessingSystem;
import at.rasebdon.hytech.machines.systems.visual.MachineBlockStateSystem;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

/// Electric machines: blocks that turn items into other items.
///
/// Not an [at.rasebdon.hytech.core.AbstractLogisticModule], because a machine transports nothing --
/// it has no network, no pipes and no containers of its own. What it has is other modules':
/// `hytech:items:container` for the slots and `hytech:energy:container` for the buffer, which is
/// why this initialises after both.
public final class MachineModule {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static MachineModule INSTANCE;

    private final ComponentType<ChunkStore, MachineProcessorComponent> processorComponentType;

    private MachineModule(ComponentRegistryProxy<ChunkStore> registry) {
        this.processorComponentType = registry.registerComponent(
                MachineProcessorComponent.class,
                "hytech:machine:processor",
                MachineProcessorComponent.CODEC
        );

        registry.registerSystem(
                new MachineProcessingSystem(
                        this.processorComponentType,
                        ItemModule.get().getBlockComponentType(),
                        EnergyModule.get().getBlockComponentType())
        );
        registry.registerSystem(
                new MachineBlockStateSystem(this.processorComponentType)
        );

        Interaction.CODEC.register(
                "OpenMachinePage",
                OpenMachinePageInteraction.class,
                OpenMachinePageInteraction.CODEC);

        LOGGER.atInfo().log("Machine Module initialized");
    }

    public static void init(ComponentRegistryProxy<ChunkStore> registry) {
        if (INSTANCE != null) throw new IllegalStateException("Already initialized");
        INSTANCE = new MachineModule(registry);
    }

    public static MachineModule get() {
        if (INSTANCE == null) throw new IllegalStateException("Not initialized");
        return INSTANCE;
    }

    public ComponentType<ChunkStore, MachineProcessorComponent> getProcessorComponentType() {
        return this.processorComponentType;
    }
}
