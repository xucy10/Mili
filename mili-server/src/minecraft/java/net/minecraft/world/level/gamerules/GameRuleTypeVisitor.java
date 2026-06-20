package net.minecraft.world.level.gamerules;

public interface GameRuleTypeVisitor {
    default <T> void visit(GameRule<T> rule) {
    }

    default void visitBoolean(GameRule<Boolean> rule) {
    }

    default void visitInteger(GameRule<Integer> rule) {
    }
}
