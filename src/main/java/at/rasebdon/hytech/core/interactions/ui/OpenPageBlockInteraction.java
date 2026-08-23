package at.rasebdon.hytech.core.interactions.ui;

import at.rasebdon.hytech.core.ui.HytechCustomPage;
import at.rasebdon.hytech.core.ui.HytechPages;
import at.rasebdon.hytech.core.util.HytechUtil;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;

import javax.annotation.Nonnull;

/// Base for interactions that open a Hytech page on a block.
///
/// Subclasses only decide *which* page. Opening it -- including opening it with an inventory window
/// when the page has a container -- is [HytechPages]' job, so every machine that shows item slots
/// gets the player's inventory alongside without asking for it.
public abstract class OpenPageBlockInteraction extends SimpleBlockInteraction {

    @Nonnull
    public static final BuilderCodec<OpenPageBlockInteraction> CODEC =
            BuilderCodec.abstractBuilder(OpenPageBlockInteraction.class, SimpleBlockInteraction.CODEC)
                    .build();

    @Override
    protected void interactWithBlock(
            @NotNull World world,
            @NotNull CommandBuffer<EntityStore> commandBuffer,
            @NotNull InteractionType type,
            @NotNull InteractionContext context,
            @Nullable ItemStack item,
            @NotNull Vector3i blockPos,
            @NotNull CooldownHandler cooldownHandler) {
        world.execute(() -> openPage(context, world, blockPos));
    }

    @Override
    protected void simulateInteractWithBlock(
            @NotNull InteractionType type,
            @NotNull InteractionContext context,
            @Nullable ItemStack item,
            @NotNull World world,
            @NotNull Vector3i blockPos) {
        world.execute(() -> openPage(context, world, blockPos));
    }

    private void openPage(@NotNull InteractionContext context,
                          @NotNull World world,
                          @NotNull Vector3i blockPos) {

        var store = world.getEntityStore().getStore();
        var entityRef = context.getEntity();

        var playerRef = store.getComponent(entityRef, PlayerRef.getComponentType());
        if (playerRef == null) return;

        // A machine whose primary screen is its container -- the burner and its fuel slot --
        // opens that instead of a custom page. It has to be one or the other: a window switches
        // the client to the Bench page, which is what carries the player's inventory, and a
        // custom page replaces that screen rather than sitting over it.
        var container = primaryContainer(world, blockPos,
                HytechUtil.isCrouching(world, entityRef));

        if (container != null) {
            openContainer(store, entityRef, container);
            return;
        }

        var page = createPage(world, blockPos, playerRef);
        if (page == null) return;

        HytechPages.open(store, entityRef, page);
    }

    /// The page to open, or null when this block has nothing to show.
    @Nullable
    protected abstract HytechCustomPage createPage(@NotNull World world,
                                                   @NotNull Vector3i blockPos,
                                                   @NotNull PlayerRef playerRef);

    /// A container to open *instead of* the page, or null to open the page.
    ///
    /// `crouching` is the escape hatch: a machine that normally opens its container still needs
    /// a way to reach its readouts and side configuration, so it returns null when crouching.
    @Nullable
    protected ItemContainer primaryContainer(@NotNull World world,
                                             @NotNull Vector3i blockPos,
                                             boolean crouching) {
        return null;
    }

    private void openContainer(@NotNull Store<EntityStore> store,
                               @NotNull Ref<EntityStore> entityRef,
                               @NotNull ItemContainer container) {
        var player = store.getComponent(entityRef, Player.getComponentType());
        if (player == null) return;

        var pageManager = player.getPageManager();
        if (pageManager == null) return;

        pageManager.setPageWithWindows(entityRef, store, Page.Bench, true,
                new ContainerWindow(container));
    }

    /* ---------------- Formatting shared by machine pages ---------------- */

    /// Signs a rate so a readout distinguishes gaining from losing at a glance.
    protected static String signed(long value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    protected static int percent(float ratio) {
        return Math.round(Math.max(0f, Math.min(1f, ratio)) * 100f);
    }
}
