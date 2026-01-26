package com.rasebdon.hytech.core.networks;

import com.rasebdon.hytech.core.components.LogisticPipeComponent;
import com.rasebdon.hytech.core.events.LogisticChangeType;
import com.rasebdon.hytech.core.events.LogisticContainerChangedEvent;
import com.rasebdon.hytech.core.events.LogisticNetworkChangedEvent;
import com.rasebdon.hytech.energy.util.EventBusUtil;

import java.util.HashSet;
import java.util.Set;

public abstract class LogisticNetworkSystem<
        TNetwork extends LogisticNetwork<TNetwork, TPipe, TContainer>,
        TPipe extends LogisticPipeComponent<TNetwork, TPipe, TContainer>,
        TContainer
        > {

    protected Set<TNetwork> networks = new HashSet<>();

    public Set<TNetwork> getNetworks() {
        return networks;
    }

    public void onContainerChanged(LogisticContainerChangedEvent<TContainer> event) {
        if (!(event.getComponent() instanceof LogisticPipeComponent<?, ?, TContainer> component)) {
            return;
        }

        @SuppressWarnings("unchecked")
        TPipe pipe = (TPipe) component;

        if (event.isAdded()) {
            handlePipeAdded(pipe);
        } else if (event.isChanged()) {
            handlePipeChanged(pipe);
        } else if (event.isRemoved()) {
            handlePipeRemoved(pipe);
        }
    }

    private void handlePipeAdded(TPipe pipe) {
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

    private void handlePipeChanged(TPipe pipe) {
        var network = pipe.getNetwork();
        if (network == null) {
            // Was orphaned → treat as add
            handlePipeAdded(pipe);
            return;
        }

        // Re-evaluate connectivity of the entire network
        splitNetworkIfNeeded(network);
    }

    private void handlePipeRemoved(TPipe pipe) {
        var network = pipe.getNetwork();
        if (network == null) return;

        network.detachPipe(pipe);
        splitNetworkIfNeeded(network);
    }

    private void createNewNetwork(TPipe pipe) {
        var network = createNetwork(Set.of(pipe));
        pipe.assignNetwork(network);

        networks.add(network);
        dispatch(network, LogisticChangeType.ADDED);
    }

    private void joinExistingNetwork(
            TPipe pipe,
            TNetwork network
    ) {
        pipe.assignNetwork(network);
        network.rebuildTargets();

        dispatch(network, LogisticChangeType.CHANGED);
    }

    private void mergeNetworksAndAddPipe(
            Set<TNetwork> networks,
            TPipe pipe
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

    private void splitNetworkIfNeeded(TNetwork network) {
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

    private Set<TNetwork> findNeighborNetworks(TPipe pipe) {
        var result = new HashSet<TNetwork>();

        for (var target : pipe.getTransferTargets()) {
            if (target.target() instanceof LogisticPipeComponent<?, ?, TContainer> neighborComponent) {
                @SuppressWarnings("unchecked")
                TPipe neighbor = (TPipe) neighborComponent;
                TNetwork neighborNetwork = neighbor.getNetwork();

                if (neighborNetwork != null) {
                    result.add(neighborNetwork);
                }
            }
        }
        return result;
    }

    protected void dispatch(TNetwork network, LogisticChangeType type) {
        EventBusUtil.dispatchIfListening(createEvent(network, type));
    }

    protected abstract TNetwork createNetwork(Set<TPipe> pipes);

    protected abstract LogisticNetworkChangedEvent<TNetwork, TPipe, TContainer> createEvent(
            TNetwork network,
            LogisticChangeType changeType
    );
}
