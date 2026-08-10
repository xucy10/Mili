package net.minecraft.util;

import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class Graph {
    private Graph() {
    }

    public static <T> boolean depthFirstSearch(Map<T, Set<T>> graph, Set<T> nonCyclicalNodes, Set<T> pathSet, Consumer<T> onNonCyclicalNodeFound, T currentNode) {
        if (nonCyclicalNodes.contains(currentNode)) {
            return false;
        } else if (pathSet.contains(currentNode)) {
            return true;
        } else {
            pathSet.add(currentNode);

            for (T object : graph.getOrDefault(currentNode, ImmutableSet.of())) {
                if (depthFirstSearch(graph, nonCyclicalNodes, pathSet, onNonCyclicalNodeFound, object)) {
                    return true;
                }
            }

            pathSet.remove(currentNode);
            nonCyclicalNodes.add(currentNode);
            onNonCyclicalNodeFound.accept(currentNode);
            return false;
        }
    }
}
