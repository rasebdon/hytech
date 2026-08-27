package at.rasebdon.hytech.core.ui;

import at.rasebdon.hytech.core.LogisticResourceType;
import at.rasebdon.hytech.core.transport.BlockFaceConfigType;
import at.rasebdon.hytech.core.util.BlockFaceUtil;
import at.rasebdon.hytech.core.util.HytechUtil;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/// Per-resource side configuration, as a panel beside the machine rather than a page instead of it.
///
/// It used to be its own page, which meant configuring a crusher hid the crusher: you could not see
/// the effect of switching a face to Output while looking at the thing outputting. Living inside
/// [MachinePage] also retires the reopen dance -- Back used to rebuild the machine page from
/// scratch -- because the machine page never went away.
///
/// The six faces are laid out as a plus. Position is what says which side, so a cell carries only
/// the neighbouring block's icon; the button's own colour says the mode, and its tooltip spells
/// both out. Up sits above the centre and Down in the corner opposite it, which is the only place
/// left once the four horizontal faces have taken the arms.
///
/// A machine can carry several containers -- the burner outputs energy on a face while accepting
/// items on the same one -- so sides are configured per resource, with one tab per resource this
/// block actually has.
public final class SideConfigPanel {

    static final String ACTION_FACE = "face:";
    static final String ACTION_RESOURCE = "res:";
    static final String ACTION_PUSH = "push";

    /// Tabs the document declares: one per registered resource type.
    private static final int TABS = 5;

    // The container's height is declared in the markup rather than written here: this panel shows
    // the same six faces whatever the block, so unlike the machine's page nothing about it varies.

    /// Every face, in no particular order -- the plus in the document decides where each one sits.
    private static final List<BlockFace> FACES = List.of(
            BlockFace.Up, BlockFace.Down,
            BlockFace.North, BlockFace.South,
            BlockFace.East, BlockFace.West);

    /// How each face mode is drawn and described.
    ///
    /// The colours are taken from the wrench's own in-world overlay textures
    /// (`Common/VFX/Overlay/Face_Overlay_*.png`) so that a face is the same colour on the panel as
    /// it is on the block. That is what lets this panel carry no legend: the player has already
    /// learned red-in, blue-out, purple-both from pointing a wrench at things.
    ///
    /// One table rather than three parallel switches, so a new mode cannot pick up a colour and
    /// lose its hover or its wording.
    private static final Map<BlockFaceConfigType, FaceStyle> FACE_STYLES = Map.of(
            BlockFaceConfigType.BOTH, new FaceStyle("#a040c0", "#c066e0", "Both"),
            BlockFaceConfigType.INPUT, new FaceStyle("#d03030", "#e85c5c", "Input"),
            BlockFaceConfigType.OUTPUT, new FaceStyle("#3060d0", "#5c88e8", "Output"),
            BlockFaceConfigType.NONE, new FaceStyle("#808080", "#a0a0a0", "Disabled"));

    /// A block with no container of the selected resource: grey like Disabled, worded as what it is.
    private static final FaceStyle UNCONFIGURABLE =
            new FaceStyle("#808080", "#a0a0a0", "Not configurable");

    /// A face the block's assets pin to a single state. Clicking does nothing, so it must not look
    /// like it would -- the same colour hovered as at rest.
    private static final String LOCKED = "#2a2f36";

    /// Auto-push, on and off. Warm when the block is ejecting, inert when it is not.
    private static final String PUSH_ON = "#e8a93b";
    private static final String PUSH_ON_HOVER = "#f5c25f";
    private static final String PUSH_OFF = "#1b2530";
    private static final String PUSH_OFF_HOVER = "#2a3846";

    /// One accent per resource, matching the pipe tints, so a tab is recognisable before its
    /// tooltip appears. Keyed by the resource id `LogisticResourceType` carries.
    private static final Map<String, String> RESOURCE_COLOURS = Map.of(
            "energy", "#e8a93b",
            "items", "#7a9cc6",
            "fluid", "#5ca8d1",
            "gas", "#7cb855",
            "heat", "#d18a5c");

    private static final String RESOURCE_FALLBACK = "#5a6a7a";

    private final World world;
    private final Vector3i blockPos;

    /// Which resource's faces are on screen. An index into the *live* present list rather than a
    /// remembered resource, so a block that loses a container while its page is open degrades to
    /// showing a different one instead of throwing.
    private int editing;

    private boolean open;

