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
        // A local, not a field: nothing outside this constructor needs the type, because the
        // system built from it is the only thing that reads the component.
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

        // Per-player wrench mode: which resource its face cycling applies to.
        this.wrenchModeComponentType = entityStoreComponentRegistry.registerComponent(
                WrenchModeComponent.class,
                "hytech:core:wrench_mode",
                WrenchModeComponent.CODEC);

        entityStoreComponentRegistry.registerSystem(new FaceConfigOverlaySystem());
        // Machine pages are built on Hytale's own custom-UI API now, so refreshing them is ours
        // to drive; HyUI used to do this internally.
        entityStoreComponentRegistry.registerSystem(new PageRefreshSystem());

        // `Interaction.CODEC` is one registry for the whole server, shared with vanilla and with
        // every other plugin, so these ids are prefixed. A bare "Wrench" is exactly the name a
        // second tech mod would reach for, and the loser of that collision is whichever plugin
        // loaded first -- a failure with no good diagnostic. The prefix follows the item-id
        // convention (`Hytech_Workbench`) rather than the component one, because this registry is
        // vanilla's and uses vanilla's spelling.
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
        // The explicit type argument is load bearing: `blockType()` is declared with a wildcard, so
        // without it the element type infers to a *capture* of that wildcard and the result is a
        // `List<capture>`, which is not assignable to the declared return type.
        return this.resourceTypes.stream()
                .<ComponentType<ChunkStore, ? extends LogisticBlockComponent<?>>>map(
                        LogisticResourceType::blockType)
                .toList();
    }

    /// Pipe component types of every registered resource type.
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
