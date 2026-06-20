package net.minecraft.world.phys.shapes;

public interface BooleanOp {
    BooleanOp FALSE = (left, right) -> false;
    BooleanOp NOT_OR = (left, right) -> !left && !right;
    BooleanOp ONLY_SECOND = (left, right) -> right && !left;
    BooleanOp NOT_FIRST = (left, right) -> !left;
    BooleanOp ONLY_FIRST = (left, right) -> left && !right;
    BooleanOp NOT_SECOND = (left, right) -> !right;
    BooleanOp NOT_SAME = (left, right) -> left != right;
    BooleanOp NOT_AND = (left, right) -> !left || !right;
    BooleanOp AND = (left, right) -> left && right;
    BooleanOp SAME = (left, right) -> left == right;
    BooleanOp SECOND = (left, right) -> right;
    BooleanOp CAUSES = (left, right) -> !left || right;
    BooleanOp FIRST = (left, right) -> left;
    BooleanOp CAUSED_BY = (left, right) -> left || !right;
    BooleanOp OR = (left, right) -> left || right;
    BooleanOp TRUE = (left, right) -> true;

    boolean apply(boolean primaryBool, boolean secondaryBool);
}
