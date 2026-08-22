package at.rasebdon.hytech.core.interactions.ui;

import at.rasebdon.hytech.core.HytechCoreModule;
import at.rasebdon.hytech.core.LogisticResourceType;
import at.rasebdon.hytech.core.transport.BlockFaceConfigType;
import at.rasebdon.hytech.core.util.BlockFaceUtil;
import at.rasebdon.hytech.core.util.HytechUtil;
import au.ellie.hyui.builders.ButtonBuilder;
import au.ellie.hyui.builders.GroupBuilder;
import au.ellie.hyui.builders.ItemSlotBuilder;
import au.ellie.hyui.builders.LabelBuilder;
import au.ellie.hyui.builders.PageBuilder;
import au.ellie.hyui.events.UIContext;
import au.ellie.hyui.html.TemplateProcessor;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/// The side-configuration page shared by every logistic block's UI.
///
/// A machine can carry several containers, so its sides are configured *per resource*: the
/// burner generator outputs energy on a face while accepting items on the same one. A wrench
/// cannot express that without a mode, and even with one it is fiddly -- so each container gets
/// a row of six face buttons here, and only the resources the block actually has are shown.
///
/// Rows are built with HyUI builders rather than template slots because the set of rows depends
/// on the block: a battery shows one row, the burner shows two, and a static HTML page cannot
/// declare ids for rows that may not exist.
public final class SideConfigPage {

    /// Element ids the machine pages and this page agree on.
    public static final String OPEN_BUTTON_ID = "side-config-button";
    public static final String BACK_BUTTON_ID = "side-config-back";

    private static final String HTML = "Core/SideConfigPage.html";

    /// The container the HTML declares, and the generated subtree parented into it.
    private static final String ROWS_CONTAINER_ID = "side-config-rows";
    private static final String ROWS_ID = "side-config-generated";

    /// Valid HyUI layout modes. "Vertical"/"Horizontal" are not among them -- a Group stacks
    /// vertically by default (its .ui declares LayoutMode: Top), and a horizontal run of children
    /// is LeftCenterWrap. Passing an invalid value disconnects the client with
    /// "CustomUI Set command couldn't set value".
    private static final String LAYOUT_STACK = "Top";
    private static final String LAYOUT_ROW = "LeftCenterWrap";

    /// Face order, laid out as three opposing pairs so the rows read like the block.
    private static final List<BlockFace> FACES = List.of(
            BlockFace.Up, BlockFace.Down,
            BlockFace.North, BlockFace.South,
            BlockFace.East, BlockFace.West);

    private SideConfigPage() {
    }

    /// Builds the page for one block, listing every resource that block participates in.
    ///
    /// Returns null when the block carries no logistic container at all, which is the caller's
    /// signal to leave its button out entirely.
    @Nullable
    public static PageBuilder of(@Nonnull World world, @Nonnull Vector3i blockPos,
                                 @Nonnull String blockName) {

        var present = presentResources(world, blockPos);
        if (present.isEmpty()) return null;

        var template = new TemplateProcessor().setVariable("blockName", blockName);
        var page = HytechPage.of(HTML, template);

        var rows = GroupBuilder.group()
                .withId(ROWS_ID)
                .withLayoutMode(LAYOUT_STACK);

        for (var resource : present) {
            rows.addChild(resourceRow(world, blockPos, resource));
        }

        // Two separate jobs, and doing only one of them was the bug. addElement walks the subtree
        // and registers every id, which is what makes the buttons wireable -- but it parents to
        // #HyUIRoot, so the rows landed outside the container and nothing rendered. Attaching
        // children to a getById container renders them but registers nothing, so the listeners
        // were skipped. Do both: register via addElement, then reparent with inside().
        page.addElement(rows);
        rows.inside("#" + ROWS_CONTAINER_ID);

        for (var resource : present) {
            for (var face : FACES) {
                String id = buttonId(resource, face);

                HytechPage.onClick(page, id, (_, ctx) -> {
                    cycleFace(world, blockPos, resource, face);
                    refresh(ctx, id, world, blockPos, resource, face);
                });
            }
        }

        return page;
    }

    /// Resources this block actually participates in, in registration order.
    @Nonnull
    public static List<LogisticResourceType> presentResources(
            @Nonnull World world, @Nonnull Vector3i blockPos) {

        return HytechCoreModule.get().getResourceTypes().stream()
                .filter(resource -> resource.isPresentAt(world, blockPos))
                .toList();
    }

