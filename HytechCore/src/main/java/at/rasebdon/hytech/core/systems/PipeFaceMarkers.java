package at.rasebdon.hytech.core.systems;

import at.rasebdon.hytech.core.components.LogisticEntityProxyComponent;
import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.transport.BlockFaceConfigType;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/// Small marker models on faces the player has explicitly set to push or pull.
///
/// Connections themselves are part of the block's own model, so nothing is spawned for an
/// ordinary connection. Only a face that has been wrenched away from the default gets an
/// entity, which keeps a typical network at zero entities and confines the per-tick entity
/// tracking cost to the handful of faces someone actually configured.
final class PipeFaceMarkers {


    private static final float MARKER_SCALE = 2f;
    private static final Map<BlockFace, Placement> PLACEMENTS = new EnumMap<>(BlockFace.class);

    static {
        PLACEMENTS.put(BlockFace.Up,
                new Placement(new Vector3d(0.5, 0.0, 0.5), new Rotation3f(0f, 0f, 0f)));
        PLACEMENTS.put(BlockFace.Down,
                new Placement(new Vector3d(0.5, 1.0, 0.5),
                        new Rotation3f(0f, 0f, (float) Math.toRadians(180))));
        PLACEMENTS.put(BlockFace.East,
                new Placement(new Vector3d(0.0, 0.5, 0.5),
                        new Rotation3f(0f, 0f, (float) Math.toRadians(-90))));
        PLACEMENTS.put(BlockFace.West,
                new Placement(new Vector3d(1.0, 0.5, 0.5),
                        new Rotation3f(0f, 0f, (float) Math.toRadians(90))));
        PLACEMENTS.put(BlockFace.North,
                new Placement(new Vector3d(0.5, 0.5, 1.0),
                        new Rotation3f((float) Math.toRadians(-90), 0f, 0f)));
        PLACEMENTS.put(BlockFace.South,
                new Placement(new Vector3d(0.5, 0.5, 0.0),
                        new Rotation3f((float) Math.toRadians(90), 0f, 0f)));
    }

    private PipeFaceMarkers() {
    }

    static void despawn(@Nonnull List<Ref<EntityStore>> markers, @Nonnull Store<EntityStore> store) {
        for (var ref : markers) {
            if (ref != null && ref.isValid()) {
                store.removeEntity(ref, RemoveReason.REMOVE);
            }
        }
        markers.clear();
    }

    /// Spawns a marker for every face explicitly configured as input or output.
    @Nonnull
    static <TContainer> List<Ref<EntityStore>> spawn(
            @Nonnull LogisticPipeComponent<TContainer> pipe,
            @Nonnull Vector3i blockWorldPos,
            @Nonnull Store<EntityStore> store) {

        var spawned = new ArrayList<Ref<EntityStore>>();

        for (var face : PLACEMENTS.keySet()) {
            var neighbor = pipe.getNeighbor(face);
            if (neighbor == null || !pipe.isConnectedTo(neighbor)) continue;

            BlockFaceConfigType configType;
            if (pipe.canPullFrom(neighbor)) {
                configType = BlockFaceConfigType.INPUT;
            } else if (pipe.canPushTo(neighbor)) {
                configType = BlockFaceConfigType.OUTPUT;
            } else {
                // A plain two-way connection is already drawn by the block model.
                continue;
            }

            var modelAsset = pipe.getConnectionModelAssetFor(configType);
            if (modelAsset == null) continue;

            var placement = PLACEMENTS.get(face);
            var position = new Vector3d(blockWorldPos.x, blockWorldPos.y, blockWorldPos.z)
                    .add(placement.offset());

            spawned.add(addMarker(store, buildModel(modelAsset, placement.rotation()),
                    position, placement.rotation(), pipe, face));
        }

        return spawned;
    }

    /// Builds the marker's model with a bounding box that matches how it is rotated.
    ///
    /// An entity's model bounding box is axis aligned and is not rotated along with the
    /// transform, so an arm pointing sideways would otherwise keep the upright box it was
    /// authored with -- leaving nothing to aim at where the arm actually is. The box is
    /// supplied up front rather than mutated afterwards, so the asset's own box is never
    /// touched and markers cannot fight over a shared instance.
    @Nonnull
    private static Model buildModel(@Nonnull ModelAsset modelAsset, @Nonnull Rotation3f rotation) {
        // Box hugs the connection geometry: it is what the player aims at, so any padding
        // here steals clicks from the block behind it -- including break attempts.
        var assetBox = modelAsset.getBoundingBox();
        Box rotatedBox = assetBox.enclosingRotatedAABB(rotation.pitch(), rotation.yaw(), rotation.roll());

        return Model.createScaledModel(
                modelAsset, MARKER_SCALE, modelAsset.generateRandomAttachmentIds(), rotatedBox, true);
    }

    private static Ref<EntityStore> addMarker(
            @Nonnull Store<EntityStore> store,
            @Nonnull Model model,
            @Nonnull Vector3d worldPosition,
            @Nonnull Rotation3f rotation,
            @Nonnull LogisticPipeComponent<?> pipe,
            @Nonnull BlockFace face) {

        Holder<EntityStore> holder = store.getRegistry().newHolder();

        holder.addComponent(TransformComponent.getComponentType(),
                new TransformComponent(worldPosition, rotation));
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
        // Load bearing: EntityStore.NetworkIdSystem queries on NetworkId, so it only ever
        // reallocates an existing id and never assigns one. Without this the marker is not
        // in the (TransformComponent, NetworkId) archetype that NetworkSendableSpatialSystem
        // replicates, so it exists server side and is invisible to every client.
        holder.addComponent(NetworkId.getComponentType(),
                new NetworkId(store.getExternalData().takeNextNetworkId()));
        // The block model omits this arm, so the marker is the only thing the player can
        // aim at when cycling the face back.
        holder.addComponent(LogisticEntityProxyComponent.getComponentType(),
                new LogisticEntityProxyComponent(pipe, face));
        holder.ensureComponent(UUIDComponent.getComponentType());

        return store.addEntity(holder, AddReason.SPAWN);
    }

    /// Placement of a face's marker: offset within the block, and the rotation that aims
    /// the model outwards. Carried over from the previous per-face renderer.
    private record Placement(Vector3d offset, Rotation3f rotation) {
    }
}
