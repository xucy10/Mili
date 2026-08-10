package net.minecraft.world.level.pathfinder;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.metrics.MetricCategory;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import org.jspecify.annotations.Nullable;

public class PathFinder {
    private static final float FUDGING = 1.5F;
    private final Node[] neighbors = new Node[32];
    private int maxVisitedNodes;
    public final NodeEvaluator nodeEvaluator;
    private final BinaryHeap openSet = new BinaryHeap();
    private BooleanSupplier captureDebug = () -> false;

    public PathFinder(NodeEvaluator nodeEvaluator, int maxVisitedNodes) {
        this.nodeEvaluator = nodeEvaluator;
        this.maxVisitedNodes = maxVisitedNodes;
    }

    public void setCaptureDebug(BooleanSupplier captureDebug) {
        this.captureDebug = captureDebug;
    }

    public void setMaxVisitedNodes(int maxVisitedNodes) {
        this.maxVisitedNodes = maxVisitedNodes;
    }

    public @Nullable Path findPath(PathNavigationRegion region, Mob mob, Set<BlockPos> targets, float maxRange, int reachRange, float maxVisitedNodesMultiplier) {
        this.openSet.clear();
        this.nodeEvaluator.prepare(region, mob);
        Node start = this.nodeEvaluator.getStart();
        if (start == null) {
            return null;
        } else {
            // Paper start - Perf: remove streams and optimize collection
            List<Map.Entry<Target, BlockPos>> map = Lists.newArrayList();
            for (BlockPos pos : targets) {
                map.add(new java.util.AbstractMap.SimpleEntry<>(this.nodeEvaluator.getTarget(pos.getX(), pos.getY(), pos.getZ()), pos));
            }
            // Paper end - Perf: remove streams and optimize collection
            Path path = this.findPath(start, map, maxRange, reachRange, maxVisitedNodesMultiplier);
            this.nodeEvaluator.done();
            return path;
        }
    }

    private @Nullable Path findPath(Node node, List<Map.Entry<Target, BlockPos>> positions, float maxRange, int reachRange, float maxVisitedNodesMultiplier) { // Paper - optimize collection
        ProfilerFiller profilerFiller = Profiler.get();
        profilerFiller.push("find_path");
        profilerFiller.markForCharting(MetricCategory.PATH_FINDING);
        // Set<Target> set = targetPositions.keySet(); // Paper - unused
        node.g = 0.0F;
        node.h = this.getBestH(node, positions); // Paper - optimize collection
        node.f = node.h;
        this.openSet.clear();
        this.openSet.insert(node);
        boolean asBoolean = this.captureDebug.getAsBoolean();
        Set<Node> set1 = asBoolean ? new HashSet<>() : Set.of();
        int i = 0;
        List<Map.Entry<Target, BlockPos>> entryList = Lists.newArrayListWithExpectedSize(positions.size()); // Paper - optimize collection
        int i1 = (int)(this.maxVisitedNodes * maxVisitedNodesMultiplier);

        while (!this.openSet.isEmpty()) {
            if (++i >= i1) {
                break;
            }

            Node node1 = this.openSet.pop();
            node1.closed = true;

            // Paper start - optimize collection
            for (int positionIndex = 0, size = positions.size(); positionIndex < size; positionIndex++) {
                final Map.Entry<Target, BlockPos> entry = positions.get(positionIndex);
                Target target = entry.getKey();
                if (node1.distanceManhattan(target) <= reachRange) {
                    target.setReached();
                    entryList.add(entry);
                    // Paper end - Perf: remove streams and optimize collection
                }
            }

            if (!entryList.isEmpty()) { // Paper - Perf: remove streams and optimize collection; rename
                break;
            }

            if (asBoolean) {
                set1.add(node1);
            }

            if (!(node1.distanceTo(node) >= maxRange)) {
                int neighbors = this.nodeEvaluator.getNeighbors(this.neighbors, node1);

                for (int i2 = 0; i2 < neighbors; i2++) {
                    Node node2 = this.neighbors[i2];
                    float f = this.distance(node1, node2);
                    node2.walkedDistance = node1.walkedDistance + f;
                    float f1 = node1.g + f + node2.costMalus;
                    if (node2.walkedDistance < maxRange && (!node2.inOpenSet() || f1 < node2.g)) {
                        node2.cameFrom = node1;
                        node2.g = f1;
                        node2.h = this.getBestH(node2, positions) * 1.5F; // Paper - Perf: remove streams and optimize collection
                        if (node2.inOpenSet()) {
                            this.openSet.changeCost(node2, node2.g + node2.h);
                        } else {
                            node2.f = node2.g + node2.h;
                            this.openSet.insert(node2);
                        }
                    }
                }
            }
        }

        // Paper start - Perf: remove streams and optimize collection
        Path best = null;
        boolean entryListIsEmpty = entryList.isEmpty();
        Comparator<Path> comparator = entryListIsEmpty
            ? Comparator.comparingInt(Path::getNodeCount)
            : Comparator.comparingDouble(Path::getDistToTarget).thenComparingInt(Path::getNodeCount);
        for (Map.Entry<Target, BlockPos> entry : entryListIsEmpty ? positions : entryList) {
            Path path = this.reconstructPath(entry.getKey().getBestNode(), entry.getValue(), !entryListIsEmpty);
            if (best == null || comparator.compare(path, best) < 0) {
                best = path;
            }
        }
        profilerFiller.pop();
        if(asBoolean && best != null) {
            Set<Target> set = Sets.newHashSet();
            for(Map.Entry<Target, BlockPos> entry : positions) {
                set.add(entry.getKey());
            }
            best.setDebug(this.openSet.getHeap(), set1.toArray(Node[]::new), set);
        }
        return best;
        // Paper end - Perf: remove streams and optimize collection
    }

    protected float distance(Node first, Node second) {
        return first.distanceTo(second);
    }

    private float getBestH(Node node, List<Map.Entry<Target, BlockPos>> targets) { // Paper - Perf: remove streams and optimize collection; Set<Target> -> List<Map.Entry<Target, BlockPos>>
        float f = Float.MAX_VALUE;

        // Paper start - Perf: remove streams and optimize collection
        for (int i = 0, targetsSize = targets.size(); i < targetsSize; i++) {
            final Target target = targets.get(i).getKey();
            // Paper end - Perf: remove streams and optimize collection
            float f1 = node.distanceTo(target);
            target.updateBest(f1, node);
            f = Math.min(f1, f);
        }

        return f;
    }

    private Path reconstructPath(Node node, BlockPos targetPos, boolean reachesTarget) {
        List<Node> list = Lists.newArrayList();
        Node node1 = node;
        list.add(0, node);

        while (node1.cameFrom != null) {
            node1 = node1.cameFrom;
            list.add(0, node1);
        }

        return new Path(list, targetPos, reachesTarget);
    }
}