    /// One resource: a heading, then a face button per side.
    private static GroupBuilder resourceRow(
            World world, Vector3i blockPos, LogisticResourceType resource) {

        var row = GroupBuilder.group()
                .withId("side-row-" + resource.id())
                .withLayoutMode(LAYOUT_STACK);

        row.addChild(LabelBuilder.label()
                .withId("side-head-" + resource.id())
                .withText(resource.label()));

        var buttons = GroupBuilder.group()
                .withId("side-buttons-" + resource.id())
                .withLayoutMode(LAYOUT_ROW);

        for (var face : FACES) {
            buttons.addChild(faceButton(world, blockPos, resource, face));
        }

        row.addChild(buttons);

        return row;
    }

    /// A single face: the neighbour's icon above a button showing that side's mode.
    ///
    /// The icon is what makes the grid readable -- "Out" on North means little until you can see
    /// it is pointing at a pipe.
    private static GroupBuilder faceButton(
            World world, Vector3i blockPos,
            LogisticResourceType resource, BlockFace face) {

        String id = buttonId(resource, face);

        var cell = GroupBuilder.group()
                .withId(id + "-cell")
                .withLayoutMode(LAYOUT_STACK);

        cell.addChild(LabelBuilder.label()
                .withId(id + "-face")
                .withText(face.name()));

        var neighbour = neighbourItemId(world, blockPos, face);
        if (neighbour != null) {
            cell.addChild(ItemSlotBuilder.itemSlot()
                    .withId(id + "-icon")
                    .withItemId(neighbour)
                    .withShowQuantity(false));
        }

        cell.addChild(ButtonBuilder.smallSecondaryTextButton()
                .withId(id)
                .withText(faceLabel(world, blockPos, resource, face)));

        return cell;
    }

    /// Stable element id, so the listener and the refresh agree on which button they mean.
    @Nonnull
    public static String buttonId(@Nonnull LogisticResourceType resource, @Nonnull BlockFace face) {
        return "side-" + resource.id() + "-" + face.name().toLowerCase();
    }

    private static void cycleFace(World world, Vector3i blockPos,
                                  LogisticResourceType resource, BlockFace face) {
        var component = resource.componentAt(world, blockPos);
        if (component == null) return;

        component.cycleBlockFaceConfig(localFace(world, blockPos, face));
    }

    private static void refresh(UIContext ctx, String id, World world, Vector3i blockPos,
                                LogisticResourceType resource, BlockFace face) {
        ctx.editById(id, ButtonBuilder.class,
                button -> button.withText(faceLabel(world, blockPos, resource, face)));
        ctx.updatePage(true);
    }

    private static String faceLabel(World world, Vector3i blockPos,
                                    LogisticResourceType resource, BlockFace face) {
        var config = faceConfig(world, blockPos, resource, face);

        return config == null ? "-" : switch (config) {
            case BOTH -> "Both";
            case INPUT -> "In";
            case OUTPUT -> "Out";
            case NONE -> "Off";
        };
    }

    @Nullable
    private static BlockFaceConfigType faceConfig(World world, Vector3i blockPos,
                                                  LogisticResourceType resource, BlockFace face) {
        var component = resource.componentAt(world, blockPos);
        if (component == null) return null;

        return component.getFaceConfigTowards(localFace(world, blockPos, face));
    }

    /// The neighbouring block's item id, for its icon. Null when the side is air or unloaded.
    @Nullable
    private static String neighbourItemId(World world, Vector3i blockPos, BlockFace face) {
        var direction = BlockFaceUtil.getVectorFromFace(face);
        var neighbourPos = new Vector3i(blockPos).add(direction);

        var blockType = HytechUtil.getBlockType(world, neighbourPos);
        if (blockType == null) return null;

        var item = blockType.getItem();

        return item == null ? null : item.getId();
    }

    /// Converts a world face to the block's own local face, honouring its placed rotation.
    ///
    /// Without this a rotated machine would configure the wrong side. The wrench goes through the
    /// same conversion, which is what keeps the two agreeing.
    private static BlockFace localFace(World world, Vector3i blockPos, BlockFace worldFace) {
        var blockRef = HytechUtil.getBlockEntityRef(world, blockPos);
        if (blockRef == null) return worldFace;

        var transform = HytechUtil.getBlockTransform(blockRef, world.getChunkStore().getStore());
        if (transform == null) return worldFace;

        return BlockFaceUtil.getLocalFace(BlockFaceUtil.getVectorFromFace(worldFace),
                transform.rotation());
    }
}
