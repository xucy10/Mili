package net.minecraft.util.profiling;

import com.mojang.jtracy.TracyClient;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;

public final class Profiler {
    private static final ThreadLocal<TracyZoneFiller> TRACY_FILLER = ThreadLocal.withInitial(TracyZoneFiller::new);
    private static final ThreadLocal<@Nullable ProfilerFiller> ACTIVE = new ThreadLocal<>();
    private static final AtomicInteger ACTIVE_COUNT = new AtomicInteger();

    private Profiler() {
    }

    public static Profiler.Scope use(ProfilerFiller profiler) {
        startUsing(profiler);
        return Profiler::stopUsing;
    }

    private static void startUsing(ProfilerFiller profiler) {
        if (ACTIVE.get() != null) {
            throw new IllegalStateException("Profiler is already active");
        } else {
            ProfilerFiller profilerFiller = decorateFiller(profiler);
            ACTIVE.set(profilerFiller);
            ACTIVE_COUNT.incrementAndGet();
            profilerFiller.startTick();
        }
    }

    private static void stopUsing() {
        ProfilerFiller profilerFiller = ACTIVE.get();
        if (profilerFiller == null) {
            throw new IllegalStateException("Profiler was not active");
        } else {
            ACTIVE.remove();
            ACTIVE_COUNT.decrementAndGet();
            profilerFiller.endTick();
        }
    }

    private static ProfilerFiller decorateFiller(ProfilerFiller filler) {
        return ProfilerFiller.combine(getDefaultFiller(), filler);
    }

    public static ProfilerFiller get() {
        return ACTIVE_COUNT.get() == 0 ? getDefaultFiller() : Objects.requireNonNullElseGet(ACTIVE.get(), Profiler::getDefaultFiller);
    }

    private static ProfilerFiller getDefaultFiller() {
        return (ProfilerFiller)(TracyClient.isAvailable() ? TRACY_FILLER.get() : InactiveProfiler.INSTANCE);
    }

    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
