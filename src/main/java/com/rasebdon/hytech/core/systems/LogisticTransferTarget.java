package com.rasebdon.hytech.core.systems;

import com.hypixel.hytale.protocol.BlockFace;

public record LogisticTransferTarget<TContainer>(
        TContainer target,
        BlockFace from,
        BlockFace to
) {
}
