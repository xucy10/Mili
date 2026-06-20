package net.minecraft.util.thread;

public abstract class ReentrantBlockableEventLoop<R extends Runnable> extends BlockableEventLoop<R> {
    private int reentrantCount;

    public ReentrantBlockableEventLoop(String name) {
        super(name);
    }

    @Override
    public boolean scheduleExecutables() {
        return this.runningTask() || super.scheduleExecutables();
    }

    protected boolean runningTask() {
        return this.reentrantCount != 0;
    }

    @Override
    public void doRunTask(R task) {
        this.reentrantCount++;

        try {
            super.doRunTask(task);
        } finally {
            this.reentrantCount--;
        }
    }
}
