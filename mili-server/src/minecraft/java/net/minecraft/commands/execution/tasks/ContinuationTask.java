package net.minecraft.commands.execution.tasks;

import java.util.List;
import net.minecraft.commands.execution.CommandQueueEntry;
import net.minecraft.commands.execution.EntryAction;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.Frame;

public class ContinuationTask<T, P> implements EntryAction<T> {
    private final ContinuationTask.TaskProvider<T, P> taskFactory;
    private final List<P> arguments;
    private final CommandQueueEntry<T> selfEntry;
    private int index;

    private ContinuationTask(ContinuationTask.TaskProvider<T, P> taskFactory, List<P> arguments, Frame frame) {
        this.taskFactory = taskFactory;
        this.arguments = arguments;
        this.selfEntry = new CommandQueueEntry<>(frame, this);
    }

    @Override
    public void execute(ExecutionContext<T> context, Frame frame) {
        P object = this.arguments.get(this.index);
        context.queueNext(this.taskFactory.create(frame, object));
        if (++this.index < this.arguments.size()) {
            context.queueNext(this.selfEntry);
        }
    }

    public static <T, P> void schedule(ExecutionContext<T> executionContext, Frame frame, List<P> arguments, ContinuationTask.TaskProvider<T, P> taskProvider) {
        int size = arguments.size();
        switch (size) {
            case 0:
                break;
            case 1:
                executionContext.queueNext(taskProvider.create(frame, arguments.get(0)));
                break;
            case 2:
                executionContext.queueNext(taskProvider.create(frame, arguments.get(0)));
                executionContext.queueNext(taskProvider.create(frame, arguments.get(1)));
                break;
            default:
                executionContext.queueNext((new ContinuationTask<>(taskProvider, arguments, frame)).selfEntry);
        }
    }

    @FunctionalInterface
    public interface TaskProvider<T, P> {
        CommandQueueEntry<T> create(Frame frame, P argument);
    }
}
