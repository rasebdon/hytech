package at.rasebdon.hytech.gas.systems;

import at.rasebdon.hytech.core.systems.AbstractTransferSystem;
import at.rasebdon.hytech.gas.HytechGasContainer;
import at.rasebdon.hytech.gas.events.GasContainerChangedEvent;
import at.rasebdon.hytech.gas.events.GasNetworkChangedEvent;
import com.hypixel.hytale.event.IEventRegistry;

/// Gas transfer. The algorithm lives in [AbstractTransferSystem]; the single-type rule is
/// enforced by the container canAccept check during the move itself.
public class GasTransferSystem extends AbstractTransferSystem<HytechGasContainer> {

    public GasTransferSystem(IEventRegistry eventRegistry) {
        super(eventRegistry, GasContainerChangedEvent.class, GasNetworkChangedEvent.class);
    }
}
