package at.rasebdon.hytech.core.networks;

import at.rasebdon.hytech.core.components.LogisticPipeComponent;

import java.util.*;

public final class NetworkGraphUtil {

    private NetworkGraphUtil() {
    }

    public static <TContainer> List<Set<LogisticPipeComponent<TContainer>>> findConnectedComponents(
            Collection<LogisticPipeComponent<TContainer>> pipes) {
        var graph = buildGraph(pipes);

        var visited = new HashSet<LogisticPipeComponent<TContainer>>();
        var result = new ArrayList<Set<LogisticPipeComponent<TContainer>>>();

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

            result.add(component);
        }

        return result;
    }

    private static <TContainer> Map<LogisticPipeComponent<TContainer>, Set<LogisticPipeComponent<TContainer>>> buildGraph(
            Collection<LogisticPipeComponent<TContainer>> pipes) {

        Map<LogisticPipeComponent<TContainer>, Set<LogisticPipeComponent<TContainer>>> graph = new HashMap<>();

        for (var pipe : pipes) {
            graph.putIfAbsent(pipe, new HashSet<>());

            for (var neighbor : pipe.getNeighbors()) {
                if (neighbor.getLogisticContainer() instanceof LogisticPipeComponent<TContainer> neighborPipe) {
                    graph.get(pipe).add(neighborPipe);
                    graph.computeIfAbsent(neighborPipe, _ -> new HashSet<>()).add(pipe);
                }
            }
        }
        return graph;
    }
}