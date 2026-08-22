package at.rasebdon.hytech.fluid;

import at.rasebdon.hytech.core.AbstractLogisticModule;
import at.rasebdon.hytech.core.networks.LogisticNetworkSystem;
import at.rasebdon.hytech.core.systems.LogisticTransferSystem;
import at.rasebdon.hytech.fluid.components.FluidBlockComponent;
import at.rasebdon.hytech.fluid.components.FluidPipeComponent;
import at.rasebdon.hytech.fluid.networks.FluidNetworkSystem;
import at.rasebdon.hytech.fluid.systems.FluidComponentRegistrationSystem;
import at.rasebdon.hytech.fluid.systems.FluidNetworkSaveSystem;
import at.rasebdon.hytech.fluid.systems.FluidTransferSystem;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public final class FluidModule extends AbstractLogisticModule<
        FluidBlockComponent,
        FluidPipeComponent,
        FluidComponentRegistrationSystem,
        HytechFluidContainer
        > {

    private static FluidModule INSTANCE;

    private FluidModule(ComponentRegistryProxy<ChunkStore> registry, IEventRegistry eventRegistry) {
        super(
                registry,
                eventRegistry,
                FluidBlockComponent.class,
                "hytech:fluid:container",
                FluidBlockComponent.CODEC,
                FluidPipeComponent.class,
                "hytech:fluid:pipe",
                FluidPipeComponent.CODEC
        );
    }

    public static void init(ComponentRegistryProxy<ChunkStore> registry, IEventRegistry eventRegistry) {
        if (INSTANCE != null) throw new IllegalStateException("Already initialized");
        INSTANCE = new FluidModule(registry, eventRegistry);
    }

    public static FluidModule get() {
        if (INSTANCE == null) throw new IllegalStateException("Not initialized");
        return INSTANCE;
    }

    @Override
    protected void registerAdditionalSystems(ComponentRegistryProxy<ChunkStore> registry,
                                             IEventRegistry eventRegistry) {
        registry.registerSystem(new FluidNetworkSaveSystem(getNetworkSystem()));
    }

    @Override
    protected String getResourceId() {
        return "fluid";
    }

    @Override
    protected String getResourceLabel() {
        return "Fluid";
    }

    @Override
    protected String getModuleName() {
        return "Fluid Module";
    }

    @Override
    protected LogisticNetworkSystem<HytechFluidContainer> createNetworkSystem() {
        return new FluidNetworkSystem();
    }

    @Override
    protected LogisticTransferSystem<HytechFluidContainer> createTransferSystem(IEventRegistry eventRegistry) {
        return new FluidTransferSystem(eventRegistry);
    }

    @Override
    protected FluidComponentRegistrationSystem createContainerRegistrationSystem(
            ComponentType<ChunkStore, FluidBlockComponent> blockType,
            ComponentType<ChunkStore, FluidPipeComponent> pipeType,
            IEventRegistry eventRegistry,
            LogisticNetworkSystem<HytechFluidContainer> networkSystem
    ) {
        return new FluidComponentRegistrationSystem(blockType, pipeType, eventRegistry, networkSystem);
    }
}