    public SideConfigPanel(@Nonnull World world, @Nonnull Vector3i blockPos) {
        this.world = world;
        this.blockPos = new Vector3i(blockPos);
    }

    /// "Energy" -> "E". Enough to tell five tabs apart at a glance; the tooltip carries the rest.
    @Nonnull
    private static String initial(@Nonnull String label) {
        return label.isEmpty() ? "?" : label.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    @Nonnull
    private static String selector(@Nonnull BlockFace face) {
        return "#Face" + face.name();
    }

    @Nonnull
    private static FaceStyle style(@Nullable BlockFaceConfigType mode) {
        return mode == null ? UNCONFIGURABLE : FACE_STYLES.get(mode);
    }

    public void toggle() {
        this.open = !this.open;
    }

    /// Binds every control the panel owns. Called once, on open, for the whole document.
    void bind(@Nonnull UIEventBuilder events) {
        for (var face : FACES) {
            HytechCustomPage.onClick(events, selector(face), ACTION_FACE + face.name());
        }

        // Bound for every tab the document has: binding happens on open, while which tabs are
        // *visible* is decided on every render.
        for (int tab = 0; tab < TABS; tab++) {
            HytechCustomPage.onClick(events, "#Res" + tab, ACTION_RESOURCE + tab);
        }

        HytechCustomPage.onClick(events, "#PushToggle", ACTION_PUSH);
    }

    /// Handles one of this panel's actions. Returns false when the action was not its.
    boolean onAction(@Nonnull String action) {
        if (action.startsWith(ACTION_RESOURCE)) {
            select(action.substring(ACTION_RESOURCE.length()));
            return true;
        }

        if (action.startsWith(ACTION_FACE)) {
            cycle(action.substring(ACTION_FACE.length()));
            return true;
        }

        if (action.equals(ACTION_PUSH)) {
            togglePush();
            return true;
        }

        return false;
    }

    /// Draws the panel, or hides it.
    ///
    /// Everything goes through the view rather than the command builder so it lands in the page's
    /// change signature -- a value written around the signature is one that can go stale on screen
    /// without the refresh ever noticing.
    void render(@Nonnull MachineView view, @Nonnull List<LogisticResourceType> present) {
        boolean showing = this.open && !present.isEmpty();

        view.write("#SidesContainer.Visible", showing);

        if (!showing) return;

        if (this.editing >= present.size()) this.editing = 0;

        var resource = present.get(this.editing);

        renderPush(view, resource);

        for (int tab = 0; tab < TABS; tab++) {
            boolean used = tab < present.size();

            view.write("#Res" + tab + ".Visible", used);
            if (!used) continue;

            var type = present.get(tab);
            boolean active = tab == this.editing;
            String colour = RESOURCE_COLOURS.getOrDefault(type.id(), RESOURCE_FALLBACK);

            // A square carrying the resource's initial, with the full name in the tooltip. Five
            // text buttons wrapped onto three rows in a 268px column and read as a mess; five
            // squares fit one row with space over.
            view.write("#Res" + tab + ".Text", initial(type.label()));
            view.write("#Res" + tab + ".TooltipText", type.label());

            // The selected tab burns at full accent, the rest are dimmed to a flat slot colour, so
            // "which resource am I editing" is answerable without reading anything.
            view.write("#Res" + tab + ".Style.Default.Background", active ? colour : PUSH_OFF);
            view.write("#Res" + tab + ".Style.Hovered.Background", active ? colour : PUSH_OFF_HOVER);
        }

        view.write("#FaceDetail.Text", "Configuring: " + resource.label());

        for (var face : FACES) {
            renderFace(view, resource, face);
        }
    }

    /// The auto-push toggle for the resource on screen.
    ///
    /// Auto-push used to be a stack of "Push Energy: On" buttons in the status column, which put a
    /// per-resource decision a long way from the other per-resource decision. It is the same
    /// question the faces answer -- which way does this resource leave the block -- so it sits with
    /// them, one button that follows the selected tab.
    private void renderPush(@Nonnull MachineView view, @Nonnull LogisticResourceType resource) {
        var block = resource.blockAt(this.world, this.blockPos);

        // A pipe carries no block component and cannot push on its own; hide rather than draw a
        // toggle that would do nothing.
        view.write("#PushToggle.Visible", block != null);
        if (block == null) return;

        boolean pushing = block.isExtracting();

        view.write("#PushToggle.Style.Default.Background", pushing ? PUSH_ON : PUSH_OFF);
        view.write("#PushToggle.Style.Hovered.Background", pushing ? PUSH_ON_HOVER : PUSH_OFF_HOVER);
        view.write("#PushToggle.TooltipText",
                "Auto-Push " + resource.label() + ": " + (pushing ? "On" : "Off"));
    }

    private void togglePush() {
        var present = LogisticResourceType.presentAt(this.world, this.blockPos);
        if (present.isEmpty()) return;

        var block = present.get(Math.min(this.editing, present.size() - 1))
                .blockAt(this.world, this.blockPos);
        if (block == null) return;

        block.setExtracting(!block.isExtracting());
    }

    private void renderFace(@Nonnull MachineView view,
                            @Nonnull LogisticResourceType resource,
                            @Nonnull BlockFace face) {

        String cell = selector(face);
        var component = resource.componentAt(this.world, this.blockPos);

        var local = localFace(face);
        var mode = component == null ? null : component.getFaceConfigTowards(local);
        boolean locked = component == null || !component.isFaceConfigurable(local);

        var style = style(mode);

        view.write(cell + ".Style.Default.Background", locked ? LOCKED : style.colour());
        view.write(cell + ".Style.Hovered.Background", locked ? LOCKED : style.hover());

        // An empty well when there is nothing on that side -- air, an unloaded chunk, or a block
        // with no item form to draw. All three are "nothing to show" as far as the player is
        // concerned.
        var neighbourId = HytechUtil.getBlockItemIdOrNull(this.world, neighbourPos(face));

        view.write(cell + " #Icon.Visible", neighbourId != null);
        if (neighbourId != null) {
            view.write(cell + " #Icon.ItemId", neighbourId);
        }

        view.write(cell + ".TooltipText", tooltip(face, style, locked));
    }

    /// "Up -- Output -- Energy Pipe". The tooltip carries what the plus deliberately does not draw.
    @Nonnull
    private String tooltip(@Nonnull BlockFace face, @Nonnull FaceStyle style, boolean locked) {
        var name = HytechUtil.getBlockDisplayNameOrNull(this.world, neighbourPos(face));

        String line = face.name() + " -- " + style.label();
        if (name != null) line += " -- " + name;
        if (locked) line += "  (fixed by this block)";

        return line;
    }

    private void select(@Nonnull String tab) {
        int index;
        try {
            index = Integer.parseInt(tab);
        } catch (NumberFormatException error) {
            return;
        }

        // Bounds are re-checked against the live list on render, so an index for a resource the
        // block has since lost simply falls back to the first one.
        if (index >= 0 && index < TABS) this.editing = index;
    }

    private void cycle(@Nonnull String faceName) {
        BlockFace face;
        try {
            face = BlockFace.valueOf(faceName);
        } catch (IllegalArgumentException error) {
            return;
        }

        var present = LogisticResourceType.presentAt(this.world, this.blockPos);
        if (present.isEmpty()) return;

        int index = Math.min(this.editing, present.size() - 1);

        var component = present.get(index).componentAt(this.world, this.blockPos);
        if (component == null) return;

        // cycleBlockFaceConfig walks only the states the block's assets permit, so a face pinned to
        // one state stays on it. That is the whole reason a generator's OUTPUT side does not become
        // an INPUT when someone clicks it.
        component.cycleBlockFaceConfig(localFace(face));
    }

    @Nonnull
    private Vector3i neighbourPos(@Nonnull BlockFace face) {
        return new Vector3i(this.blockPos).add(BlockFaceUtil.getVectorFromFace(face));
    }

    /// Converts a world face to the block's own local face, honouring its placed rotation.
    ///
    /// Without this a rotated machine configures the wrong side. The wrench uses the same
    /// conversion, which is what keeps the two agreeing.
    @Nonnull
    private BlockFace localFace(@Nonnull BlockFace worldFace) {
        var blockRef = HytechUtil.getBlockEntityRef(this.world, this.blockPos);
        if (blockRef == null) return worldFace;

        var transform = HytechUtil.getBlockTransform(blockRef, this.world.getChunkStore().getStore());
        if (transform == null) return worldFace;

        return BlockFaceUtil.getLocalFace(BlockFaceUtil.getVectorFromFace(worldFace),
                transform.rotation());
    }

    /// One face mode's resting colour, hover colour and wording.
    private record FaceStyle(@Nonnull String colour, @Nonnull String hover, @Nonnull String label) {
    }
}
