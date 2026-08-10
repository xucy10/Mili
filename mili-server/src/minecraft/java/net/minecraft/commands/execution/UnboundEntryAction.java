package net.minecraft.commands.execution;

@FunctionalInterface
public interface UnboundEntryAction<T> {
    void execute(T source, ExecutionContext<T> executionContext, Frame frame);

    default EntryAction<T> bind(T source) {
        return (context, frame) -> this.execute(source, context, frame);
    }
}
