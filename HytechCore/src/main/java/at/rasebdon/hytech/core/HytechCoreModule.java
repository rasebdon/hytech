package at.rasebdon.hytech.core;

import at.rasebdon.hytech.core.components.CreativeSourceComponent;
import at.rasebdon.hytech.core.components.LogisticBlockComponent;
import at.rasebdon.hytech.core.components.LogisticEntityProxyComponent;
import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.components.WrenchModeComponent;
import at.rasebdon.hytech.core.interactions.ReadLogisticContainerInteraction;
import at.rasebdon.hytech.core.interactions.ui.OpenLogisticContainerPageInteraction;
import at.rasebdon.hytech.core.systems.CreativeSourceSystem;
import at.rasebdon.hytech.core.ui.PageRefreshSystem;
import at.rasebdon.hytech.core.interactions.WrenchInteraction;
import at.rasebdon.hytech.core.systems.FaceConfigOverlaySystem;
import at.rasebdon.hytech.core.systems.PipeConnectionStateSystem;
import at.rasebdon.hytech.core.systems.PipeMarkerCleanupSystem;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HytechCoreModule {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /// Order must be stable: a block with more than one container (the burner has energy and
    /// items) is otherwise configured arbitrarily by the wrench/side UI.
    private final List<LogisticResourceType> resourceTypes = new ArrayList<>();

    @Nullable
    private static HytechCoreModule INSTANCE;

    /// Shared across modules: the component registry allows only one system per class.
    private final ComponentType<EntityStore, LogisticEntityProxyComponent> logisticEntityProxyComponentType;
    private final ComponentType<EntityStore, WrenchModeComponent> wrenchModeComponentType;
    private final PipeConnectionStateSystem pipeConnectionStateSystem;
    private final PipeMarkerCleanupSystem pipeMarkerCleanupSystem;

    public HytechCoreModule(
            ComponentRegistryProxy<EntityStore> entityStoreComponentRegistry,
            ComponentRegistryProxy<ChunkStore> chunkStoreComponentRegistry) {
        this.logisticEntityProxyComponentType = entityStoreComponentRegistry.registerComponent(
                LogisticEntityProxyComponent.class,
                "hytech:core:logistic_entity_proxy",
                LogisticEntityProxyComponent.CODEC);

        var creativeSourceComponentType = chunkStoreComponentRegistry.registerComponent(
                CreativeSourceComponent.class,
                "hytech:core:creative_source",
                CreativeSourceComponent.CODEC);
        chunkStoreComponentRegistry.registerSystem(
                new CreativeSourceSystem(creativeSourceComponentType));

        this.pipeConnectionStateSystem = new PipeConnectionStateSystem();
        this.pipeMarkerCleanupSystem = new PipeMarkerCleanupSystem(this.pipeConnectionStateSystem);
        chunkStoreComponentRegistry.registerSystem(this.pipeConnectionStateSystem);
        chunkStoreComponentRegistry.registerSystem(this.pipeMarkerCleanupSystem);

        this.wrenchModeComponentType = entityStoreComponentRegistry.registerComponent(
                WrenchModeComponent.class,
                "hytech:core:wrench_mode",
                WrenchModeComponent.CODEC);

        entityStoreComponentRegistry.registerSystem(new FaceConfigOverlaySystem());
        // Refreshing custom-UI pages is ours to drive now; HyUI used to do this internally.
        entityStoreComponentRegistry.registerSystem(new PageRefreshSystem());

        // `Interaction.CODEC` is shared with vanilla and every plugin, so ids are prefixed --
        // a bare "Wrench" would collide with any other tech mod's.
        Interaction.CODEC.register(
                "Hytech_Wrench",
                WrenchInteraction.class,
                WrenchInteraction.CODEC);

        Interaction.CODEC.register(
                "Hytech_OpenLogisticContainer",
                OpenLogisticContainerPageInteraction.class,
                OpenLogisticContainerPageInteraction.CODEC);

        Interaction.CODEC.register(
                "Hytech_ReadLogisticContainer",
                ReadLogisticContainerInteraction.class,
                ReadLogisticContainerInteraction.CODEC);

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

    public ComponentType<EntityStore, LogisticEntityProxyComponent> getLogisticEntityProxyComponentType() {
        return this.logisticEntityProxyComponentType;
    }

    /// Called once per module from [AbstractLogisticModule], fixing registration order.
    public void registerResourceType(@Nonnull LogisticResourceType resourceType) {
        this.resourceTypes.add(resourceType);
        this.pipeConnectionStateSystem.registerPipeType(resourceType.pipeType());
        this.pipeMarkerCleanupSystem.registerPipeType(resourceType.pipeType());
    }

    @Nonnull
    public ComponentType<EntityStore, WrenchModeComponent> getWrenchModeComponentType() {
        return this.wrenchModeComponentType;
    }

    @Nonnull
    public List<LogisticResourceType> getResourceTypes() {
        return Collections.unmodifiableList(this.resourceTypes);
    }

    @Nullable
    public LogisticResourceType getResourceType(@Nonnull String id) {
        for (var resourceType : this.resourceTypes) {
            if (resourceType.id().equals(id)) {
                return resourceType;
            }
        }

        return null;
    }

    @Nonnull
    public List<ComponentType<ChunkStore, ? extends LogisticBlockComponent<?>>> getBlockComponents() {
        // Explicit type argument needed: without it, the wildcard return of blockType() infers to
        // a capture, giving a List<capture> that isn't assignable to this signature.
        return this.resourceTypes.stream()
                .<ComponentType<ChunkStore, ? extends LogisticBlockComponent<?>>>map(
                        LogisticResourceType::blockType)
                .toList();
    }

    @Nonnull
    public List<ComponentType<ChunkStore, ? extends LogisticPipeComponent<?>>> getPipeComponents() {
        return this.resourceTypes.stream()
                .<ComponentType<ChunkStore, ? extends LogisticPipeComponent<?>>>map(
                        LogisticResourceType::pipeType)
                .toList();
    }

    @Nonnull
    public static HytechCoreModule get() {
        if (INSTANCE == null) {
            throw new IllegalStateException("Hytech Core Module not initialized.");
        } else {
            return INSTANCE;
        }
    }
}
