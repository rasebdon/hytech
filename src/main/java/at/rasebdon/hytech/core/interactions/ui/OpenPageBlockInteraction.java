package at.rasebdon.hytech.core.interactions.ui;

import at.rasebdon.hytech.core.interactions.WrenchInteraction;
import at.rasebdon.hytech.core.ui.HytechCustomPage;
import at.rasebdon.hytech.core.ui.HytechPages;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.protocol.InteractionType;
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

        // A block's own Use interaction runs instead of the held item's, so a machine has to
        // honour the wrench itself or the wrench would silently do nothing on it.
        if (WrenchInteraction.isWrench(item)) {
            var clientState = context.getClientState();
            if (clientState != null) {
                world.execute(() -> WrenchInteraction.configureTargetedFace(
                        clientState, world, context.getEntity(), blockPos));
            }
            return;
        }

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

        var page = createPage(world, blockPos, playerRef);
        if (page == null) return;

        HytechPages.open(store, entityRef, page);
    }

    /// The page to open, or null when this block has nothing to show.
    @Nullable
    protected abstract HytechCustomPage createPage(@NotNull World world,
                                                   @NotNull Vector3i blockPos,
                                                   @NotNull PlayerRef playerRef);

    /* ---------------- Formatting shared by machine pages ---------------- */

    /// Signs a rate so a readout distinguishes gaining from losing at a glance.
    protected static String signed(long value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    protected static int percent(float ratio) {
        return Math.round(Math.max(0f, Math.min(1f, ratio)) * 100f);
    }
}
