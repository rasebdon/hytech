package at.rasebdon.hytech.heat.systems;

import at.rasebdon.hytech.core.systems.AbstractTransferSystem;
import at.rasebdon.hytech.heat.HytechHeatContainer;
import at.rasebdon.hytech.heat.events.HeatContainerChangedEvent;
import at.rasebdon.hytech.heat.events.HeatNetworkChangedEvent;
import com.hypixel.hytale.event.IEventRegistry;

/// Heat transfer. The algorithm lives in [AbstractTransferSystem].
public class HeatTransferSystem extends AbstractTransferSystem<HytechHeatContainer> {

    public HeatTransferSystem(IEventRegistry eventRegistry) {
        super(eventRegistry, HeatContainerChangedEvent.class, HeatNetworkChangedEvent.class);
    }
}
