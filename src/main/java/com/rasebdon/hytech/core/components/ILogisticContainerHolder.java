package com.rasebdon.hytech.core.components;

import com.hypixel.hytale.protocol.BlockFace;

public interface ILogisticContainerHolder<TContainer> extends IContainerHolder<TContainer> {
    void tryAddTransferTarget(TContainer target, BlockFace from, BlockFace to);

    void removeTransferTarget(TContainer target);

    void clearTransferTargets();
}
