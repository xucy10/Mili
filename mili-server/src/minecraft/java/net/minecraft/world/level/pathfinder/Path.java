package net.minecraft.world.level.pathfinder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class Path {
    public static final StreamCodec<FriendlyByteBuf, Path> STREAM_CODEC = StreamCodec.of((buffer, value) -> value.writeToStream(buffer), Path::createFromStream);
    public final List<Node> nodes;
    private Path.@Nullable DebugData debugData;
    private int nextNodeIndex;
    private final BlockPos target;
    private final float distToTarget;
    private final boolean reached;

    public Path(List<Node> nodes, BlockPos target, boolean reached) {
        this.nodes = nodes;
        this.target = target;
        this.distToTarget = nodes.isEmpty() ? Float.MAX_VALUE : this.nodes.get(this.nodes.size() - 1).distanceManhattan(this.target);
        this.reached = reached;
    }

    public void advance() {
        this.nextNodeIndex++;
    }

    public boolean notStarted() {
        return this.nextNodeIndex <= 0;
    }

    public boolean isDone() {
        return this.nextNodeIndex >= this.nodes.size();
    }

    public @Nullable Node getEndNode() {
        return !this.nodes.isEmpty() ? this.nodes.get(this.nodes.size() - 1) : null;
    }

    public Node getNode(int index) {
        return this.nodes.get(index);
    }

    public void truncateNodes(int length) {
        if (this.nodes.size() > length) {
            this.nodes.subList(length, this.nodes.size()).clear();
        }
    }

    public void replaceNode(int index, Node node) {
        this.nodes.set(index, node);
    }

    public int getNodeCount() {
        return this.nodes.size();
    }

    public int getNextNodeIndex() {
        return this.nextNodeIndex;
    }

    public void setNextNodeIndex(int currentIndex) {
        this.nextNodeIndex = currentIndex;
    }

    public Vec3 getEntityPosAtNode(Entity entity, int index) {
        Node node = this.nodes.get(index);
        double d = node.x + (int)(entity.getBbWidth() + 1.0F) * 0.5;
        double d1 = node.y;
        double d2 = node.z + (int)(entity.getBbWidth() + 1.0F) * 0.5;
        return new Vec3(d, d1, d2);
    }

    public BlockPos getNodePos(int index) {
        return this.nodes.get(index).asBlockPos();
    }

    public Vec3 getNextEntityPos(Entity entity) {
        return this.getEntityPosAtNode(entity, this.nextNodeIndex);
    }

    public BlockPos getNextNodePos() {
        return this.nodes.get(this.nextNodeIndex).asBlockPos();
    }

    public Node getNextNode() {
        return this.nodes.get(this.nextNodeIndex);
    }

    public @Nullable Node getPreviousNode() {
        return this.nextNodeIndex > 0 ? this.nodes.get(this.nextNodeIndex - 1) : null;
    }

    public boolean sameAs(@Nullable Path path) {
        return path != null && this.nodes.equals(path.nodes);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Path path
            && this.nextNodeIndex == path.nextNodeIndex
            && this.debugData == path.debugData
            && this.reached == path.reached
            && this.target.equals(path.target)
            && this.nodes.equals(path.nodes);
    }

    @Override
    public int hashCode() {
        return this.nextNodeIndex + this.nodes.hashCode() * 31;
    }

    public boolean canReach() {
        return this.reached;
    }

    @VisibleForDebug
    void setDebug(Node[] openSet, Node[] closedSet, Set<Target> targetNodes) {
        this.debugData = new Path.DebugData(openSet, closedSet, targetNodes);
    }

    public Path.@Nullable DebugData debugData() {
        return this.debugData;
    }

    public void writeToStream(FriendlyByteBuf buffer) {
        if (this.debugData != null && !this.debugData.targetNodes.isEmpty()) {
            buffer.writeBoolean(this.reached);
            buffer.writeInt(this.nextNodeIndex);
            buffer.writeBlockPos(this.target);
            buffer.writeCollection(this.nodes, (buffer1, value) -> value.writeToStream(buffer1));
            this.debugData.write(buffer);
        } else {
            throw new IllegalStateException("Missing debug data");
        }
    }

    public static Path createFromStream(FriendlyByteBuf buffer) {
        boolean _boolean = buffer.readBoolean();
        int _int = buffer.readInt();
        BlockPos blockPos = buffer.readBlockPos();
        List<Node> list = buffer.readList(Node::createFromStream);
        Path.DebugData debugData = Path.DebugData.read(buffer);
        Path path = new Path(list, blockPos, _boolean);
        path.debugData = debugData;
        path.nextNodeIndex = _int;
        return path;
    }

    @Override
    public String toString() {
        return "Path(length=" + this.nodes.size() + ")";
    }

    public BlockPos getTarget() {
        return this.target;
    }

    public float getDistToTarget() {
        return this.distToTarget;
    }

    static Node[] readNodeArray(FriendlyByteBuf buffer) {
        Node[] nodes = new Node[buffer.readVarInt()];

        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = Node.createFromStream(buffer);
        }

        return nodes;
    }

    static void writeNodeArray(FriendlyByteBuf buffer, Node[] nodeArray) {
        buffer.writeVarInt(nodeArray.length);

        for (Node node : nodeArray) {
            node.writeToStream(buffer);
        }
    }

    public Path copy() {
        Path path = new Path(this.nodes, this.target, this.reached);
        path.debugData = this.debugData;
        path.nextNodeIndex = this.nextNodeIndex;
        return path;
    }

    public record DebugData(Node[] openSet, Node[] closedSet, Set<Target> targetNodes) {
        public void write(FriendlyByteBuf buffer) {
            buffer.writeCollection(this.targetNodes, (buffer1, value) -> value.writeToStream(buffer1));
            Path.writeNodeArray(buffer, this.openSet);
            Path.writeNodeArray(buffer, this.closedSet);
        }

        public static Path.DebugData read(FriendlyByteBuf buffer) {
            HashSet<Target> set = buffer.readCollection(HashSet::new, Target::createFromStream);
            Node[] nodeArray = Path.readNodeArray(buffer);
            Node[] nodeArray1 = Path.readNodeArray(buffer);
            return new Path.DebugData(nodeArray, nodeArray1, set);
        }
    }
}
