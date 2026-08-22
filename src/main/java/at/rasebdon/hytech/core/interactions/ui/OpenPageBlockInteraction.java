package at.rasebdon.hytech.core.interactions.ui;

import at.rasebdon.hytech.core.util.HytechUtil;
import au.ellie.hyui.builders.HyUIPage;
import au.ellie.hyui.builders.PageBuilder;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.MessageUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;

import javax.annotation.Nonnull;

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
        world.execute(() -> openUiInternal(context, world, blockPos));
    }

    @Override
    protected void simulateInteractWithBlock(
            @NotNull InteractionType type,
            @NotNull InteractionContext context,
            @Nullable ItemStack item,
            @NotNull World world,
            @NotNull Vector3i blockPos) {
        world.execute(() -> openUiInternal(context, world, blockPos));
    }

    private void openUiInternal(@NotNull InteractionContext context,
                                @NotNull World world,
                                @NotNull Vector3i blockPos) {
        var entityStore = world.getEntityStore().getStore();
        var playerRef = entityStore.getComponent(context.getEntity(), PlayerRef.getComponentType());
        assert playerRef != null;

        var pageBuilder = getPageBuilder(context, world, blockPos);
        if (pageBuilder == null) return;

        wireSideConfig(pageBuilder, context, world, blockPos, playerRef, entityStore);

        pageBuilder.open(playerRef, entityStore);
    }

    /// Gives every machine page the side configurator, without each page having to know about it.
    ///
    /// The button lives in the shared page chrome, so a new machine UI gets per-resource side
    /// configuration for free as long as its HTML includes the button. Blocks with no logistic
    /// container at all get nothing wired, and the configurator lists only the resources the
    /// block actually carries -- so a battery shows one row and the burner shows two.
    private void wireSideConfig(@NotNull PageBuilder pageBuilder,
                                @NotNull InteractionContext context,
                                @NotNull World world,
                                @NotNull Vector3i blockPos,
                                @NotNull PlayerRef playerRef,
                                @NotNull Store<EntityStore> entityStore) {

        if (SideConfigPage.presentResources(world, blockPos).isEmpty()) return;

        pageBuilder.addEventListener(SideConfigPage.OPEN_BUTTON_ID,
                CustomUIEventBindingType.Activating, (_, ctx) -> {
                    var sideConfig = SideConfigPage.of(world, blockPos, getBlockName(world, blockPos));
                    if (sideConfig == null) return;

                    // Back reopens the machine page rather than leaving the player on nothing.
                    sideConfig.addEventListener(SideConfigPage.BACK_BUTTON_ID,
                            CustomUIEventBindingType.Activating,
                            (_, backCtx) -> {
                                backCtx.getPage().ifPresent(HyUIPage::close);
                                world.execute(() -> openUiInternal(context, world, blockPos));
                            });

                    ctx.getPage().ifPresent(HyUIPage::close);
                    sideConfig.open(playerRef, entityStore);
                });
    }

    @Nullable
    protected abstract PageBuilder getPageBuilder(@NotNull InteractionContext context,
                                                  @NotNull World world,
                                                  @NotNull Vector3i blockPos);


    protected String getBlockName(@NotNull World world, @NotNull Vector3i blockPos) {
        var blockType = HytechUtil.getBlockType(world, blockPos);
        assert blockType != null;

        var blockItem = blockType.getItem();
        assert blockItem != null;

        var translationKey = blockItem.getTranslationProperties().getName();
        assert translationKey != null;

        return MessageUtil.toAnsiString(Message.translation(translationKey)).toString();
    }

    protected String getPrefix(long value) {
        if (value < 0) {
            return "-";
        } else if (value > 0) {
            return "+";
        } else {
            return "";
        }
    }

    protected String getValueColor(long value) {
        if (value < 0) {
            return "#fc2e23";
        } else if (value > 0) {
            return "#23fc31";
        } else {
            return "#ffffff";
        }
    }
}
