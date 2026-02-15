package at.rasebdon.hytech.core.interactions.ui;

import at.rasebdon.hytech.core.util.HytechUtil;
import au.ellie.hyui.builders.PageBuilder;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionType;
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
        if (pageBuilder != null) {
            pageBuilder.open(playerRef, entityStore);
        }
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
}
