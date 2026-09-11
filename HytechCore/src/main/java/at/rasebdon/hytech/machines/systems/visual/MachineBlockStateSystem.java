package at.rasebdon.hytech.machines.systems.visual;

import at.rasebdon.hytech.core.systems.AbstractBlockStateSystem;
import at.rasebdon.hytech.machines.components.MachineProcessorComponent;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

// Both states are named since a block state can't be cleared; stopping switches to Idle.
public final class MachineBlockStateSystem extends AbstractBlockStateSystem<MachineProcessorComponent> {

    private static final String STATE_PROCESSING = "Processing";
    private static final String STATE_IDLE = "Idle";

    private static final float UPDATE_INTERVAL_SECONDS = 0.5f;

    public MachineBlockStateSystem(ComponentType<ChunkStore, MachineProcessorComponent> componentType) {
        super(componentType, UPDATE_INTERVAL_SECONDS);
    }

    @Override
    protected @NotNull String resolveState(@NonNull MachineProcessorComponent component) {
        return component.isActive() ? STATE_PROCESSING : STATE_IDLE;
    }
}
