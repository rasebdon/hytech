package com.rasebdon.hytech.core.networks;

import com.hypixel.hytale.protocol.BlockFace;
import com.rasebdon.hytech.core.components.IContainerHolder;
import com.rasebdon.hytech.core.components.LogisticPipeComponent;
import com.rasebdon.hytech.core.events.LogisticChangeType;
import com.rasebdon.hytech.core.events.LogisticNetworkChangedEvent;
import com.rasebdon.hytech.core.face.BlockFaceConfigType;
import com.rasebdon.hytech.core.systems.LogisticTransferTarget;
import com.rasebdon.hytech.energy.util.EventBusUtil;

import java.util.*;

public abstract class LogisticNetwork<TContainer> implements IContainerHolder<TContainer> {

    /**
     * Pipes belonging to this network instance
     */
    protected final Set<LogisticPipeComponent<TContainer>> pipes = new HashSet<>();

    /**
     * Edges whose pipe-source face is configured to allow receiving (INPUT).
     */
    protected final List<LogisticTransferTarget<TContainer>> pullTargets = new ArrayList<>();

    /**
     * Edges whose pipe-source face is configured to allow extracting (OUTPUT or BOTH).
     */
    protected final List<LogisticTransferTarget<TContainer>> pushTargets = new ArrayList<>();

    public Collection<LogisticPipeComponent<TContainer>> getPipes() {
        return Collections.unmodifiableSet(pipes);
    }

    public List<LogisticTransferTarget<TContainer>> getPullTargets() {
        return Collections.unmodifiableList(pullTargets);
    }

    public List<LogisticTransferTarget<TContainer>> getPushTargets() {
        return Collections.unmodifiableList(pushTargets);
    }

    public void addPipe(LogisticPipeComponent<TContainer> pipe) {
        if (pipe == null || pipes.contains(pipe)) return;
        pipes.add(pipe);
        rebuildNetworks();
    }

    public void removePipe(LogisticPipeComponent<TContainer> pipe) {
        if (pipe == null || !pipes.remove(pipe)) return;
        rebuildNetworks();
    }

    /**
     * Call when a pipe’s transfer targets or face configs change
     */
    public void rebuildNetworks() {
        // Split this network into connected components
        List<Set<LogisticPipeComponent<TContainer>>> components = findConnectedComponents();

        // Network fully removed
        if (components.isEmpty()) {
            pipes.clear();
            pullTargets.clear();
            pushTargets.clear();
            EventBusUtil.dispatchIfListening(createEvent(this, LogisticChangeType.REMOVED));
            return;
        }

        // Still a single connected network
        if (components.size() == 1) {
            rebuildTargetsFor(components.getFirst());
            EventBusUtil.dispatchIfListening(createEvent(this, LogisticChangeType.CHANGED));
            return;
        }

        // Create subnetworks
        Iterator<Set<LogisticPipeComponent<TContainer>>> it = components.iterator();
        Set<LogisticPipeComponent<TContainer>> primary = it.next();

        pipes.clear();
        pipes.addAll(primary);
        rebuildTargetsFor(primary);

        while (it.hasNext()) {
            var subnet = createNetwork(it.next());
            EventBusUtil.dispatchIfListening(createEvent(subnet, LogisticChangeType.ADDED));
        }

        EventBusUtil.dispatchIfListening(createEvent(this, LogisticChangeType.CHANGED));
    }

    private void rebuildTargetsFor(Set<LogisticPipeComponent<TContainer>> pipes) {
        pullTargets.clear();
        pushTargets.clear();

        var pipeContainers = new HashSet<TContainer>();
        for (var pipe : pipes) {
            pipeContainers.add(pipe.getContainer());
        }

        for (var pipe : pipes) {
            for (var target : pipe.getTransferTargets()) {
                if (target == null) continue;

                BlockFace from = target.from();
                if (from == null || from == BlockFace.None) continue;

                if (pipeContainers.contains(target.target())) continue;

                if (pipe.getConfigForFace(from) == BlockFaceConfigType.INPUT) {
                    pullTargets.add(target);
                }

                if (pipe.canExtractFromFace(from)) {
                    pushTargets.add(target);
                }
            }
        }
    }

    /**
     * Finds all connected pipe groups using an undirected graph traversal.
     */
    private List<Set<LogisticPipeComponent<TContainer>>> findConnectedComponents() {
        var graph = buildAdjacencyGraph();

        var visited = new HashSet<LogisticPipeComponent<TContainer>>();
        var components = new ArrayList<Set<LogisticPipeComponent<TContainer>>>();

        for (var pipe : pipes) {
            if (visited.contains(pipe)) continue;

            var component = new HashSet<LogisticPipeComponent<TContainer>>();
            var stack = new ArrayDeque<LogisticPipeComponent<TContainer>>();
            stack.push(pipe);

            while (!stack.isEmpty()) {
                var current = stack.pop();
                if (!visited.add(current)) continue;

                component.add(current);

                for (var neighbor : graph.getOrDefault(current, Set.of())) {
                    if (!visited.contains(neighbor)) {
                        stack.push(neighbor);
                    }
                }
            }

            components.add(component);
        }

        return components;
    }

    /**
     * Builds an undirected adjacency graph of pipes based on transfer targets.
     * Direction does NOT matter for connectivity.
     */
    @SuppressWarnings("unchecked")
    private Map<LogisticPipeComponent<TContainer>, Set<LogisticPipeComponent<TContainer>>> buildAdjacencyGraph() {
        Map<LogisticPipeComponent<TContainer>, Set<LogisticPipeComponent<TContainer>>> graph = new HashMap<>();

        for (var pipe : pipes) {
            graph.putIfAbsent(pipe, new HashSet<>());

            for (var edge : pipe.getTransferTargets()) {
                if (edge == null) continue;

                if (edge.target() instanceof LogisticPipeComponent<?> other) {
                    var otherPipe = (LogisticPipeComponent<TContainer>) other;

                    graph.get(pipe).add(otherPipe);
                    graph.computeIfAbsent(otherPipe, p -> new HashSet<>()).add(pipe);
                }
            }
        }

        return graph;
    }

    protected abstract LogisticNetwork<TContainer> createNetwork(Set<LogisticPipeComponent<TContainer>> pipes);

    protected abstract LogisticNetworkChangedEvent<TContainer> createEvent(
            LogisticNetwork<TContainer> network, LogisticChangeType changeType);

    public abstract TContainer getNetworkContainer();

    public abstract void pullFromTargets();

    public abstract void pushToTargets();
}
