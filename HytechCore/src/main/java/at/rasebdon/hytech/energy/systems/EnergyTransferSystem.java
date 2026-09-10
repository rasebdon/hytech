package at.rasebdon.hytech.energy.systems;

import at.rasebdon.hytech.core.systems.AbstractTransferSystem;
import at.rasebdon.hytech.energy.HytechEnergyContainer;
import at.rasebdon.hytech.energy.events.EnergyContainerChangedEvent;
import at.rasebdon.hytech.energy.events.EnergyNetworkChangedEvent;
import com.hypixel.hytale.event.IEventRegistry;

/// Energy transfer. The algorithm lives in [AbstractTransferSystem]; energy adds nothing to
/// it beyond the default every-tick pass, so `MaxTransfer` is per tick for energy blocks.
public class EnergyTransferSystem extends AbstractTransferSystem<HytechEnergyContainer> {

    public EnergyTransferSystem(IEventRegistry eventRegistry) {
        super(eventRegistry, EnergyContainerChangedEvent.class, EnergyNetworkChangedEvent.class);
    }
}
