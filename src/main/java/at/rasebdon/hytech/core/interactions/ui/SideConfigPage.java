package at.rasebdon.hytech.core.interactions.ui;

import at.rasebdon.hytech.core.HytechCoreModule;
import at.rasebdon.hytech.core.LogisticResourceType;
import at.rasebdon.hytech.core.transport.BlockFaceConfigType;
import at.rasebdon.hytech.core.util.BlockFaceUtil;
import at.rasebdon.hytech.core.util.HytechUtil;
import au.ellie.hyui.builders.ButtonBuilder;
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

/// Per-resource side configuration, shared by every logistic block UI.
///
/// A machine can carry several containers -- the burner outputs energy on a face while accepting
/// items on the same one -- so sides are configured per resource. One resource is edited at a
/// time and "Next Resource" walks the ones this block actually has, which keeps the page a fixed
/// size however many resource types the mod grows to.
///
/// **Every element here is declared statically in the HTML and only ever relabelled.** That is
/// not a style preference. HyUI registers element ids when the page is parsed, so a builder
/// created at runtime and attached to a container renders but receives no events, while one added
/// through `addElement` registers but is parented to the page root rather than the container.
/// Static ids sidestep both, and `editById` on them is the path that actually works. A nested
/// `div` carrying its own `layout-mode` is worse still -- it disconnects the client.
public final class SideConfigPage {

    /// Element ids the machine pages and this page agree on.
    public static final String OPEN_BUTTON_ID = "side-config-button";
    public static final String BACK_BUTTON_ID = "side-config-back";

    private static final String HTML = "Core/SideConfigPage.html";
    private static final String RESOURCE_LABEL_ID = "side-resource-label";
    private static final String RESOURCE_NEXT_ID = "side-resource-next";

    /// Face order top to bottom, as the buttons read on screen.
    private static final List<BlockFace> FACES = List.of(
            BlockFace.Up, BlockFace.Down,
            BlockFace.North, BlockFace.South,
            BlockFace.East, BlockFace.West);

    private SideConfigPage() {
    }

    /// Builds the page for one block, or null when the block carries no logistic container --
    /// the caller signal to leave its button out entirely.
    @Nullable
    public static PageBuilder of(@Nonnull World world, @Nonnull Vector3i blockPos,
                                 @Nonnull String blockName) {

        var present = presentResources(world, blockPos);
        if (present.isEmpty()) return null;

        var template = new TemplateProcessor().setVariable("blockName", blockName);
        var page = HytechPage.of(HTML, template);

        // Which resource is being edited. A one-element array rather than a field because one
        // page instance exists per player per opening, and the handlers have to mutate it.
        var editing = new int[]{0};

        applyLabels(page, world, blockPos, present, editing[0]);

        HytechPage.onClick(page, RESOURCE_NEXT_ID, (_, ctx) -> {
            editing[0] = (editing[0] + 1) % present.size();
            refresh(ctx, world, blockPos, present, editing[0]);
        });

        for (var face : FACES) {
            HytechPage.onClick(page, faceButtonId(face), (_, ctx) -> {
                cycleFace(world, blockPos, present.get(editing[0]), face);
                refresh(ctx, world, blockPos, present, editing[0]);
            });
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

    private static String faceButtonId(BlockFace face) {
        return "side-face-" + face.name().toLowerCase();
    }

    /// Initial labels, set on the builder before the page opens.
    private static void applyLabels(PageBuilder page, World world, Vector3i blockPos,
                                    List<LogisticResourceType> present, int editing) {

        var resource = present.get(editing);

        page.editById(RESOURCE_LABEL_ID, LabelBuilder.class,
                label -> label.withText(resourceHeading(present, editing)));

        // Hidden when there is only one resource: a selector that cannot select is worse than
        // no selector.
        page.editById(RESOURCE_NEXT_ID, ButtonBuilder.class,
                button -> button.withVisible(present.size() > 1));

        for (var face : FACES) {
            page.editById(faceButtonId(face), ButtonBuilder.class,
                    button -> button.withText(faceLabel(world, blockPos, resource, face)));
        }
    }

    /// The same labels, applied through a live page after a click.
    private static void refresh(UIContext ctx, World world, Vector3i blockPos,
                                List<LogisticResourceType> present, int editing) {

        var resource = present.get(editing);

        ctx.editById(RESOURCE_LABEL_ID, LabelBuilder.class,
                label -> label.withText(resourceHeading(present, editing)));

        for (var face : FACES) {
            ctx.editById(faceButtonId(face), ButtonBuilder.class,
                    button -> button.withText(faceLabel(world, blockPos, resource, face)));
        }

        ctx.updatePage(true);
    }

    private static String resourceHeading(List<LogisticResourceType> present, int editing) {
        var resource = present.get(editing);

        if (present.size() == 1) return "Editing: " + resource.label();

        return String.format("Editing: %s  (%d/%d)", resource.label(), editing + 1, present.size());
    }

    private static void cycleFace(World world, Vector3i blockPos,
                                  LogisticResourceType resource, BlockFace face) {
        var component = resource.componentAt(world, blockPos);
        if (component == null) return;

        component.cycleBlockFaceConfig(localFace(world, blockPos, face));
    }

    /// "Up: Both" -- the face and its mode on one button, since a button is all there is.
    private static String faceLabel(World world, Vector3i blockPos,
                                    LogisticResourceType resource, BlockFace face) {
        var config = faceConfig(world, blockPos, resource, face);

        String mode = config == null ? "-" : switch (config) {
            case BOTH -> "Both";
            case INPUT -> "In";
            case OUTPUT -> "Out";
            case NONE -> "Off";
        };

        return face.name() + ": " + mode;
    }

    @Nullable
    private static BlockFaceConfigType faceConfig(World world, Vector3i blockPos,
                                                  LogisticResourceType resource, BlockFace face) {
        var component = resource.componentAt(world, blockPos);
        if (component == null) return null;

        return component.getFaceConfigTowards(localFace(world, blockPos, face));
    }

    /// Converts a world face to the block local face, honouring its placed rotation.
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
