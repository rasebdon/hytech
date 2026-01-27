package com.rasebdon.hytech.core.systems;

import com.hypixel.hytale.protocol.BlockFace;
import com.rasebdon.hytech.core.components.ILogisticContainerHolder;

/**
 * A directed transfer edge:
 * source (extracts) -> target (receives)
 *
 * @param from Source-local face
 * @param to   Target-local face
 */
public record LogisticTransferTarget<TContainer>(
        ILogisticContainerHolder<TContainer> source,
        ILogisticContainerHolder<TContainer> target,
        BlockFace from,
        BlockFace to
) {
}
