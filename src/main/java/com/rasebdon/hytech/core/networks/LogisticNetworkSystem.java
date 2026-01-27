package com.rasebdon.hytech.core.networks;

import com.rasebdon.hytech.core.components.LogisticPipeComponent;
import com.rasebdon.hytech.core.events.LogisticChangeType;
import com.rasebdon.hytech.core.events.LogisticContainerChangedEvent;
import com.rasebdon.hytech.core.events.LogisticNetworkChangedEvent;
import com.rasebdon.hytech.energy.util.EventBusUtil;

import java.util.HashSet;
import java.util.Set;

public abstract class LogisticNetworkSystem<TContainer> {

    protected Set<LogisticNetwork<TContainer>> networks = new HashSet<>();

    public Set<LogisticNetwork<TContainer>> getNetworks() {
        return networks;
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
        var neighboringNetworks = findNeighborNetworks(pipe);

        if (neighboringNetworks.isEmpty()) {
            createNewNetwork(pipe);
            return;
        }

        if (neighboringNetworks.size() == 1) {
            joinExistingNetwork(pipe, neighboringNetworks.iterator().next());
            return;
        }

        mergeNetworksAndAddPipe(neighboringNetworks, pipe);
    }

    private void handlePipeChanged(LogisticPipeComponent<TContainer> pipe) {
        var network = pipe.getNetwork();
        if (network == null) {
            // Was orphaned → treat as add
            handlePipeAdded(pipe);
            return;
        }

        // Re-evaluate connectivity of the entire network
        splitNetworkIfNeeded(network);
    }

    private void handlePipeRemoved(LogisticPipeComponent<TContainer> pipe) {
        var network = pipe.getNetwork();
        if (network == null) return;

        network.detachPipe(pipe);
        splitNetworkIfNeeded(network);
    }

    private void createNewNetwork(LogisticPipeComponent<TContainer> pipe) {
        var network = createNetwork(Set.of(pipe));
        pipe.assignNetwork(network);

        networks.add(network);
        dispatch(network, LogisticChangeType.ADDED);
    }

    private void joinExistingNetwork(
            LogisticPipeComponent<TContainer> pipe,
            LogisticNetwork<TContainer> network
    ) {
        pipe.assignNetwork(network);
        network.rebuildTargets();

        dispatch(network, LogisticChangeType.CHANGED);
    }

    private void mergeNetworksAndAddPipe(
            Set<LogisticNetwork<TContainer>> networks,
            LogisticPipeComponent<TContainer> pipe
    ) {
        var iterator = networks.iterator();
        var primary = iterator.next();

        // Merge all others into primary
        while (iterator.hasNext()) {
            var other = iterator.next();
            for (var otherPipe : other.getPipes()) {
                otherPipe.assignNetwork(primary);
            }

            networks.remove(other);
            dispatch(other, LogisticChangeType.REMOVED);
        }

        pipe.assignNetwork(primary);
        primary.rebuildTargets();

        dispatch(primary, LogisticChangeType.CHANGED);
    }

    private void splitNetworkIfNeeded(LogisticNetwork<TContainer> network) {
        var components = NetworkGraphUtil.findConnectedComponents(network.getPipes());

        if (components.isEmpty()) {
            networks.remove(network);
            dispatch(network, LogisticChangeType.REMOVED);
            return;
        }

        if (components.size() == 1) {
            network.rebuildTargets();
            dispatch(network, LogisticChangeType.CHANGED);
            return;
        }

        // Reuse original network for first component
        var iterator = components.iterator();
        var primaryPipes = iterator.next();

        network.resetPipes(primaryPipes);
        dispatch(network, LogisticChangeType.CHANGED);

        // Create subnetworks
        while (iterator.hasNext()) {
            var subNetwork = createNetwork(iterator.next());
            networks.add(subNetwork);
            dispatch(subNetwork, LogisticChangeType.ADDED);
        }
    }

    private Set<LogisticNetwork<TContainer>> findNeighborNetworks(LogisticPipeComponent<TContainer> pipe) {
        var result = new HashSet<LogisticNetwork<TContainer>>();

        for (var target : pipe.getTransferTargets()) {
            if (target.target() instanceof LogisticPipeComponent<TContainer> neighborPipe) {
                var neighborNetwork = neighborPipe.getNetwork();

                if (neighborNetwork != null) {
                    result.add(neighborNetwork);
                }
            }
        }
        return result;
    }

    protected void dispatch(LogisticNetwork<TContainer> network, LogisticChangeType type) {
        EventBusUtil.dispatchIfListening(createEvent(network, type));
    }

    protected abstract LogisticNetwork<TContainer> createNetwork(Set<LogisticPipeComponent<TContainer>> pipes);

    protected abstract LogisticNetworkChangedEvent<TContainer> createEvent(
            LogisticNetwork<TContainer> network,
            LogisticChangeType changeType
    );
}
