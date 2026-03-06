package at.rasebdon.hytech.core;

import at.rasebdon.hytech.core.components.LogisticBlockComponent;
import at.rasebdon.hytech.core.components.LogisticEntityProxyComponent;
import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.interactions.WrenchInteraction;
import at.rasebdon.hytech.core.systems.PipeRenderModule;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

public class HytechCoreModule {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public Set<ComponentType<ChunkStore, ? extends LogisticBlockComponent<?>>> blockComponents
            = new HashSet<>();
    public Set<ComponentType<ChunkStore, ? extends LogisticPipeComponent<?>>> pipeComponents
            = new HashSet<>();

    @Nullable
    private static HytechCoreModule INSTANCE;

    private final ComponentType<EntityStore, LogisticEntityProxyComponent> logisticEntityProxyComponentType;

    public HytechCoreModule(
            ComponentRegistryProxy<EntityStore> entityStoreComponentRegistry,
            ComponentRegistryProxy<ChunkStore> chunkStoreComponentRegistry) {
        this.logisticEntityProxyComponentType = entityStoreComponentRegistry.registerComponent(
                LogisticEntityProxyComponent.class,
                "hytech:core:logistic_entity_proxy",
                LogisticEntityProxyComponent.CODEC);

        PipeRenderModule.init(chunkStoreComponentRegistry);

        Interaction.CODEC.register(
                "Wrench",
                WrenchInteraction.class,
                WrenchInteraction.CODEC);

        LOGGER.atInfo().log("Hytech Core Module initialized");
    }

    public static void init(
            @Nonnull ComponentRegistryProxy<EntityStore> entityStoreComponentRegistry,
            @Nonnull ComponentRegistryProxy<ChunkStore> chunkStoreComponentRegistry) {
        if (INSTANCE != null) {
            throw new IllegalStateException("Hytech Core Module already initialized.");
        } else {
            INSTANCE = new HytechCoreModule(entityStoreComponentRegistry, chunkStoreComponentRegistry);
        }
    }

    @Nonnull
    public static HytechCoreModule get() {
        if (INSTANCE == null) {
            throw new IllegalStateException("Hytech Core Module not initialized.");
        } else {
            return INSTANCE;
        }
    }

    public ComponentType<EntityStore, LogisticEntityProxyComponent> getLogisticEntityProxyComponentType() {
        return this.logisticEntityProxyComponentType;
    }
}
