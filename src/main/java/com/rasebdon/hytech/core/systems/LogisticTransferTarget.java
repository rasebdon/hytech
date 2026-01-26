package com.rasebdon.hytech.core.systems;

import com.hypixel.hytale.protocol.BlockFace;

/**
 * A directed transfer edge:
 * source (extracts) -> target (receives)
 *
 * @param from Source-local face
 * @param to   Target-local face
 */
public record LogisticTransferTarget<TContainer>(
        TContainer source,
        TContainer target,
        BlockFace from,
        BlockFace to
) {
}
