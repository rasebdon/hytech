package at.rasebdon.hytech.core.networks;

import at.rasebdon.hytech.core.components.LogisticPipeComponent;
import at.rasebdon.hytech.core.events.LogisticChangeType;
import at.rasebdon.hytech.core.events.LogisticComponentChangedEvent;
import at.rasebdon.hytech.core.events.LogisticNetworkChangedEvent;
import at.rasebdon.hytech.core.util.EventBusUtil;
import com.hypixel.hytale.logger.HytaleLogger;

import java.util.*;

public abstract class LogisticNetworkSystem<TContainer> {

    protected static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * All currently active networks.
     */
    protected final Set<LogisticNetwork<TContainer>> networks = new HashSet<>();

    public Set<LogisticNetwork<TContainer>> getNetworks() {
        return Collections.unmodifiableSet(networks);
    }

    public void onContainerChanged(LogisticComponentChangedEvent<TContainer> event) {

        if (!(event.getComponent() instanceof LogisticPipeComponent<TContainer> pipe)) {
            return;
        }

        if (event.isAdded()) {
            handlePipeAdded(pipe);
        } else if (event.isRemoved()) {
            handlePipeRemoved(pipe);
        } else if (event.isChanged()) {
            handlePipeChanged(pipe);
        }
    }

    private void handlePipeAdded(LogisticPipeComponent<TContainer> pipe) {
        LOGGER.atInfo().log("Pipe added");
        rebuildFromPipe(pipe);
    }

    private void handlePipeRemoved(LogisticPipeComponent<TContainer> pipe) {
        LOGGER.atInfo().log("Pipe removed");

        LogisticNetwork<TContainer> old = pipe.getNetwork();
        if (old == null) {
            return;
        }

        // Remove pipe from its network
        detachPipe(pipe, old);

        // Rebuild remaining structure
        rebuildFromPipes(old.getPipes());
    }

    private void handlePipeChanged(LogisticPipeComponent<TContainer> pipe) {
        LOGGER.atInfo().log("Pipe changed");
        rebuildFromPipe(pipe);
    }

    /**
     * Rebuilds all networks connected to the given pipe.
     */
    private void rebuildFromPipe(LogisticPipeComponent<TContainer> pipe) {

        Set<LogisticPipeComponent<TContainer>> reachable =
                collectReachablePipes(pipe);

        rebuildFromPipes(reachable);
    }

    /**
     * Rebuilds networks from a given pipe set.
     */
    private void rebuildFromPipes(Set<LogisticPipeComponent<TContainer>> pipes) {

        if (pipes.isEmpty()) {
            return;
        }

        // Remove any old networks containing these pipes
        removeOldNetworks(pipes);

        // Find connected components
        List<Set<LogisticPipeComponent<TContainer>>> components =
                NetworkGraphUtil.findConnectedComponents(pipes);

        // Create new networks deterministically
        for (Set<LogisticPipeComponent<TContainer>> component : components) {

            LogisticNetwork<TContainer> network =
                    createNetwork(component);

            assignNetwork(component, network);

            networks.add(network);
            dispatch(network, LogisticChangeType.ADDED);
        }

        validate();
    }

    private Set<LogisticPipeComponent<TContainer>> collectReachablePipes(
            LogisticPipeComponent<TContainer> start
    ) {
        Set<LogisticPipeComponent<TContainer>> visited = new HashSet<>();
        Deque<LogisticPipeComponent<TContainer>> stack = new ArrayDeque<>();
        stack.push(start);

        while (!stack.isEmpty()) {
            var pipe = stack.pop();
            if (!visited.add(pipe)) continue;

            for (var neighbor : pipe.getNeighbors()) {
                var neighborLogistic = neighbor.getLogisticContainer();
                if (neighborLogistic instanceof LogisticPipeComponent<TContainer> neighborPipe) {
                    if (pipe.isConnectedTo(neighbor)) {
                        stack.push(neighborPipe);
                    }
                }
            }
        }

        return visited;
    }

    private void removeOldNetworks(Set<LogisticPipeComponent<TContainer>> pipes) {

        Set<LogisticNetwork<TContainer>> affected = new HashSet<>();

        for (var pipe : pipes) {
            if (pipe.getNetwork() != null) {
                affected.add(pipe.getNetwork());
            }
        }

        for (var network : affected) {

            for (var pipe : new HashSet<>(network.getPipes())) {
                pipe.assignNetwork(null);
            }

            networks.remove(network);
            dispatch(network, LogisticChangeType.REMOVED);
        }
    }

    private void assignNetwork(
            Set<LogisticPipeComponent<TContainer>> pipes,
            LogisticNetwork<TContainer> network
    ) {
        for (var pipe : pipes) {
            pipe.assignNetwork(network);
        }
    }

    private void detachPipe(
            LogisticPipeComponent<TContainer> pipe,
            LogisticNetwork<TContainer> network
    ) {
        network.removePipe(pipe);
        pipe.assignNetwork(null);
    }

    /**
     * Debug invariant check.
     * Ensures no pipe is assigned to multiple networks or desynced.
     */
    private void validate() {

        for (var network : networks) {
            for (var pipe : network.getPipes()) {
                if (pipe.getNetwork() != network) {
                    throw new IllegalStateException(
                            "Pipe/network desync detected"
                    );
                }
            }
        }
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
