package at.rasebdon.hytech.core;

import at.rasebdon.hytech.core.components.CreativeSourceComponent;
import at.rasebdon.hytech.core.components.LogisticBlockComponent;
import at.rasebdon.hytech.core.components.LogisticEntityProxyComponent;
import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.components.WrenchModeComponent;
import at.rasebdon.hytech.core.interactions.ReadLogisticContainerInteraction;
import at.rasebdon.hytech.core.interactions.ui.OpenLogisticContainerPageInteraction;
import at.rasebdon.hytech.core.systems.CreativeSourceSystem;
import at.rasebdon.hytech.core.systems.WrenchModeScrollSystem;
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

    /// Every registered resource type, in module registration order.
    ///
    /// A list rather than the two unordered sets this used to be. The wrench and the
    /// side-configuration UI both have to answer "which resource am I configuring", and order
    /// has to be stable for that: a block carrying more than one container -- the burner
    /// generator has both energy and items -- would otherwise be configured arbitrarily, and
    /// possibly differently after a restart.
    private final List<LogisticResourceType> resourceTypes = new ArrayList<>();

    @Nullable
    private static HytechCoreModule INSTANCE;

    /// One instance each, shared by every resource module: the component registry allows
    /// a single system per class, so these cannot be per-module.
    private final ComponentType<EntityStore, LogisticEntityProxyComponent> logisticEntityProxyComponentType;
    private final ComponentType<ChunkStore, CreativeSourceComponent> creativeSourceComponentType;
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

        // Registered in core rather than per module so one system covers every resource type.
        this.creativeSourceComponentType = chunkStoreComponentRegistry.registerComponent(
                CreativeSourceComponent.class,
                "hytech:core:creative_source",
                CreativeSourceComponent.CODEC);
        chunkStoreComponentRegistry.registerSystem(
                new CreativeSourceSystem(this.creativeSourceComponentType));

        this.pipeConnectionStateSystem = new PipeConnectionStateSystem();
        this.pipeMarkerCleanupSystem = new PipeMarkerCleanupSystem(this.pipeConnectionStateSystem);
        chunkStoreComponentRegistry.registerSystem(this.pipeConnectionStateSystem);
        chunkStoreComponentRegistry.registerSystem(this.pipeMarkerCleanupSystem);

        // Per-player wrench mode: which resource its face cycling applies to.
        this.wrenchModeComponentType = entityStoreComponentRegistry.registerComponent(
                WrenchModeComponent.class,
                "hytech:core:wrench_mode",
                WrenchModeComponent.CODEC);

        entityStoreComponentRegistry.registerSystem(new FaceConfigOverlaySystem());
        entityStoreComponentRegistry.registerSystem(
                new WrenchModeScrollSystem(this.wrenchModeComponentType));

        Interaction.CODEC.register(
                "Wrench",
                WrenchInteraction.class,
                WrenchInteraction.CODEC);

        Interaction.CODEC.register(
                "OpenLogisticContainer",
                OpenLogisticContainerPageInteraction.class,
                OpenLogisticContainerPageInteraction.CODEC);

        Interaction.CODEC.register(
                "ReadLogisticContainer",
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

    /// Opts a resource module's pipe component into the shared rendering systems.
    /// Registers a resource type and wires its pipes into the shared rendering systems.
    ///
    /// Called once per module from [AbstractLogisticModule], which is what fixes the ordering.
    public void registerResourceType(@Nonnull LogisticResourceType resourceType) {
        this.resourceTypes.add(resourceType);
        this.pipeConnectionStateSystem.registerPipeType(resourceType.pipeType());
        this.pipeMarkerCleanupSystem.registerPipeType(resourceType.pipeType());
    }

    @Nonnull
    public ComponentType<EntityStore, WrenchModeComponent> getWrenchModeComponentType() {
        return this.wrenchModeComponentType;
    }

    /// Every registered resource type, in registration order.
    @Nonnull
    public List<LogisticResourceType> getResourceTypes() {
        return Collections.unmodifiableList(this.resourceTypes);
    }

    /// The resource type with this id, or null if no module registered it.
    @Nullable
    public LogisticResourceType getResourceType(@Nonnull String id) {
        for (var resourceType : this.resourceTypes) {
            if (resourceType.id().equals(id)) {
                return resourceType;
            }
        }

        return null;
    }

    /// Block component types of every registered resource type.
    @Nonnull
    public List<ComponentType<ChunkStore, ? extends LogisticBlockComponent<?>>> getBlockComponents() {
        return this.resourceTypes.stream().map(LogisticResourceType::blockType).toList();
    }

    /// Pipe component types of every registered resource type.
    @Nonnull
    public List<ComponentType<ChunkStore, ? extends LogisticPipeComponent<?>>> getPipeComponents() {
        return this.resourceTypes.stream().map(LogisticResourceType::pipeType).toList();
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
