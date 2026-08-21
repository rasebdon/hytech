package at.rasebdon.hytech.core.util;

import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import com.hypixel.hytale.protocol.BlockFace;
import org.joml.Vector3dc;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/// Shared geometry for the generated pipe connection variants.
///
/// A pipe renders as a centre hub plus one arm per connected face. Which arms exist is
/// encoded as a 6-bit mask, and each mask has a matching block state, model and hitbox
/// produced by `scripts/generate-pipe-assets.py`. Both the renderer and the wrench read
/// this class so the block state, the collision boxes and the click targets cannot drift
/// apart — if you change the extents here, re-run the generator.
public final class PipeConnectionMask {

    /// A block model spans 32 units, so a hub of N units occupies N/32 of the block.
    public static final int BLOCK_UNITS = 32;

    /// Default hub size in model units (the energy/default pipe). Item pipes use a larger
    /// hub, which is why this is per-component rather than a single constant -- keep in
    /// step with PIPE_TYPES in scripts/generate-pipe-assets.py.
    public static final int DEFAULT_HUB_UNITS = 8;

    /// Faces in `BlockFace` order, so bit index is `face.getValue() - 1`.
    public static final BlockFace[] FACES = {
            BlockFace.Up, BlockFace.Down, BlockFace.North,
            BlockFace.South, BlockFace.East, BlockFace.West
    };

    private PipeConnectionMask() {
    }

    public static int bitOf(@Nonnull BlockFace face) {
        return face == BlockFace.None ? 0 : 1 << (face.getValue() - 1);
    }

    /// Name of the block state variant for a mask, e.g. `Conn_5`.
    @Nonnull
    public static String stateName(int mask) {
        return "Conn_" + mask;
    }

    /// Faces the block model should draw arms for.
    ///
    /// A face explicitly set to input or output is left out: its arm is drawn by a marker
    /// entity carrying the full connection model, tip included. The push tip is smaller
    /// than the plain collar, so it can only be shown by replacing the arm rather than
    /// overlaying it.
    public static <TContainer> int renderMaskOf(@Nonnull LogisticPipeComponent<TContainer> pipe) {
        int mask = 0;

        for (BlockFace face : FACES) {
            var neighbor = pipe.getNeighbor(face);
            if (neighbor == null || !pipe.isConnectedTo(neighbor)) continue;
            if (pipe.canPullFrom(neighbor) || pipe.canPushTo(neighbor)) continue;

            mask |= bitOf(face);
        }

        return mask;
    }

    /// The pipe's logical connectivity, regardless of how each arm is drawn.
    public static <TContainer> int maskOf(@Nonnull LogisticPipeComponent<TContainer> pipe) {
        int mask = 0;

        for (BlockFace face : FACES) {
            var neighbor = pipe.getNeighbor(face);
            if (neighbor != null && pipe.isConnectedTo(neighbor)) {
                mask |= bitOf(face);
            }
        }

        return mask;
    }

    public static double hubMin(int hubUnits) {
        return 0.5 - hubUnits / 2.0 / BLOCK_UNITS;
    }

    public static double hubMax(int hubUnits) {
        return 0.5 + hubUnits / 2.0 / BLOCK_UNITS;
    }

