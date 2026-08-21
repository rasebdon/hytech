package at.rasebdon.hytech.energy.systems.visual;

import at.rasebdon.hytech.core.systems.AbstractBlockStateSystem;
import at.rasebdon.hytech.energy.components.EnergyBlockComponent;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nullable;

/// Shows a container's charge level, for blocks that declare `EnergyLevelBlockStates`.
public final class EnergyBlockStateSystem extends AbstractBlockStateSystem<EnergyBlockComponent> {

    private static final float UPDATE_INTERVAL_SECONDS = 1f;

    public EnergyBlockStateSystem(ComponentType<ChunkStore, EnergyBlockComponent> componentType) {
        super(componentType, UPDATE_INTERVAL_SECONDS);
    }

    @Override
    @Nullable
    protected String resolveState(@NonNull EnergyBlockComponent component) {
        return component.getEnergyLevelBlockState();
    }
}
