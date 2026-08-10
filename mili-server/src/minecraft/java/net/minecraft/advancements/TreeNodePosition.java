package net.minecraft.advancements;

import com.google.common.collect.Lists;
import java.util.List;
import org.jspecify.annotations.Nullable;

public class TreeNodePosition {
    private final AdvancementNode node;
    private final @Nullable TreeNodePosition parent;
    private final @Nullable TreeNodePosition previousSibling;
    private final int childIndex;
    private final List<TreeNodePosition> children = Lists.newArrayList();
    private TreeNodePosition ancestor;
    private @Nullable TreeNodePosition thread;
    private int x;
    private float y;
    private float mod;
    private float change;
    private float shift;

    public TreeNodePosition(AdvancementNode node, @Nullable TreeNodePosition parent, @Nullable TreeNodePosition previousSibling, int childIndex, int x) {
        if (node.advancement().display().isEmpty()) {
            throw new IllegalArgumentException("Can't position an invisible advancement!");
        } else {
            this.node = node;
            this.parent = parent;
            this.previousSibling = previousSibling;
            this.childIndex = childIndex;
            this.ancestor = this;
            this.x = x;
            this.y = -1.0F;
            TreeNodePosition treeNodePosition = null;

            for (AdvancementNode advancementNode : node.children()) {
                treeNodePosition = this.addChild(advancementNode, treeNodePosition);
            }
        }
    }

    private @Nullable TreeNodePosition addChild(AdvancementNode child, @Nullable TreeNodePosition previousSibling) {
        if (child.advancement().display().isPresent()) {
            previousSibling = new TreeNodePosition(child, this, previousSibling, this.children.size() + 1, this.x + 1);
            this.children.add(previousSibling);
        } else {
            for (AdvancementNode advancementNode : child.children()) {
                previousSibling = this.addChild(advancementNode, previousSibling);
            }
        }

        return previousSibling;
    }

    private void firstWalk() {
        if (this.children.isEmpty()) {
            if (this.previousSibling != null) {
                this.y = this.previousSibling.y + 1.0F;
            } else {
                this.y = 0.0F;
            }
        } else {
            TreeNodePosition treeNodePosition = null;

            for (TreeNodePosition treeNodePosition1 : this.children) {
                treeNodePosition1.firstWalk();
                treeNodePosition = treeNodePosition1.apportion(treeNodePosition == null ? treeNodePosition1 : treeNodePosition);
            }

            this.executeShifts();
            float f = (this.children.get(0).y + this.children.get(this.children.size() - 1).y) / 2.0F;
            if (this.previousSibling != null) {
                this.y = this.previousSibling.y + 1.0F;
                this.mod = this.y - f;
            } else {
                this.y = f;
            }
        }
    }

    private float secondWalk(float offsetY, int columnX, float subtreeTopY) {
        this.y += offsetY;
        this.x = columnX;
        if (this.y < subtreeTopY) {
            subtreeTopY = this.y;
        }

        for (TreeNodePosition treeNodePosition : this.children) {
            subtreeTopY = treeNodePosition.secondWalk(offsetY + this.mod, columnX + 1, subtreeTopY);
        }

        return subtreeTopY;
    }

    private void thirdWalk(float y) {
        this.y += y;

        for (TreeNodePosition treeNodePosition : this.children) {
            treeNodePosition.thirdWalk(y);
        }
    }

    private void executeShifts() {
        float f = 0.0F;
        float f1 = 0.0F;

        for (int i = this.children.size() - 1; i >= 0; i--) {
            TreeNodePosition treeNodePosition = this.children.get(i);
            treeNodePosition.y += f;
            treeNodePosition.mod += f;
            f1 += treeNodePosition.change;
            f += treeNodePosition.shift + f1;
        }
    }

    private @Nullable TreeNodePosition previousOrThread() {
        if (this.thread != null) {
            return this.thread;
        } else {
            return !this.children.isEmpty() ? this.children.get(0) : null;
        }
    }

    private @Nullable TreeNodePosition nextOrThread() {
        if (this.thread != null) {
            return this.thread;
        } else {
            return !this.children.isEmpty() ? this.children.get(this.children.size() - 1) : null;
        }
    }

    private TreeNodePosition apportion(TreeNodePosition node) {
        if (this.previousSibling == null) {
            return node;
        } else {
            TreeNodePosition treeNodePosition = this;
            TreeNodePosition treeNodePosition1 = this;
            TreeNodePosition treeNodePosition2 = this.previousSibling;
            TreeNodePosition treeNodePosition3 = this.parent.children.get(0);
            float f = this.mod;
            float f1 = this.mod;
            float f2 = treeNodePosition2.mod;

            float f3;
            for (f3 = treeNodePosition3.mod;
                treeNodePosition2.nextOrThread() != null && treeNodePosition.previousOrThread() != null;
                f1 += treeNodePosition1.mod
            ) {
                treeNodePosition2 = treeNodePosition2.nextOrThread();
                treeNodePosition = treeNodePosition.previousOrThread();
                treeNodePosition3 = treeNodePosition3.previousOrThread();
                treeNodePosition1 = treeNodePosition1.nextOrThread();
                treeNodePosition1.ancestor = this;
                float f4 = treeNodePosition2.y + f2 - (treeNodePosition.y + f) + 1.0F;
                if (f4 > 0.0F) {
                    treeNodePosition2.getAncestor(this, node).moveSubtree(this, f4);
                    f += f4;
                    f1 += f4;
                }

                f2 += treeNodePosition2.mod;
                f += treeNodePosition.mod;
                f3 += treeNodePosition3.mod;
            }

            if (treeNodePosition2.nextOrThread() != null && treeNodePosition1.nextOrThread() == null) {
                treeNodePosition1.thread = treeNodePosition2.nextOrThread();
                treeNodePosition1.mod += f2 - f1;
            } else {
                if (treeNodePosition.previousOrThread() != null && treeNodePosition3.previousOrThread() == null) {
                    treeNodePosition3.thread = treeNodePosition.previousOrThread();
                    treeNodePosition3.mod += f - f3;
                }

                node = this;
            }

            return node;
        }
    }

    private void moveSubtree(TreeNodePosition node, float shift) {
        float f = node.childIndex - this.childIndex;
        if (f != 0.0F) {
            node.change -= shift / f;
            this.change += shift / f;
        }

        node.shift += shift;
        node.y += shift;
        node.mod += shift;
    }

    private TreeNodePosition getAncestor(TreeNodePosition self, TreeNodePosition other) {
        return this.ancestor != null && self.parent.children.contains(this.ancestor) ? this.ancestor : other;
    }

    private void finalizePosition() {
        this.node.advancement().display().ifPresent(displayInfo -> displayInfo.setLocation(this.x, this.y));
        if (!this.children.isEmpty()) {
            for (TreeNodePosition treeNodePosition : this.children) {
                treeNodePosition.finalizePosition();
            }
        }
    }

    public static void run(AdvancementNode rootNode) {
        if (rootNode.advancement().display().isEmpty()) {
            throw new IllegalArgumentException("Can't position children of an invisible root!");
        } else {
            TreeNodePosition treeNodePosition = new TreeNodePosition(rootNode, null, null, 1, 0);
            treeNodePosition.firstWalk();
            float f = treeNodePosition.secondWalk(0.0F, 0, treeNodePosition.y);
            if (f < 0.0F) {
                treeNodePosition.thirdWalk(-f);
            }

            treeNodePosition.finalizePosition();
        }
    }
}
