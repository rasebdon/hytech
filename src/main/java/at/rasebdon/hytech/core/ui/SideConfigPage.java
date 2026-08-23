package at.rasebdon.hytech.core.ui;

import at.rasebdon.hytech.core.HytechCoreModule;
import at.rasebdon.hytech.core.LogisticResourceType;
import at.rasebdon.hytech.core.transport.BlockFaceConfigType;
import at.rasebdon.hytech.core.util.BlockFaceUtil;
import at.rasebdon.hytech.core.util.HytechUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.BiConsumer;

/// Per-resource side configuration for a logistic block.
///
/// A machine can carry several containers -- the burner outputs energy on a face while accepting
/// items on the same one -- so sides are configured per resource, with arrows to walk the ones this
/// block actually has. Six face buttons, fixed page size, however many resource types exist.
public final class SideConfigPage extends HytechCustomPage {

    private static final String DOCUMENT = "Hytech/SideConfigPage.ui";

    private static final String ACTION_BACK = "back";
    private static final String ACTION_PREV = "prev";
    private static final String ACTION_NEXT = "next";
    private static final String ACTION_FACE = "face:";

    /// Face order down the page, and the selector each maps to.
    private static final List<BlockFace> FACES = List.of(
            BlockFace.Up, BlockFace.Down,
            BlockFace.North, BlockFace.South,
            BlockFace.East, BlockFace.West);

    private final World world;
    private final Vector3i blockPos;
    private final List<LogisticResourceType> present;
    private final BiConsumer<Store<EntityStore>, Ref<EntityStore>> onBack;

    private int editing;

    private SideConfigPage(@Nonnull PlayerRef playerRef,
                           @Nonnull World world,
                           @Nonnull Vector3i blockPos,
                           @Nonnull List<LogisticResourceType> present,
                           @Nonnull BiConsumer<Store<EntityStore>, Ref<EntityStore>> onBack) {
        super(playerRef);

        this.world = world;
        this.blockPos = new Vector3i(blockPos);
        this.present = present;
        this.onBack = onBack;
    }

    /// The page for one block, or null when the block carries no logistic container.
    @Nullable
    public static SideConfigPage of(@Nonnull PlayerRef playerRef,
                                    @Nonnull World world,
                                    @Nonnull Vector3i blockPos,
                                    @Nonnull BiConsumer<Store<EntityStore>, Ref<EntityStore>> onBack) {

        var present = presentResources(world, blockPos);
        if (present.isEmpty()) return null;

        return new SideConfigPage(playerRef, world, blockPos, present, onBack);
    }

    /// Resources this block actually participates in, in registration order.
    @Nonnull
    public static List<LogisticResourceType> presentResources(@Nonnull World world,
                                                              @Nonnull Vector3i blockPos) {
        return HytechCoreModule.get().getResourceTypes().stream()
                .filter(resource -> resource.isPresentAt(world, blockPos))
                .toList();
    }

    @Override
    @Nonnull
    protected String document() {
        return DOCUMENT;
    }

    @Override
    protected void render(@Nonnull UICommandBuilder commands) {
        var resource = this.present.get(this.editing);

        commands.set("#TitleLabel.Text",
                HytechUtil.getBlockDisplayName(this.world, this.blockPos) + " - Sides");

        commands.set("#ResourceLabel.Text", this.present.size() == 1
                ? resource.label()
                : String.format("%s  (%d/%d)", resource.label(), this.editing + 1, this.present.size()));

        // Arrows are pointless on a block with one container, and a control that cannot do
        // anything is worse than no control.
        boolean multiple = this.present.size() > 1;
        commands.set("#ResourcePrev.Visible", multiple);
        commands.set("#ResourceNext.Visible", multiple);

        for (var face : FACES) {
            commands.set(selector(face) + ".Text", faceLabel(resource, face));
        }
    }

    @Override
    protected void bind(@Nonnull UIEventBuilder events) {
        onClick(events, "#BackButton", ACTION_BACK);
        onClick(events, "#ResourcePrev", ACTION_PREV);
        onClick(events, "#ResourceNext", ACTION_NEXT);

        for (var face : FACES) {
            onClick(events, selector(face), ACTION_FACE + face.name());
        }
    }

    @Override
    protected void onAction(@Nonnull String action,
                            @Nonnull Ref<EntityStore> ref,
                            @Nonnull Store<EntityStore> store) {

        if (action.equals(ACTION_BACK)) {
            close();
            this.onBack.accept(store, ref);
            return;
        }

        if (action.equals(ACTION_PREV)) {
            this.editing = Math.floorMod(this.editing - 1, this.present.size());
            refresh();
            return;
        }

        if (action.equals(ACTION_NEXT)) {
            this.editing = (this.editing + 1) % this.present.size();
            refresh();
            return;
        }

        if (action.startsWith(ACTION_FACE)) {
            cycle(action.substring(ACTION_FACE.length()));
            refresh();
        }
    }

    private void cycle(String faceName) {
        BlockFace face;
        try {
            face = BlockFace.valueOf(faceName);
        } catch (IllegalArgumentException error) {
            return;
        }

        var component = this.present.get(this.editing).componentAt(this.world, this.blockPos);
        if (component == null) return;

        component.cycleBlockFaceConfig(localFace(face));
    }

    private static String selector(BlockFace face) {
        return "#Face" + face.name();
    }

    /// "Up: Both  -> Energy Pipe" -- the face, its mode, and what it is pointing at.
    private String faceLabel(LogisticResourceType resource, BlockFace face) {
        var component = resource.componentAt(this.world, this.blockPos);

        String mode = "-";
        if (component != null) {
            mode = describe(component.getFaceConfigTowards(localFace(face)));
        }

        var neighbour = neighbourName(face);

        return neighbour == null
                ? String.format("%s: %s", face.name(), mode)
                : String.format("%s: %s  -> %s", face.name(), mode, neighbour);
    }

    private static String describe(@Nullable BlockFaceConfigType config) {
        if (config == null) return "-";

        return switch (config) {
            case BOTH -> "Both";
            case INPUT -> "In";
            case OUTPUT -> "Out";
            case NONE -> "Off";
        };
    }

    @Nullable
    private String neighbourName(BlockFace face) {
        var direction = BlockFaceUtil.getVectorFromFace(face);
        var neighbourPos = new Vector3i(this.blockPos).add(direction);

        return HytechUtil.getBlockDisplayNameOrNull(this.world, neighbourPos);
    }

    /// Converts a world face to the block's own local face, honouring its placed rotation.
    ///
    /// Without this a rotated machine configures the wrong side. The wrench uses the same
    /// conversion, which is what keeps the two agreeing.
    private BlockFace localFace(BlockFace worldFace) {
        var blockRef = HytechUtil.getBlockEntityRef(this.world, this.blockPos);
        if (blockRef == null) return worldFace;

        var transform = HytechUtil.getBlockTransform(blockRef, this.world.getChunkStore().getStore());
        if (transform == null) return worldFace;

        return BlockFaceUtil.getLocalFace(BlockFaceUtil.getVectorFromFace(worldFace),
                transform.rotation());
    }
}
