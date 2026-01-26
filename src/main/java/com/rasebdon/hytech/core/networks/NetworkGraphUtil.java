package com.rasebdon.hytech.core.networks;

import com.rasebdon.hytech.core.components.LogisticPipeComponent;

import java.util.*;

public final class NetworkGraphUtil {

    private NetworkGraphUtil() {
    }

    public static <
            TNetwork extends LogisticNetwork<TNetwork, TPipe, TContainer>,
            TPipe extends LogisticPipeComponent<TNetwork, TPipe, TContainer>,
            TContainer
            > List<Set<TPipe>> findConnectedComponents(Collection<TPipe> pipes) {

        Map<TPipe, Set<TPipe>> graph = buildGraph(pipes);

        Set<TPipe> visited = new HashSet<>();
        List<Set<TPipe>> result = new ArrayList<>();

        for (TPipe pipe : pipes) {
            if (visited.contains(pipe)) continue;

            Set<TPipe> component = new HashSet<>();
            Deque<TPipe> stack = new ArrayDeque<>();
            stack.push(pipe);

            while (!stack.isEmpty()) {
                TPipe current = stack.pop();
                if (!visited.add(current)) continue;

                component.add(current);

                for (TPipe neighbor : graph.getOrDefault(current, Set.of())) {
                    if (!visited.contains(neighbor)) {
                        stack.push(neighbor);
                    }
                }
            }

            result.add(component);
        }

        return result;
    }

    private static <
            TNetwork extends LogisticNetwork<TNetwork, TPipe, TContainer>,
            TPipe extends LogisticPipeComponent<TNetwork, TPipe, TContainer>,
            TContainer
            > Map<TPipe, Set<TPipe>> buildGraph(Collection<TPipe> pipes) {

        Map<TPipe, Set<TPipe>> graph = new HashMap<>();

        for (TPipe pipe : pipes) {
            graph.putIfAbsent(pipe, new HashSet<>());

            for (var target : pipe.getTransferTargets()) {
                // If getTransferTargets returns LogisticPipeComponent or a subclass,
                // the compiler now knows it MUST be a TPipe because of our class constraints.
                if (target.target() instanceof LogisticPipeComponent<?, ?, TContainer> other) {
                    @SuppressWarnings("unchecked")
                    TPipe neighbor = (TPipe) other;

                    graph.get(pipe).add(neighbor);
                    graph.computeIfAbsent(neighbor, _ -> new HashSet<>()).add(pipe);
                }
            }
        }
        return graph;
    }
}