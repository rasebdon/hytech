package at.rasebdon.hytech.heat;

import at.rasebdon.hytech.core.AbstractLogisticModule;
import at.rasebdon.hytech.core.networks.LogisticNetworkSystem;
import at.rasebdon.hytech.core.systems.LogisticTransferSystem;
import at.rasebdon.hytech.heat.components.HeatBlockComponent;
import at.rasebdon.hytech.heat.components.HeatPipeComponent;
import at.rasebdon.hytech.heat.networks.HeatNetworkSystem;
import at.rasebdon.hytech.heat.systems.HeatComponentRegistrationSystem;
import at.rasebdon.hytech.heat.systems.HeatNetworkSaveSystem;
import at.rasebdon.hytech.heat.systems.HeatTransferSystem;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public final class HeatModule extends AbstractLogisticModule<
        HeatBlockComponent,
        HeatPipeComponent,
        HeatComponentRegistrationSystem,
        HytechHeatContainer
        > {

    private static HeatModule INSTANCE;

    private HeatModule(ComponentRegistryProxy<ChunkStore> registry, IEventRegistry eventRegistry) {
        super(
                registry,
                eventRegistry,
                HeatBlockComponent.class,
                "hytech:heat:container",
                HeatBlockComponent.CODEC,
                HeatPipeComponent.class,
                "hytech:heat:pipe",
                HeatPipeComponent.CODEC
        );
    }

    public static void init(ComponentRegistryProxy<ChunkStore> registry, IEventRegistry eventRegistry) {
        if (INSTANCE != null) throw new IllegalStateException("Already initialized");
        INSTANCE = new HeatModule(registry, eventRegistry);
    }

    public static HeatModule get() {
        if (INSTANCE == null) throw new IllegalStateException("Not initialized");
        return INSTANCE;
    }

    @Override
    protected void registerAdditionalSystems(ComponentRegistryProxy<ChunkStore> registry, IEventRegistry eventRegistry) {
        registry.registerSystem(new HeatNetworkSaveSystem(getNetworkSystem()));
    }

    @Override
    protected String getResourceId() {
        return "heat";
    }

    @Override
    protected String getResourceLabel() {
        return "Heat";
    }

    @Override
    protected String getModuleName() {
        return "Heat Module";
    }

    @Override
    protected LogisticNetworkSystem<HytechHeatContainer> createNetworkSystem() {
        return new HeatNetworkSystem();
    }

    @Override
    protected LogisticTransferSystem<HytechHeatContainer> createTransferSystem(IEventRegistry eventRegistry) {
        return new HeatTransferSystem(eventRegistry);
    }

    @Override
    protected HeatComponentRegistrationSystem createContainerRegistrationSystem(
            ComponentType<ChunkStore, HeatBlockComponent> blockType,
            ComponentType<ChunkStore, HeatPipeComponent> pipeType,
            IEventRegistry eventRegistry,
            LogisticNetworkSystem<HytechHeatContainer> networkSystem
    ) {
        return new HeatComponentRegistrationSystem(blockType, pipeType, eventRegistry, networkSystem);
    }
}
