package at.rasebdon.hytech.core.networks;

import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticContainerChangedEvent;
import at.rasebdon.hytech.core.events.LogisticNetworkChangedEvent;
import at.rasebdon.hytech.core.util.EventBusUtil;
import com.hypixel.hytale.logger.HytaleLogger;

import java.util.*;

public abstract class LogisticNetworkSystem<TContainer> {

    protected static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    protected final Set<LogisticNetwork<TContainer>> networks = new HashSet<>();

    public Set<LogisticNetwork<TContainer>> getNetworks() {
        return Collections.unmodifiableSet(networks);
    }

    public void onContainerChanged(LogisticContainerChangedEvent<TContainer> event) {
        if (!(event.getComponent() instanceof LogisticPipeComponent<TContainer> pipe)) {
            return;
        }

        if (event.isAdded()) {
            onPipeAdded(pipe);
        } else if (event.isRemoved()) {
            onPipeRemoved(pipe);
        } else if (event.isChanged()) {
            onPipeChanged(pipe);
        }
    }

    private void onPipeAdded(LogisticPipeComponent<TContainer> pipe) {
        LOGGER.atInfo().log("Pipe added");

        Set<LogisticNetwork<TContainer>> neighbors = findNeighborNetworks(pipe);

        if (neighbors.isEmpty()) {
            createStandaloneNetwork(pipe);
            return;
        }

        if (neighbors.size() == 1) {
            attachPipeToNetwork(pipe, neighbors.iterator().next());
            return;
        }

        mergeNetworks(pipe, neighbors);
    }

    private void onPipeRemoved(LogisticPipeComponent<TContainer> pipe) {
        LOGGER.atInfo().log("Pipe removed");

        LogisticNetwork<TContainer> network = pipe.getNetwork();
        if (network == null) {
            return;
        }

        detachPipeFromNetwork(pipe, network);
        rebuildNetwork(network);
    }

    private void onPipeChanged(LogisticPipeComponent<TContainer> pipe) {
        LOGGER.atInfo().log("Pipe changed");

        LogisticNetwork<TContainer> network = pipe.getNetwork();
        if (network == null) {
            // Treat as new if it somehow lost its network
            onPipeAdded(pipe);
            return;
        }

        rebuildNetwork(network);
    }

    private void createStandaloneNetwork(LogisticPipeComponent<TContainer> pipe) {
        LOGGER.atInfo().log("Creating new standalone network");

        LogisticNetwork<TContainer> network = createNetwork(Set.of(pipe));
        networks.add(network);

        dispatch(network, LogisticChangeType.ADDED);
    }

    private void attachPipeToNetwork(
            LogisticPipeComponent<TContainer> pipe,
            LogisticNetwork<TContainer> network
    ) {
        LOGGER.atInfo().log("Attaching pipe to existing network");

        network.addPipe(pipe);
        dispatch(network, LogisticChangeType.CHANGED);
    }

    private void detachPipeFromNetwork(
            LogisticPipeComponent<TContainer> pipe,
            LogisticNetwork<TContainer> network
    ) {
        LOGGER.atInfo().log("Detaching pipe from network");

        network.removePipe(pipe);
    }

    private void mergeNetworks(
            LogisticPipeComponent<TContainer> newPipe,
            Set<LogisticNetwork<TContainer>> networksToMerge
    ) {
        LOGGER.atInfo().log("Merging %d networks", networksToMerge.size());

        Iterator<LogisticNetwork<TContainer>> it = networksToMerge.iterator();
        LogisticNetwork<TContainer> primary = it.next();

        // Absorb all other networks
        while (it.hasNext()) {
            LogisticNetwork<TContainer> other = it.next();

            for (var pipe : new HashSet<>(other.getPipes())) {
                primary.addPipe(pipe);
            }

            networks.remove(other);
            dispatch(other, LogisticChangeType.REMOVED);
        }

        primary.addPipe(newPipe);
        dispatch(primary, LogisticChangeType.CHANGED);
    }

    private void rebuildNetwork(LogisticNetwork<TContainer> network) {
        LOGGER.atInfo().log("Rebuilding network");

        List<Set<LogisticPipeComponent<TContainer>>> components =
                NetworkGraphUtil.findConnectedComponents(network.getPipes());

        if (components.isEmpty()) {
            LOGGER.atInfo().log("Network became empty, removing");
            networks.remove(network);
            dispatch(network, LogisticChangeType.REMOVED);
            return;
        }

        if (components.size() == 1) {
            network.setPipes(components.getFirst());
            dispatch(network, LogisticChangeType.CHANGED);
            return;
        }

        LOGGER.atInfo().log("Network split into %d subnetworks", components.size());

        Iterator<Set<LogisticPipeComponent<TContainer>>> it = components.iterator();

        // Reuse existing network for first component
        network.setPipes(it.next());
        dispatch(network, LogisticChangeType.CHANGED);

        // Create new networks for remaining components
        while (it.hasNext()) {
            LogisticNetwork<TContainer> sub = createNetwork(it.next());
            networks.add(sub);
            dispatch(sub, LogisticChangeType.ADDED);
        }
    }

    private Set<LogisticNetwork<TContainer>> findNeighborNetworks(
            LogisticPipeComponent<TContainer> pipe
    ) {
        Set<LogisticNetwork<TContainer>> result = new HashSet<>();

        for (var neighbor : pipe.getNeighbors()) {
            if (neighbor instanceof LogisticPipeComponent<TContainer> neighborPipe) {
                LogisticNetwork<TContainer> network = neighborPipe.getNetwork();
                if (network != null) {
                    result.add(network);
                }
            }
        }
        return result;
    }

    protected void dispatch(
            LogisticNetwork<TContainer> network,
            LogisticChangeType type
    ) {
        EventBusUtil.dispatchIfListening(createEvent(network, type));
    }

    protected abstract LogisticNetwork<TContainer> createNetwork(
            Set<LogisticPipeComponent<TContainer>> pipes
    );

    protected abstract LogisticNetworkChangedEvent<TContainer> createEvent(
            LogisticNetwork<TContainer> network,
            LogisticChangeType changeType
    );
}
