package net.minecraft.util.parsing.packrat;

import org.jspecify.annotations.Nullable;

public interface Rule<S, T> {
    @Nullable T parse(ParseState<S> parseState);

    static <S, T> Rule<S, T> fromTerm(Term<S> child, Rule.RuleAction<S, T> action) {
        return new Rule.WrappedTerm<>(action, child);
    }

    static <S, T> Rule<S, T> fromTerm(Term<S> child, Rule.SimpleRuleAction<S, T> action) {
        return new Rule.WrappedTerm<>(action, child);
    }

    @FunctionalInterface
    public interface RuleAction<S, T> {
        @Nullable T run(ParseState<S> parseState);
    }

    @FunctionalInterface
    public interface SimpleRuleAction<S, T> extends Rule.RuleAction<S, T> {
        T run(Scope scope);

        @Override
        default T run(ParseState<S> parseState) {
            return this.run(parseState.scope());
        }
    }

    public record WrappedTerm<S, T>(Rule.RuleAction<S, T> action, Term<S> child) implements Rule<S, T> {
        @Override
        public @Nullable T parse(ParseState<S> parseState) {
            Scope scope = parseState.scope();
            scope.pushFrame();

            Object var3;
            try {
                if (!this.child.parse(parseState, scope, Control.UNBOUND)) {
                    return null;
                }

                var3 = this.action.run(parseState);
            } finally {
                scope.popFrame();
            }

            return (T)var3;
        }
    }
}
