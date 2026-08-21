package at.rasebdon.hytech.core.systems;

import at.rasebdon.hytech.core.HytechCoreModule;
import at.rasebdon.hytech.core.components.LogisticComponent;
import at.rasebdon.hytech.core.transport.BlockFaceConfigType;
import at.rasebdon.hytech.core.util.BlockFaceUtil;
import at.rasebdon.hytech.core.util.BlockRayUtil;
import at.rasebdon.hytech.core.util.HytechUtil;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nullable;
import java.util.*;

/// Shows the face configuration of the logistic block a wrench is aimed at.
///
/// While a player holds the wrench, the side under their crosshair is covered with a flat
/// coloured quad: grey for none, purple for both, red for input, blue for output. At most
/// one entity per player, only while they are actually looking at a logistic block, so
/// this costs nothing when nobody is wrenching.
public final class FaceConfigOverlaySystem extends TickingSystem<EntityStore> {

    private static final String WRENCH_ITEM_ID = "Wrench";
    private static final double REACH = 6.0;
    private static final float UPDATE_INTERVAL_SECONDS = 0.1f;

    /// Lifts the quad clear of the block face so it does not z-fight with it. The quad has
    /// no thickness, so this only needs to beat depth precision.
    private static final double SURFACE_OFFSET = 0.01;

    /// Entity models render at half block scale, so a full-face quad needs 2 --
    /// the same convention PipeFaceMarkers uses.
    private static final float OVERLAY_SCALE = 2f;
    // Keyed by entity index, not by Ref: Ref has no equals/hashCode, so a fresh one is
    // handed out every tick and map lookups would never match -- which spawned a new quad
    // every pass and never cleaned any up.
    private final Map<Integer, Ref<EntityStore>> overlays = new HashMap<>();
    private final Map<Integer, Shown> shown = new HashMap<>();
    private float updateTime;

    @Nullable
    private static EyeRay eyeRay(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        var transform = store.getComponent(playerRef, TransformComponent.getComponentType());
        var headRotation = store.getComponent(playerRef, HeadRotation.getComponentType());
        if (transform == null || headRotation == null) return null;

        var origin = new Vector3d(transform.getPosition());

        var modelComponent = store.getComponent(playerRef, ModelComponent.getComponentType());
        if (modelComponent != null && modelComponent.getModel() != null) {
            origin.y += modelComponent.getModel().getEyeHeight(playerRef, store);
        }

        return new EyeRay(origin, headRotation.getDirection());
    }

    /// Which side of the block the hit landed on: whichever local coordinate sits closest
    /// to a face plane.
    private static BlockFace faceOfHit(@NonNull Vector3d hit, @NonNull Vector3i blockPos) {
        double x = hit.x - blockPos.x;
        double y = hit.y - blockPos.y;
        double z = hit.z - blockPos.z;

        double[] distances = {x, 1 - x, y, 1 - y, z, 1 - z};
        BlockFace[] faces = {
                BlockFace.West, BlockFace.East,
                BlockFace.Down, BlockFace.Up,
                BlockFace.North, BlockFace.South
        };

        double best = Double.MAX_VALUE;
        BlockFace face = BlockFace.None;

        for (int i = 0; i < distances.length; i++) {
            if (distances[i] < best) {
                best = distances[i];
                face = faces[i];
            }
        }

        return face;
    }

    @Nullable
    private static ModelAsset overlayAsset(BlockFaceConfigType config) {
        var name = switch (config) {
            case NONE -> "Face_Overlay_None";
            case BOTH -> "Face_Overlay_Both";
            case INPUT -> "Face_Overlay_Input";
            case OUTPUT -> "Face_Overlay_Output";
        };

        return ModelAsset.getAssetMap().getAsset(name);
    }

    /// The quad lies in the XY plane facing +Z, so south needs no rotation. Yaw sweeps it
    /// round the horizontal faces; pitch tips it onto the top and bottom.
    private static Rotation3f quadRotation(BlockFace face) {
        return switch (face) {
            case North -> new Rotation3f(0f, (float) Math.toRadians(180), 0f);
            case East -> new Rotation3f(0f, (float) Math.toRadians(90), 0f);
            case West -> new Rotation3f(0f, (float) Math.toRadians(-90), 0f);
            case Up -> new Rotation3f((float) Math.toRadians(-90), 0f, 0f);
            case Down -> new Rotation3f((float) Math.toRadians(90), 0f, 0f);
            default -> new Rotation3f(0f, 0f, 0f);
        };
    }

    private static void despawn(Store<EntityStore> store, @Nullable Ref<EntityStore> overlay) {
        if (overlay != null && overlay.isValid()) {
            store.removeEntity(overlay, RemoveReason.REMOVE);
        }
    }

