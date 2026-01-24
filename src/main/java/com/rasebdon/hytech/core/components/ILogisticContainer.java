package com.rasebdon.hytech.core.components;

import com.hypixel.hytale.protocol.BlockFace;
import com.rasebdon.hytech.energy.IEnergyContainer;

public interface ILogisticContainer {
    void tryAddTransferTarget(IEnergyContainer target, BlockFace from, BlockFace to);

    void removeTransferTarget(IEnergyContainer target);

    void clearTransferTargets();
}
