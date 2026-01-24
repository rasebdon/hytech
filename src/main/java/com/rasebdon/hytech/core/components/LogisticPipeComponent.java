package com.rasebdon.hytech.core.components;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jetbrains.annotations.Nullable;

public abstract class LogisticPipeComponent<TContainer> implements Component<ChunkStore> {

    @Override
    public @Nullable Component<ChunkStore> clone() {
        return null;
    }


}