    @Override
    public void tick(float dt, int systemIndex, @NonNull Store<EntityStore> store) {
        if (this.updateTime < UPDATE_INTERVAL_SECONDS) {
            this.updateTime += dt;
            return;
        }

        this.updateTime = 0f;

        // Entities cannot be added or removed while the store is processing, so the tick
        // only decides what should change and the work runs afterwards on the world thread.
        var pending = new ArrayList<Runnable>();
        var seen = new HashSet<Integer>();

        store.forEachChunk(Player.getComponentType(), (chunk, _) -> {
            for (int i = 0; i < chunk.size(); i++) {
                seen.add(updatePlayer(store, chunk, i, pending));
            }
        });

        // Players who have gone away leave their quad behind otherwise.
        for (var key : Set.copyOf(overlays.keySet())) {
            if (!seen.contains(key)) {
                shown.remove(key);
                pending.add(() -> despawn(store, overlays.remove(key)));
            }
        }

        if (pending.isEmpty()) return;

        var world = store.getExternalData().getWorld();

        world.execute(() -> pending.forEach(Runnable::run));
    }

    private int updatePlayer(
            Store<EntityStore> store,
            ArchetypeChunk<EntityStore> chunk,
            int index,
            List<Runnable> pending) {

        var playerRef = chunk.getReferenceTo(index);
        int key = playerRef.getIndex();

        var target = resolveTarget(store, playerRef);
        if (target == null) {
            if (shown.remove(key) != null) {
                pending.add(() -> despawn(store, overlays.remove(key)));
            }
            return key;
        }

        // Nothing moved or changed, so the existing quad is still correct.
        if (target.equals(shown.get(key))) return key;
        if (overlayAsset(target.config()) == null) return key;

        shown.put(key, target);

        // The old entity is looked up when this runs, not now: if a second update queues
        // before the world drains these, a captured reference would already be stale and
        // the entity it replaced would be leaked.
        pending.add(() -> {
            despawn(store, overlays.remove(key));
            overlays.put(key, spawnOverlay(store, target));
        });

        return key;
    }

    /// The logistic face the player is aiming at, or null if that is not what they are doing.
    @Nullable
    private Shown resolveTarget(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        var held = InventoryComponent.getItemInHand(store, playerRef);
        if (held == null || !WRENCH_ITEM_ID.equals(held.getItemId())) return null;

        var world = store.getExternalData().getWorld();

        var eye = eyeRay(store, playerRef);
        if (eye == null) return null;

        // Traced against real block shapes: the cell-based helper would stop at a pipe
        // standing beside the face being aimed at, because it only tests block ids.
        var hit = BlockRayUtil.trace(world, eye.origin(), eye.direction(), REACH);
        if (hit == null) return null;

        var blockPos = hit.block();
        var component = logisticBlockAt(world, blockPos);
        if (component == null) return null;

        var worldFace = faceOfHit(hit.point(), blockPos);
        if (worldFace == BlockFace.None) return null;

        var blockRef = HytechUtil.getBlockEntityRef(world, blockPos);
        if (blockRef == null) return null;

        var transform = HytechUtil.getBlockTransform(blockRef, world.getChunkStore().getStore());
        if (transform == null) return null;

        var localFace = BlockFaceUtil.getLocalFace(
                BlockFaceUtil.getVectorFromFace(worldFace), transform.rotation());

        return new Shown(new Vector3i(blockPos), worldFace, component.getFaceConfigTowards(localFace));
    }

    /// Logistic blocks only. Pipes are not in this set -- they are registered separately as
    /// pipe components -- so their arms keep showing connectivity without a quad over them.
    @Nullable
    private LogisticComponent<?> logisticBlockAt(@NonNull World world, @NonNull Vector3i blockPos) {
        for (var blockType : HytechCoreModule.get().blockComponents) {
            var component = HytechUtil.getBlockComponent(world, blockPos, blockType);
            if (component != null) {
                return component;
            }
        }

        return null;
    }

    private Ref<EntityStore> spawnOverlay(Store<EntityStore> store, Shown target) {
        var direction = BlockFaceUtil.getVectorFromFace(target.face());

        // Centre of the targeted face, nudged outwards along its normal.
        var position = new Vector3d(
                target.block().x + 0.5, target.block().y + 0.5, target.block().z + 0.5)
                .add(direction.x * (0.5 + SURFACE_OFFSET),
                        direction.y * (0.5 + SURFACE_OFFSET),
                        direction.z * (0.5 + SURFACE_OFFSET));

        var model = Model.createStaticScaledModel(Objects.requireNonNull(overlayAsset(target.config())), OVERLAY_SCALE);

        Holder<EntityStore> holder = store.getRegistry().newHolder();
        holder.addComponent(TransformComponent.getComponentType(),
                new TransformComponent(position, quadRotation(target.face())));
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
        holder.addComponent(NetworkId.getComponentType(),
                new NetworkId(store.getExternalData().takeNextNetworkId()));
        holder.ensureComponent(UUIDComponent.getComponentType());

        return store.addEntity(holder, AddReason.SPAWN);
    }

    /// What the overlay currently shows for a player, so it is only respawned on change.

    private record Shown(Vector3i block, BlockFace face, BlockFaceConfigType config) {
    }

    private record EyeRay(Vector3d origin, Vector3d direction) {
    }
}
