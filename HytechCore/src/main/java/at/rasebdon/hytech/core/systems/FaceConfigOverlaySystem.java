package at.rasebdon.hytech.core.systems;

import at.rasebdon.hytech.core.interactions.WrenchInteraction;
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

/// Shows the face configuration of the logistic block a wrench is aimed at, as a flat coloured
/// quad: grey for none, purple for both, red for input, blue for output.
public final class FaceConfigOverlaySystem extends TickingSystem<EntityStore> {

    private static final double REACH = 6.0;
    private static final float UPDATE_INTERVAL_SECONDS = 0.1f;

    /// Lifts the quad clear of the block face to avoid z-fighting.
    private static final double SURFACE_OFFSET = 0.01;

    /// Entity models render at half block scale (2 = full face); inset slightly so it reads as
    /// an overlay rather than a retexture.
    private static final float OVERLAY_SCALE = 1.6f;

    // Keyed by index, not Ref: Ref has no equals/hashCode, so lookups by Ref would never match.
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

    /// Which side of the block the hit landed on: the local coordinate closest to a face plane.
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

    /// The quad lies in the XY plane facing +Z, so south needs no rotation.
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

        // Entities cannot be added/removed while the store is processing; work runs afterwards.
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

        if (target.equals(shown.get(key))) return key;
        if (overlayAsset(target.config()) == null) return key;

        shown.put(key, target);

        // Old entity looked up when this runs, not now, or a queued second update would leak it.
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
        // Via WrenchInteraction so the overlay and the interaction agree on what is a wrench.
        if (!WrenchInteraction.isWrench(held)) return null;

        var world = store.getExternalData().getWorld();

        var eye = eyeRay(store, playerRef);
        if (eye == null) return null;

        // Traced against real block shapes: a cell-based test would stop at a pipe beside the face.
        var hit = BlockRayUtil.trace(world, eye.origin(), eye.direction(), REACH);
        if (hit == null) return null;

        var blockPos = hit.block();

        var component = componentForMode(store, world, playerRef, blockPos);
        if (component == null) return null;

        var worldFace = faceOfHit(hit.point(), blockPos);
        if (worldFace == BlockFace.None) return null;

        var blockRef = HytechUtil.getBlockEntityRef(world, blockPos);
        if (blockRef == null) return null;

        var transform = HytechUtil.getBlockTransform(blockRef, world.getChunkStore().getStore());
        if (transform == null) return null;

        var localFace = BlockFaceUtil.getLocalFace(
                BlockFaceUtil.getVectorFromFace(worldFace), transform.rotation());

        // A side locked to one mode gets no overlay rather than one the player cannot cycle.
        if (component.isFaceLocked(localFace)) return null;

        return new Shown(new Vector3i(blockPos), worldFace, component.getFaceConfigTowards(localFace));
    }

    /// The block component for the player's selected resource, or null. Deliberately does *not*
    /// fall back to another resource, unlike the wrench: the quad implies "this is what you are
    /// about to change", so showing the wrong resource would be a lie.
    @Nullable
    private LogisticComponent<?> componentForMode(
            @NonNull Store<EntityStore> store, @NonNull World world,
            @NonNull Ref<EntityStore> playerRef, @NonNull Vector3i blockPos) {

        var modeType = HytechCoreModule.get().getWrenchModeComponentType();
        var mode = store.getComponent(playerRef, modeType);

        var resource = mode == null ? null : mode.resolve();
        if (resource == null) return null;

        // Pipes are excluded: their arms already show connectivity, and a quad would hide them.
        return resource.blockAt(world, blockPos);
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