    /// Resolves which side of the pipe the given ray hits first.
    ///
    /// The client only reports the face of the block's overall bounding box -- the engine
    /// exposes one bounding box per hitbox set, not per box -- so the pipe is hit-tested
    /// here against its own geometry. Arms return their own face regardless of which side
    /// of the arm was struck; a hit on the hub returns the hub face the ray entered, so
    /// the core configures over its own sides. [BlockFace#None] means the ray missed the
    /// pipe entirely, leaving the caller to fall back.
    @Nonnull
    public static BlockFace faceAlongRay(
            int mask,
            int hubUnits,
            @Nonnull Vector3i blockPos,
            @Nonnull Vector3dc origin,
            @Nonnull Vector3dc direction) {

        double lo = hubMin(hubUnits);
        double hi = hubMax(hubUnits);

        // The hub occludes the arms behind it: without it in the test, a ray aimed at the
        // core passes through and reports whichever arm it exits into.
        RayHit best = intersect(blockPos, lo, lo, lo, hi, hi, hi, origin, direction);
        BlockFace bestFace = best == null ? BlockFace.None : best.enteredFace();

        for (BlockFace face : FACES) {
            if ((mask & bitOf(face)) == 0) continue;

            double[] box = armBox(face, lo, hi);
            RayHit hit = intersect(blockPos, box[0], box[1], box[2], box[3], box[4], box[5],
                    origin, direction);
            if (hit == null) continue;

            // Strictly nearer, so a tie on the shared hub/arm boundary leaves the hub
            // winning rather than flickering between the two.
            if (best == null || hit.t() < best.t()) {
                best = hit;
                bestFace = face;
            }
        }

        return bestFace;
    }

    /// {minX, minY, minZ, maxX, maxY, maxZ} of an arm in block-local space: the hub
    /// cross-section stretched out to the block edge along the arm's axis. Arms meet the
    /// hub on a shared plane and never overlap one another.
    private static double[] armBox(@Nonnull BlockFace face, double lo, double hi) {
        return switch (face) {
            case Up -> new double[]{lo, hi, lo, hi, 1.0, hi};
            case Down -> new double[]{lo, 0.0, lo, hi, lo, hi};
            case North -> new double[]{lo, lo, 0.0, hi, hi, lo};
            case South -> new double[]{lo, lo, hi, hi, hi, 1.0};
            case East -> new double[]{hi, lo, lo, 1.0, hi, hi};
            case West -> new double[]{0.0, lo, lo, lo, hi, hi};
            default -> new double[]{0, 0, 0, 0, 0, 0};
        };
    }

    /// Slab-method ray/AABB intersection against a box given in block-local coordinates.
    @Nullable
    private static RayHit intersect(
            @Nonnull Vector3i blockPos,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            @Nonnull Vector3dc origin, @Nonnull Vector3dc dir) {

        double[] o = {origin.x(), origin.y(), origin.z()};
        double[] d = {dir.x(), dir.y(), dir.z()};
        double[] lo = {blockPos.x + minX, blockPos.y + minY, blockPos.z + minZ};
        double[] hi = {blockPos.x + maxX, blockPos.y + maxY, blockPos.z + maxZ};

        double tMin = 0.0;
        double tMax = Double.MAX_VALUE;
        int entryAxis = -1;
        boolean entryAtMinPlane = true;

        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(d[axis]) < 1e-9) {
                // Parallel to this slab: a miss unless the origin already lies inside it.
                if (o[axis] < lo[axis] || o[axis] > hi[axis]) return null;
                continue;
            }

            double inv = 1.0 / d[axis];
            double near = (lo[axis] - o[axis]) * inv;
            double far = (hi[axis] - o[axis]) * inv;
            boolean atMinPlane = true;

            if (near > far) {
                double swap = near;
                near = far;
                far = swap;
                atMinPlane = false;
            }

            if (near > tMin) {
                tMin = near;
                entryAxis = axis;
                entryAtMinPlane = atMinPlane;
            }

            tMax = Math.min(tMax, far);
            if (tMin > tMax) return null;
        }

        // entryAxis stays unset when the origin is already inside the box.
        if (entryAxis < 0) return null;

        return new RayHit(tMin, faceOfPlane(entryAxis, entryAtMinPlane));
    }

    /// The face whose outward normal points along the entry plane.
    @Nonnull
    private static BlockFace faceOfPlane(int axis, boolean atMinPlane) {
        return switch (axis) {
            case 0 -> atMinPlane ? BlockFace.West : BlockFace.East;
            case 1 -> atMinPlane ? BlockFace.Down : BlockFace.Up;
            default -> atMinPlane ? BlockFace.North : BlockFace.South;
        };
    }

    /// Entry distance along the ray plus the face it entered through.
    private record RayHit(double t, BlockFace enteredFace) {
    }
}
