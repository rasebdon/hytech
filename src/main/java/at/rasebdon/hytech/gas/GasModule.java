package at.rasebdon.hytech.gas;

import at.rasebdon.hytech.core.AbstractLogisticModule;
import at.rasebdon.hytech.core.networks.LogisticNetworkSystem;
import at.rasebdon.hytech.core.systems.LogisticTransferSystem;
import at.rasebdon.hytech.gas.components.GasBlockComponent;
import at.rasebdon.hytech.gas.components.GasPipeComponent;
import at.rasebdon.hytech.gas.networks.GasNetworkSystem;
import at.rasebdon.hytech.gas.systems.GasComponentRegistrationSystem;
import at.rasebdon.hytech.gas.systems.GasNetworkSaveSystem;
import at.rasebdon.hytech.gas.systems.GasTransferSystem;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public final class GasModule extends AbstractLogisticModule<
        GasBlockComponent,
        GasPipeComponent,
        GasComponentRegistrationSystem,
        HytechGasContainer
        > {

    private static GasModule INSTANCE;

    private GasModule(ComponentRegistryProxy<ChunkStore> registry, IEventRegistry eventRegistry) {
        super(
                registry,
                eventRegistry,
                GasBlockComponent.class,
                "hytech:gas:container",
                GasBlockComponent.CODEC,
                GasPipeComponent.class,
                "hytech:gas:pipe",
                GasPipeComponent.CODEC
        );
    }

    public static void init(ComponentRegistryProxy<ChunkStore> registry, IEventRegistry eventRegistry) {
        if (INSTANCE != null) throw new IllegalStateException("Already initialized");
        INSTANCE = new GasModule(registry, eventRegistry);
    }

    public static GasModule get() {
        if (INSTANCE == null) throw new IllegalStateException("Not initialized");
        return INSTANCE;
    }

    @Override
    protected void registerAdditionalSystems(ComponentRegistryProxy<ChunkStore> registry,
                                             IEventRegistry eventRegistry) {
        registry.registerSystem(new GasNetworkSaveSystem(getNetworkSystem()));
    }

    @Override
    protected String getModuleName() {
        return "Gas Module";
    }

    @Override
    protected LogisticNetworkSystem<HytechGasContainer> createNetworkSystem() {
        return new GasNetworkSystem();
    }

    @Override
    protected LogisticTransferSystem<HytechGasContainer> createTransferSystem(IEventRegistry eventRegistry) {
        return new GasTransferSystem(eventRegistry);
    }

    @Override
    protected GasComponentRegistrationSystem createContainerRegistrationSystem(
            ComponentType<ChunkStore, GasBlockComponent> blockType,
            ComponentType<ChunkStore, GasPipeComponent> pipeType,
            IEventRegistry eventRegistry,
            LogisticNetworkSystem<HytechGasContainer> networkSystem
    ) {
        return new GasComponentRegistrationSystem(blockType, pipeType, eventRegistry, networkSystem);
    }
}
