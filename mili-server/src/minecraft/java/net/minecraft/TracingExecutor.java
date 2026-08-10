package net.minecraft;

import com.mojang.jtracy.TracyClient;
import com.mojang.jtracy.Zone;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public record TracingExecutor(ExecutorService service) implements Executor {
    public Executor forName(String name) {
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            return task -> this.service.execute(() -> {
                Thread thread = Thread.currentThread();
                String name1 = thread.getName();
                thread.setName(name);

                try (Zone zone = TracyClient.beginZone(name, SharedConstants.IS_RUNNING_IN_IDE)) {
                    task.run();
                } finally {
                    thread.setName(name1);
                }
            });
        } else {
            return (TracyClient.isAvailable() ? runnable -> this.service.execute(() -> {
                try (Zone zone = TracyClient.beginZone(name, SharedConstants.IS_RUNNING_IN_IDE)) {
                    runnable.run();
                }
            }) : this.service);
        }
    }

    @Override
    public void execute(Runnable task) {
        this.service.execute(wrapUnnamed(task));
    }

    public void shutdownAndAwait(long timeout, TimeUnit unit) {
        this.service.shutdown();

        boolean flag;
        try {
            flag = this.service.awaitTermination(timeout, unit);
        } catch (InterruptedException var6) {
            flag = false;
        }

        if (!flag) {
            this.service.shutdownNow();
        }
    }

    private static Runnable wrapUnnamed(Runnable task) {
        return !TracyClient.isAvailable() ? task : () -> {
            try (Zone zone = TracyClient.beginZone("task", SharedConstants.IS_RUNNING_IN_IDE)) {
                task.run();
            }
        };
    }
}
