package com.rasebdon.hytech.core.components;

import com.hypixel.hytale.protocol.BlockFace;

public interface ILogisticContainerHolder<TContainer> extends IContainerHolder<TContainer> {
    void tryAddTransferTarget(ILogisticContainerHolder<TContainer> target, BlockFace from, BlockFace to);

    void removeTransferTarget(ILogisticContainerHolder<TContainer> target);

    void clearTransferTargets();
}
