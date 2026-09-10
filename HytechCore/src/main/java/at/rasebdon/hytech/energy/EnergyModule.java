package at.rasebdon.hytech.energy;

import at.rasebdon.hytech.core.AbstractLogisticModule;
import at.rasebdon.hytech.core.networks.LogisticNetworkSystem;
import at.rasebdon.hytech.core.systems.LogisticTransferSystem;
import at.rasebdon.hytech.energy.components.EnergyBlockComponent;
import at.rasebdon.hytech.energy.components.EnergyPipeComponent;
import at.rasebdon.hytech.energy.networks.EnergyNetworkSystem;
import at.rasebdon.hytech.energy.systems.EnergyComponentRegistrationSystem;
import at.rasebdon.hytech.energy.systems.EnergyNetworkSaveSystem;
import at.rasebdon.hytech.energy.systems.EnergyTransferSystem;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public final class EnergyModule extends AbstractLogisticModule<
        EnergyBlockComponent,
        EnergyPipeComponent,
        EnergyComponentRegistrationSystem,
        HytechEnergyContainer
        > {

    private static EnergyModule INSTANCE;

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
        registry.registerSystem(
                new EnergyNetworkSaveSystem(getNetworkSystem())
        );
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
    protected String getResourceAccent() {
        return "#e8a93b";
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
}
