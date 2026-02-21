package at.rasebdon.hytech.core;

import at.rasebdon.hytech.core.components.LogisticComponent;
import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.networks.LogisticNetworkSystem;
import at.rasebdon.hytech.core.systems.LogisticComponentRegistrationSystem;
import at.rasebdon.hytech.core.systems.LogisticTransferSystem;
import at.rasebdon.hytech.core.systems.PipeRenderModule;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;

public abstract class AbstractLogisticModule<
        TBlockComponent extends LogisticComponent<TContainer>,
        TPipeComponent extends LogisticPipeComponent<TContainer>,
        TRegistrationSystem extends LogisticComponentRegistrationSystem<TContainer>,
        TContainer
        > {

    protected final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    protected final ComponentType<ChunkStore, TBlockComponent> blockComponentType;
    protected final ComponentType<ChunkStore, TPipeComponent> pipeComponentType;

    protected final LogisticNetworkSystem<TContainer> networkSystem;
    protected final TRegistrationSystem registrationSystem;

    protected AbstractLogisticModule(
            @Nonnull ComponentRegistryProxy<ChunkStore> registry,
            @Nonnull IEventRegistry eventRegistry,
            @Nonnull Class<TBlockComponent> blockClass,
            @Nonnull String blockId,
            @Nonnull BuilderCodec<TBlockComponent> blockCodec,
            @Nonnull Class<TPipeComponent> pipeClass,
            @Nonnull String pipeId,
            @Nonnull BuilderCodec<TPipeComponent> pipeCodec
    ) {

        // Register components
        blockComponentType = registry.registerComponent(
                blockClass,
                blockId,
                blockCodec
        );

        pipeComponentType = registry.registerComponent(
                pipeClass,
                pipeId,
                pipeCodec
        );

        // Create network system
        networkSystem = createNetworkSystem();

        // Register pipe rendering
        PipeRenderModule.registerPipe(pipeComponentType);

        // Register core systems
        registry.registerSystem(createTransferSystem(eventRegistry));
        registrationSystem = createContainerRegistrationSystem(
                blockComponentType,
                pipeComponentType,
                eventRegistry,
                networkSystem
        );
        registry.registerSystem(registrationSystem);

        // Allow subclasses to register additional systems
        registerAdditionalSystems(registry, eventRegistry);

        LOGGER.atInfo().log("%s initialized", getModuleName());
    }

    protected abstract String getModuleName();

    protected abstract LogisticNetworkSystem<TContainer> createNetworkSystem();

    protected abstract LogisticTransferSystem<TContainer> createTransferSystem(IEventRegistry eventRegistry);

    protected abstract TRegistrationSystem createContainerRegistrationSystem(
            ComponentType<ChunkStore, TBlockComponent> blockType,
            ComponentType<ChunkStore, TPipeComponent> pipeType,
            IEventRegistry eventRegistry,
            LogisticNetworkSystem<TContainer> networkSystem
    );

    protected void registerAdditionalSystems(
            ComponentRegistryProxy<ChunkStore> registry,
            IEventRegistry eventRegistry
    ) {
    }

    public ComponentType<ChunkStore, TBlockComponent> getBlockComponentType() {
        return blockComponentType;
    }

    public ComponentType<ChunkStore, TPipeComponent> getPipeComponentType() {
        return pipeComponentType;
    }

    public LogisticNetworkSystem<TContainer> getNetworkSystem() {
        return networkSystem;
    }
}
