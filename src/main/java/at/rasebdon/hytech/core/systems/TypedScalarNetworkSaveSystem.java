package at.rasebdon.hytech.core.systems;

import at.rasebdon.hytech.core.components.AbstractScalarPipeComponent;
import at.rasebdon.hytech.core.components.AbstractTypedScalarPipeComponent;
import at.rasebdon.hytech.core.containers.TypedScalarContainer;
import at.rasebdon.hytech.core.networks.LogisticNetwork;
import at.rasebdon.hytech.core.networks.LogisticNetworkSystem;

/// Persists a typed network by writing both the amount and the resource onto each pipe.
///
/// Without the resource id, a reloaded run would come back holding a quantity of nothing --
/// which [at.rasebdon.hytech.core.networks.TypedScalarNetwork] discards on principle, so the
/// contents would simply vanish.
@SuppressWarnings("rawtypes")
public abstract class TypedScalarNetworkSaveSystem<TContainer> extends ScalarNetworkSaveSystem<TContainer> {

    protected TypedScalarNetworkSaveSystem(LogisticNetworkSystem<TContainer> networkSystem) {
        super(networkSystem);
    }

    @Override
    protected void writePipe(
            AbstractScalarPipeComponent pipe,
            long amount,
            LogisticNetwork<TContainer> network) {
        super.writePipe(pipe, amount, network);

        if (!(pipe instanceof AbstractTypedScalarPipeComponent<?> typedPipe)) return;

        // Setting the type after the amount matters: clearing the type zeroes the amount, so
        // doing it the other way round would throw the share away.
        typedPipe.setSavedResourceType(resourceTypeOf(network));
    }

    private String resourceTypeOf(LogisticNetwork<TContainer> network) {
        var container = network.getContainer();

        return container instanceof TypedScalarContainer<?> typed && typed.getResourceType() != null
                ? String.valueOf(typed.getResourceType())
                : null;
    }
}
