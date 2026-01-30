package com.rasebdon.hytech.core.systems;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.rasebdon.hytech.core.transport.BlockFaceConfigType;

import java.util.EnumMap;
import java.util.Map;

public class PipeRenderHelper {
    private static final EnumMap<BlockFace, PipeFaceRenderData> PIPE_FACE_DATA;

    public static Map<BlockFaceConfigType, String> DEFAULT_CONNECTION_MODEL_ASSETS = Map.of(
            BlockFaceConfigType.BOTH, "Pipe_Normal",
            BlockFaceConfigType.OUTPUT, "Pipe_Push",
            BlockFaceConfigType.INPUT, "Pipe_Pull"
    );

    static {
        PIPE_FACE_DATA = new EnumMap<>(BlockFace.class);

        PIPE_FACE_DATA.put(BlockFace.Up,
                new PipeFaceRenderData(new Vector3d(0.5, 0.0, 0.5), new Vector3f()));
        PIPE_FACE_DATA.put(BlockFace.Down,
                new PipeFaceRenderData(new Vector3d(0.5, 1.0, 0.5),
                        new Vector3f(0f, 0f, (float) Math.toRadians(180))));
        PIPE_FACE_DATA.put(BlockFace.East,
                new PipeFaceRenderData(new Vector3d(0.0, 0.5, 0.5),
                        new Vector3f(0f, 0f, (float) Math.toRadians(-90))));
        PIPE_FACE_DATA.put(BlockFace.West,
                new PipeFaceRenderData(new Vector3d(1.0, 0.5, 0.5),
                        new Vector3f(0f, 0f, (float) Math.toRadians(90))));
        PIPE_FACE_DATA.put(BlockFace.North,
                new PipeFaceRenderData(new Vector3d(0.5, 0.5, 1.0),
                        new Vector3f((float) Math.toRadians(-90), 0f, 0f)));
        PIPE_FACE_DATA.put(BlockFace.South,
                new PipeFaceRenderData(new Vector3d(0.5, 0.5, 0.0),
                        new Vector3f((float) Math.toRadians(90), 0f, 0f)));
    }

    public static PipeFaceRenderData getRenderData(BlockFace face) {
        return PIPE_FACE_DATA.get(face);
    }

    public static void recalculateBoundingBoxForFace(
            Model model,
            BlockFace face
    ) {
        assert model.getBoundingBox() != null;
        var modelBoundingBox = model.getBoundingBox();

        var up_aabb_min = modelBoundingBox.min;
        double minX = up_aabb_min.x;
        double minY = up_aabb_min.y;
        double minZ = up_aabb_min.z;

        var up_aabb_max = modelBoundingBox.max;
        double maxX = up_aabb_max.x;
        double maxY = up_aabb_max.y;
        double maxZ = up_aabb_max.z;

        Vector3d min = new Vector3d();
        Vector3d max = new Vector3d();

        switch (face) {
            case Up -> {
                min.assign(minX, minY, minZ);
                max.assign(maxX, maxY, maxZ);
            }

            case Down -> {
                min.assign(minX, -maxY, minZ);
                max.assign(maxX, -minY, maxZ);
            }

            case South -> {
                min.assign(minX, minZ, minY);
                max.assign(maxX, maxZ, maxY);
            }

            case North -> {
                min.assign(minX, minZ, -maxY);
                max.assign(maxX, maxZ, -minY);
            }

            case East -> {
                min.assign(minY, minZ, minX);
                max.assign(maxY, maxZ, maxX);
            }

            case West -> {
                min.assign(-maxY, minZ, minX);
                max.assign(-minY, maxZ, maxX);
            }

            default -> throw new IllegalStateException(face.name());
        }

        modelBoundingBox.setMinMax(min, max);
    }

    public record PipeFaceRenderData(Vector3d offset, Vector3f rotation) {
    }
}
