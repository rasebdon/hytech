package com.rasebdon.hytech.energy.util;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;

public class BlockFaceUtil {
    public static BlockFace getLocalFace(Vector3i worldDir, RotationTuple rotation) {
        // Apply inverse rotation to the world direction to find the local face
        Rotation invYaw = Rotation.None.subtract(rotation.yaw());
        Rotation invPitch = Rotation.None.subtract(rotation.pitch());
        Rotation invRoll = Rotation.None.subtract(rotation.roll());

        Vector3i localVec = Rotation.rotate(worldDir, invYaw, invPitch, invRoll);

        if (localVec.y > 0) return BlockFace.Up;
        if (localVec.y < 0) return BlockFace.Down;
        if (localVec.z < 0) return BlockFace.North;
        if (localVec.z > 0) return BlockFace.South;
        if (localVec.x > 0) return BlockFace.East;
        if (localVec.x < 0) return BlockFace.West;
        return BlockFace.None;
    }

    public static Vector3i getVectorFromFace(BlockFace face) {
        return switch (face) {
            case Up -> new Vector3i(0, 1, 0);
            case Down -> new Vector3i(0, -1, 0);
            case North -> new Vector3i(0, 0, -1);
            case South -> new Vector3i(0, 0, 1);
            case East -> new Vector3i(1, 0, 0);
            case West -> new Vector3i(-1, 0, 0);
            default -> new Vector3i(0, 0, 0);
        };
    }

}
