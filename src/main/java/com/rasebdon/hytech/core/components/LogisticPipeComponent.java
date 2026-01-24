package com.rasebdon.hytech.core.components;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.rasebdon.hytech.core.systems.LogisticTransferTarget;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LogisticPipeComponent<TContainer> implements Component<ChunkStore> {

    protected List<LogisticTransferTarget<TContainer>> pullTargets;

    /// Push targets are pipe transfer targets that are either configured Normal (BOTH) or PUSH mode
    protected List<LogisticTransferTarget<TContainer>> pushTargets;

    @Override
    public @Nullable Component<ChunkStore> clone() {
        return null;
    }
}
