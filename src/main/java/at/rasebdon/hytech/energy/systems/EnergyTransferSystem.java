package at.rasebdon.hytech.energy.systems;

import at.rasebdon.hytech.core.components.ContainerHolder;
import at.rasebdon.hytech.core.systems.AbstractTransferSystem;
import at.rasebdon.hytech.energy.HytechEnergyContainer;
import at.rasebdon.hytech.energy.events.EnergyContainerChangedEvent;
import at.rasebdon.hytech.energy.events.EnergyNetworkChangedEvent;
import com.hypixel.hytale.event.IEventRegistry;

/// Energy transfer. The algorithm lives in [AbstractTransferSystem]; all that is energy
/// specific is the per-pass delta snapshot the battery UI reads.
public class EnergyTransferSystem extends AbstractTransferSystem<HytechEnergyContainer> {

    public EnergyTransferSystem(IEventRegistry eventRegistry) {
        super(eventRegistry, EnergyContainerChangedEvent.class, EnergyNetworkChangedEvent.class);
    }

    /// Energy moves every tick, so `MaxTransfer` is per tick for energy blocks.
    @Override
    protected float getTransferIntervalSeconds() {
        return 0f;
    }

    @Override
    protected void onBeforePass(ContainerHolder<HytechEnergyContainer> holder) {
        if (!holder.isAvailable()) return;

        var container = holder.getContainer();
        if (container == null) return;

        container.updateDelta();
    }
}
