package net.minecraft.util;

import com.mojang.logging.LogUtils;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ThreadingDetector {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final String name;
    private final Semaphore lock = new Semaphore(1);
    private final Lock stackTraceLock = new ReentrantLock();
    private volatile @Nullable Thread threadThatFailedToAcquire;
    private volatile @Nullable ReportedException fullException;

    public ThreadingDetector(String name) {
        this.name = name;
    }

    public void checkAndLock() {
        boolean flag = false;

        try {
            this.stackTraceLock.lock();
            if (!this.lock.tryAcquire()) {
                this.threadThatFailedToAcquire = Thread.currentThread();
                flag = true;
                this.stackTraceLock.unlock();

                try {
                    this.lock.acquire();
                } catch (InterruptedException var6) {
                    Thread.currentThread().interrupt();
                }

                throw this.fullException;
            }
        } finally {
            if (!flag) {
                this.stackTraceLock.unlock();
            }
        }
    }

    public void checkAndUnlock() {
        try {
            this.stackTraceLock.lock();
            Thread thread = this.threadThatFailedToAcquire;
            if (thread != null) {
                ReportedException reportedException = makeThreadingException(this.name, thread);
                this.fullException = reportedException;
                this.lock.release();
                throw reportedException;
            }

            this.lock.release();
        } finally {
            this.stackTraceLock.unlock();
        }
    }

    public static ReportedException makeThreadingException(String accessed, @Nullable Thread thread) {
        String string = Stream.of(Thread.currentThread(), thread).filter(Objects::nonNull).map(ThreadingDetector::stackTrace).collect(Collectors.joining("\n"));
        String string1 = "Accessing " + accessed + " from multiple threads";
        CrashReport crashReport = new CrashReport(string1, new IllegalStateException(string1));
        CrashReportCategory crashReportCategory = crashReport.addCategory("Thread dumps");
        crashReportCategory.setDetail("Thread dumps", string);
        LOGGER.error("Thread dumps: \n{}", string);
        return new ReportedException(crashReport);
    }

    private static String stackTrace(Thread thread) {
        return thread.getName() + ": \n\tat " + Arrays.stream(thread.getStackTrace()).map(Object::toString).collect(Collectors.joining("\n\tat "));
    }
}
