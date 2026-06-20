package net.minecraft.advancements;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public class AdvancementNode {
    private final AdvancementHolder holder;
    private final @Nullable AdvancementNode parent;
    private final Set<AdvancementNode> children = new ReferenceOpenHashSet<>();

    @VisibleForTesting
    public AdvancementNode(AdvancementHolder holder, @Nullable AdvancementNode parent) {
        this.holder = holder;
        this.parent = parent;
    }

    public Advancement advancement() {
        return this.holder.value();
    }

    public AdvancementHolder holder() {
        return this.holder;
    }

    public @Nullable AdvancementNode parent() {
        return this.parent;
    }

    public AdvancementNode root() {
        return getRoot(this);
    }

    public static AdvancementNode getRoot(AdvancementNode node) {
        AdvancementNode advancementNode = node;

        while (true) {
            AdvancementNode advancementNode1 = advancementNode.parent();
            if (advancementNode1 == null) {
                return advancementNode;
            }

            advancementNode = advancementNode1;
        }
    }

    public Iterable<AdvancementNode> children() {
        return this.children;
    }

    @VisibleForTesting
    public void addChild(AdvancementNode child) {
        this.children.add(child);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof AdvancementNode advancementNode && this.holder.equals(advancementNode.holder);
    }

    @Override
    public int hashCode() {
        return this.holder.hashCode();
    }

    @Override
    public String toString() {
        return this.holder.id().toString();
    }
}
