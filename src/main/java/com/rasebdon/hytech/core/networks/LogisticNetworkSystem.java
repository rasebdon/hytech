package com.rasebdon.hytech.core.networks;

import com.hypixel.hytale.logger.HytaleLogger;
import com.rasebdon.hytech.core.components.LogisticPipeComponent;
import com.rasebdon.hytech.core.events.LogisticChangeType;
import com.rasebdon.hytech.core.events.LogisticContainerChangedEvent;
import com.rasebdon.hytech.core.events.LogisticNetworkChangedEvent;
import com.rasebdon.hytech.core.util.EventBusUtil;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

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
            handlePipeAdded(pipe);
        } else if (event.isChanged()) {
            handlePipeChanged(pipe);
        } else if (event.isRemoved()) {
            handlePipeRemoved(pipe);
        }
    }

    private void handlePipeAdded(LogisticPipeComponent<TContainer> pipe) {
        var neighborNetworks = findNeighborNetworks(pipe);

        LOGGER.atInfo().log(
                "Pipe added, neighbor networks: %d",
                neighborNetworks.size()
        );

        if (neighborNetworks.isEmpty()) {
            createNewNetwork(pipe);
        } else if (neighborNetworks.size() == 1) {
            joinExistingNetwork(pipe, neighborNetworks.iterator().next());
        } else {
            mergeNetworksAndAddPipe(neighborNetworks, pipe);
        }
    }

    private void handlePipeChanged(LogisticPipeComponent<TContainer> pipe) {
        var network = pipe.getNetwork();

        LOGGER.atInfo().log(
                "Pipe changed, network present: %b",
                network != null
        );

        if (network == null) {
            // Treat as newly added
            handlePipeAdded(pipe);
            return;
        }

        splitNetworkIfNeeded(network);
    }

    private void handlePipeRemoved(LogisticPipeComponent<TContainer> pipe) {
        var network = pipe.getNetwork();

        LOGGER.atInfo().log(
                "Pipe removed, network present: %b",
                network != null
        );

        if (network == null) {
            return;
        }

        network.removePipe(pipe);

        splitNetworkIfNeeded(network);
    }

    private void createNewNetwork(LogisticPipeComponent<TContainer> pipe) {
        LOGGER.atInfo().log("Creating new logistic network");

        var network = createNetwork(Set.of(pipe));

        networks.add(network);
        dispatch(network, LogisticChangeType.ADDED);
    }

    private void joinExistingNetwork(
            LogisticPipeComponent<TContainer> pipe,
            LogisticNetwork<TContainer> network
    ) {
        LOGGER.atInfo().log("Joining existing network");

        network.addPipe(pipe);
        dispatch(network, LogisticChangeType.CHANGED);
    }

    private void mergeNetworksAndAddPipe(
            Set<LogisticNetwork<TContainer>> neighborNetworks,
            LogisticPipeComponent<TContainer> pipe
    ) {
        Iterator<LogisticNetwork<TContainer>> iterator = neighborNetworks.iterator();
        LogisticNetwork<TContainer> primary = iterator.next();

        LOGGER.atInfo().log(
                "Merging %d networks",
                neighborNetworks.size()
        );

        while (iterator.hasNext()) {
            LogisticNetwork<TContainer> other = iterator.next();

            for (var otherPipe : new HashSet<>(other.getPipes())) {
                primary.addPipe(otherPipe);
            }

            networks.remove(other);
            dispatch(other, LogisticChangeType.REMOVED);
        }

        primary.addPipe(pipe);
        dispatch(primary, LogisticChangeType.CHANGED);
    }

    private void splitNetworkIfNeeded(LogisticNetwork<TContainer> network) {
        var components = NetworkGraphUtil.findConnectedComponents(network.getPipes());

        if (components.isEmpty()) {
            LOGGER.atInfo().log("Removing empty network");
            networks.remove(network);
            dispatch(network, LogisticChangeType.REMOVED);
            return;
        }

        if (components.size() == 1) {
            LOGGER.atInfo().log("Network remains connected");
            dispatch(network, LogisticChangeType.CHANGED);
            return;
        }

        LOGGER.atInfo().log(
                "Splitting network into %d networks",
                components.size()
        );

        Iterator<Set<LogisticPipeComponent<TContainer>>> iterator = components.iterator();

        // Reuse original network
        Set<LogisticPipeComponent<TContainer>> primaryPipes = iterator.next();
        network.setPipes(primaryPipes);
        dispatch(network, LogisticChangeType.CHANGED);

        // Create subnetworks
        while (iterator.hasNext()) {
            Set<LogisticPipeComponent<TContainer>> pipes = iterator.next();
            LogisticNetwork<TContainer> subNetwork = createNetwork(pipes);

            networks.add(subNetwork);
            dispatch(subNetwork, LogisticChangeType.ADDED);
        }
    }

    private Set<LogisticNetwork<TContainer>> findNeighborNetworks(
            LogisticPipeComponent<TContainer> pipe
    ) {
        Set<LogisticNetwork<TContainer>> result = new HashSet<>();

        for (var neighbor : pipe.getNeighbors()) {
            if (neighbor instanceof LogisticPipeComponent<TContainer> neighborPipe) {
                var neighborNetwork = neighborPipe.getNetwork();
                if (neighborNetwork != null) {
                    result.add(neighborNetwork);
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
