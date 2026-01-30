package com.rasebdon.hytech.core.transport;

import com.hypixel.hytale.protocol.BlockFace;
import com.rasebdon.hytech.core.components.IContainerHolder;

import javax.annotation.Nullable;
import java.util.Set;

public interface ILogisticContainerHolder<TContainer> extends IContainerHolder<TContainer> {
    @Nullable
    ILogisticContainerHolder<TContainer> getNeighborContainer(BlockFace face);

    @Nullable
    BlockFace getNeighborFace(ILogisticContainerHolder<TContainer> neighbor);

    Set<ILogisticContainerHolder<TContainer>> getNeighbors();

    void addNeighbor(BlockFace face, ILogisticContainerHolder<TContainer> neighbor);

    void removeNeighbor(ILogisticContainerHolder<TContainer> neighbor);

    void clearNeighbors();

    // Config
    BlockFaceConfigType getFaceConfigTowards(ILogisticContainerHolder<TContainer> neighbor);

    BlockFaceConfigType getFaceConfigTowards(BlockFace face);

    boolean hasInputFaceTowards(ILogisticContainerHolder<TContainer> neighbor);

    boolean hasOutputFaceTowards(ILogisticContainerHolder<TContainer> neighbor);

    void cycleBlockFaceConfig(BlockFace face);
}
