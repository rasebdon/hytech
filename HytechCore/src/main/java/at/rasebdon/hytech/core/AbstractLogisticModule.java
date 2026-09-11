package at.rasebdon.hytech.core;

import at.rasebdon.hytech.core.components.LogisticBlockComponent;
import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.containers.LogisticContainer;
import at.rasebdon.hytech.core.networks.LogisticNetworkSystem;
import at.rasebdon.hytech.core.systems.LogisticComponentRegistrationSystem;
import at.rasebdon.hytech.core.systems.LogisticTransferSystem;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;

public abstract class AbstractLogisticModule<
        TBlockComponent extends LogisticBlockComponent<TContainer>,
        TPipeComponent extends LogisticPipeComponent<TContainer>,
        TRegistrationSystem extends LogisticComponentRegistrationSystem<TContainer>,
        TContainer extends LogisticContainer
        > {
    protected final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    protected final ComponentType<ChunkStore, TBlockComponent> blockComponentType;
    protected final ComponentType<ChunkStore, TPipeComponent> pipeComponentType;

    protected final LogisticNetworkSystem<TContainer> networkSystem;
    protected LogisticResourceType resourceType;
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

        networkSystem = createNetworkSystem();

        // One registration for both components so they can't drift apart; lets the wrench/side UI
        // name the resource instead of guessing a component type.
        this.resourceType = new LogisticResourceType(
                getResourceId(), getResourceLabel(), getResourceAccent(),
                blockComponentType, pipeComponentType);
        HytechCoreModule.get().registerResourceType(this.resourceType);

        registry.registerSystem(createTransferSystem(eventRegistry));
        registrationSystem = createContainerRegistrationSystem(
                blockComponentType,
                pipeComponentType,
                eventRegistry,
                networkSystem
        );
        registry.registerSystem(registrationSystem);

        registerAdditionalSystems(registry, eventRegistry);

        LOGGER.atInfo().log("%s initialized", getModuleName());
    }

    protected abstract String getModuleName();

    /// Stable id for this resource, matching its component ids -- `energy`, `items`, `fluid`, …
    protected abstract String getResourceId();

    /// Player-facing name, shown by the wrench and the side-configuration UI.
    protected abstract String getResourceLabel();

    /// Not abstract: falls back to [LogisticResourceType#DEFAULT_ACCENT] if unset.
    protected String getResourceAccent() {
        return LogisticResourceType.DEFAULT_ACCENT;
    }

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

    /// No caller in this repo, but it's the pipe half of the pair content mods reach for.
    @SuppressWarnings("unused")
    public ComponentType<ChunkStore, TPipeComponent> getPipeComponentType() {
        return pipeComponentType;
    }

    public LogisticNetworkSystem<TContainer> getNetworkSystem() {
        return networkSystem;
    }

    public LogisticResourceType getResourceType() {
        return resourceType;
    }
}
