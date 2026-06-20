package net.minecraft.util;

import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.MoreExecutors;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.jtracy.TracyClient;
import com.mojang.jtracy.Zone;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceImmutableList;
import it.unimi.dsi.fastutil.objects.ReferenceList;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import net.minecraft.CharPredicate;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.DefaultUncaughtExceptionHandler;
import net.minecraft.ReportType;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.SuppressForbidden;
import net.minecraft.TracingExecutor;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class Util {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final int DEFAULT_MAX_THREADS = 255;
    private static final int DEFAULT_SAFE_FILE_OPERATION_RETRIES = 10;
    private static final String MAX_THREADS_SYSTEM_PROPERTY = "max.bg.threads";
    private static final TracingExecutor BACKGROUND_EXECUTOR = makeExecutor("Main", -1); // Paper - Perf: add priority
    private static final TracingExecutor IO_POOL = makeIoExecutor("IO-Worker-", false);
    public static final TracingExecutor DIMENSION_DATA_IO_POOL = makeExtraIoExecutor("Dimension-Data-IO-Worker-"); // Paper - Separate dimension data IO pool
    // Paper start - Limit download pool size
    private static final TracingExecutor DOWNLOAD_POOL = new TracingExecutor(Executors.newFixedThreadPool(4, new java.util.concurrent.ThreadFactory() {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public Thread newThread(Runnable run) {
            Thread ret = new Thread(run);
            ret.setDaemon(true);
            ret.setName("Download-" + this.count.getAndIncrement());
            ret.setUncaughtExceptionHandler((Thread thread, Throwable throwable) -> {
                LOGGER.error("Uncaught exception in thread " + thread.getName(), throwable);
            });
            return ret;
        }
    }));
    // Paper end - Limit download pool size
    private static final DateTimeFormatter FILENAME_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss", Locale.ROOT);
    public static final int LINEAR_LOOKUP_THRESHOLD = 8;
    private static final Set<String> ALLOWED_UNTRUSTED_LINK_PROTOCOLS = Set.of("http", "https");
    public static final long NANOS_PER_MILLI = 1000000L;
    public static TimeSource.NanoTimeSource timeSource = System::nanoTime;
    public static final Ticker TICKER = new Ticker() {
        @Override
        public long read() {
            return Util.timeSource.getAsLong();
        }
    };
    public static final UUID NIL_UUID = new UUID(0L, 0L);
    public static final FileSystemProvider ZIP_FILE_SYSTEM_PROVIDER = FileSystemProvider.installedProviders()
        .stream()
        .filter(provider -> provider.getScheme().equalsIgnoreCase("jar"))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No jar file system provider found"));
    private static Consumer<String> thePauser = string -> {};
    public static final double COLLISION_EPSILON = 1.0E-7; // Paper - Check distance in entity interactions

    public static <K, V> Collector<Entry<? extends K, ? extends V>, ?, Map<K, V>> toMap() {
        return Collectors.toMap(Entry::getKey, Entry::getValue);
    }

    public static <T> Collector<T, ?, List<T>> toMutableList() {
        return Collectors.toCollection(Lists::newArrayList);
    }

    public static <T extends Comparable<T>> String getPropertyName(Property<T> property, Object value) {
        return property.getName((T)value);
    }

    public static String makeDescriptionId(String type, @Nullable Identifier id) {
        return id == null ? type + ".unregistered_sadface" : type + "." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }

    public static long getMillis() {
        return getNanos() / 1000000L;
    }

    public static long getNanos() {
        return System.nanoTime(); // Paper
    }

    public static long getEpochMillis() {
        return Instant.now().toEpochMilli();
    }

    public static String getFilenameFormattedDateTime() {
        return FILENAME_DATE_TIME_FORMATTER.format(ZonedDateTime.now());
    }

    private static TracingExecutor makeExecutor(String name, final int priorityModifier) { // Paper - Perf: add priority
        int i = maxAllowedExecutorThreads();
        // Paper start - Perf: use simpler thread pool that allows 1 thread and reduce worldgen thread worker count for low core count CPUs
        final ExecutorService directExecutorService;
        if (i <= 0) {
            directExecutorService = MoreExecutors.newDirectExecutorService();
        } else {
            AtomicInteger atomicInteger = new AtomicInteger(1);
            directExecutorService = new ForkJoinPool(i, forkJoinPool -> {
                final String string = "Worker-" + name + "-" + atomicInteger.getAndIncrement();
                ForkJoinWorkerThread forkJoinWorkerThread = new ForkJoinWorkerThread(forkJoinPool) {
                    @Override
                    protected void onStart() {
                        TracyClient.setThreadName(string, name.hashCode());
                        super.onStart();
                    }

                    @Override
                    protected void onTermination(@Nullable Throwable throwOnTermination) {
                        if (throwOnTermination != null) {
                            Util.LOGGER.warn("{} died", this.getName(), throwOnTermination);
                        } else {
                            Util.LOGGER.debug("{} shutdown", this.getName());
                        }

                        super.onTermination(throwOnTermination);
                    }
                };
                forkJoinWorkerThread.setPriority(Thread.NORM_PRIORITY + priorityModifier); // Paper - Deprioritize over main
                forkJoinWorkerThread.setName(string);
                return forkJoinWorkerThread;
            }, Util::onThreadException, true, 0, Integer.MAX_VALUE, 1, null, 365, TimeUnit.DAYS); // Paper - do not expire threads
        }

        return new TracingExecutor(directExecutorService);
    }

    public static int maxAllowedExecutorThreads() {
        // Paper start - Perf: use simpler thread pool that allows 1 thread and reduce worldgen thread worker count for low core count CPUs
        final int cpus = ca.spottedleaf.concurrentutil.numa.OSNuma.getNativeInstance().getTotalCores()  / 2;
        int maxExecutorThreads;
        if (cpus <= 4) {
            maxExecutorThreads = cpus <= 2 ? 1 : 2;
        } else if (cpus <= 8) {
            // [5, 8]
            maxExecutorThreads = Math.max(3, cpus - 2);
        } else {
            maxExecutorThreads = cpus * 2 / 3;
        }
        maxExecutorThreads = Math.min(8, maxExecutorThreads);
        return Integer.getInteger("Paper.WorkerThreadCount", maxExecutorThreads);
        // Paper end - Perf: use simpler thread pool that allows 1 thread and reduce worldgen thread worker count for low core count CPUs
    }

    private static int getMaxThreads() {
        String property = System.getProperty("max.bg.threads");
        if (property != null) {
            try {
                int i = Integer.parseInt(property);
                if (i >= 1 && i <= 255) {
                    return i;
                }

                LOGGER.error("Wrong {} property value '{}'. Should be an integer value between 1 and {}.", "max.bg.threads", property, 255);
            } catch (NumberFormatException var2) {
                LOGGER.error("Could not parse {} property value '{}'. Should be an integer value between 1 and {}.", "max.bg.threads", property, 255);
            }
        }

        return 255;
    }

    public static TracingExecutor backgroundExecutor() {
        return BACKGROUND_EXECUTOR;
    }

    public static TracingExecutor ioPool() {
        return IO_POOL;
    }

    public static TracingExecutor nonCriticalIoPool() {
        return DOWNLOAD_POOL;
    }

    public static void shutdownExecutors() {
        BACKGROUND_EXECUTOR.shutdownAndAwait(3L, TimeUnit.SECONDS);
        IO_POOL.shutdownAndAwait(3L, TimeUnit.SECONDS);
    }

    private static TracingExecutor makeIoExecutor(String name, boolean daemon) {
        AtomicInteger atomicInteger = new AtomicInteger(1);
        return new TracingExecutor(Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task);
            String string = name + atomicInteger.getAndIncrement();
            TracyClient.setThreadName(string, name.hashCode());
            thread.setName(string);
            thread.setDaemon(daemon);
            thread.setUncaughtExceptionHandler(Util::onThreadException);
            return thread;
        }));
    }

    // Paper start - Separate dimension data IO pool
    private static TracingExecutor makeExtraIoExecutor(String namePrefix) {
        AtomicInteger atomicInteger = new AtomicInteger(1);
        return new TracingExecutor(Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable);
            String string2 = namePrefix + atomicInteger.getAndIncrement();
            TracyClient.setThreadName(string2, namePrefix.hashCode());
            thread.setName(string2);
            thread.setDaemon(false);
            thread.setUncaughtExceptionHandler(Util::onThreadException);
            return thread;
        }));
    }
    // Paper end - Separate dimension data IO pool

    public static void throwAsRuntime(Throwable throwable) {
        throw throwable instanceof RuntimeException ? (RuntimeException)throwable : new RuntimeException(throwable);
    }

    public static void onThreadException(Thread thread, Throwable throwable) {
        pauseInIde(throwable);
        if (throwable instanceof CompletionException) {
            throwable = throwable.getCause();
        }

        if (throwable instanceof ReportedException reportedException) {
            Bootstrap.realStdoutPrintln(reportedException.getReport().getFriendlyReport(ReportType.CRASH));
            System.exit(-1);
        }

        LOGGER.error("Caught exception in thread {}", thread, throwable);
    }

    public static @Nullable Type<?> fetchChoiceType(TypeReference type, String choiceName) {
        return !SharedConstants.CHECK_DATA_FIXER_SCHEMA ? null : doFetchChoiceType(type, choiceName);
    }

    private static @Nullable Type<?> doFetchChoiceType(TypeReference type, String choiceName) {
        Type<?> type1 = null;

        try {
            type1 = DataFixers.getDataFixer()
                .getSchema(DataFixUtils.makeKey(SharedConstants.getCurrentVersion().dataVersion().version()))
                .getChoiceType(type, choiceName);
        } catch (IllegalArgumentException var4) {
            LOGGER.error("No data fixer registered for {}", choiceName);
            if (SharedConstants.IS_RUNNING_IN_IDE) {
                throw var4;
            }
        }

        return type1;
    }

    public static void runNamed(Runnable task, String name) {
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            Thread thread = Thread.currentThread();
            String name1 = thread.getName();
            thread.setName(name);

            try (Zone zone = TracyClient.beginZone(name, SharedConstants.IS_RUNNING_IN_IDE)) {
                task.run();
            } finally {
                thread.setName(name1);
            }
        } else {
            try (Zone zone1 = TracyClient.beginZone(name, SharedConstants.IS_RUNNING_IN_IDE)) {
                task.run();
            }
        }
    }

    public static <T> String getRegisteredName(Registry<T> registry, T value) {
        Identifier key = registry.getKey(value);
        return key == null ? "[unregistered]" : key.toString();
    }

    public static <T> Predicate<T> allOf() {
        return input -> true;
    }

    public static <T> Predicate<T> allOf(Predicate<? super T> predicate) {
        return (Predicate<T>)predicate;
    }

    public static <T> Predicate<T> allOf(Predicate<? super T> predicate1, Predicate<? super T> predicate2) {
        return input -> predicate1.test(input) && predicate2.test(input);
    }

    public static <T> Predicate<T> allOf(Predicate<? super T> predicate1, Predicate<? super T> predicate2, Predicate<? super T> predicate3) {
        return input -> predicate1.test(input) && predicate2.test(input) && predicate3.test(input);
    }

    public static <T> Predicate<T> allOf(
        Predicate<? super T> predicate1, Predicate<? super T> predicate2, Predicate<? super T> predicate3, Predicate<? super T> predicate4
    ) {
        return input -> predicate1.test(input) && predicate2.test(input) && predicate3.test(input) && predicate4.test(input);
    }

    public static <T> Predicate<T> allOf(
        Predicate<? super T> predicate1,
        Predicate<? super T> predicate2,
        Predicate<? super T> predicate3,
        Predicate<? super T> predicate4,
        Predicate<? super T> predicate5
    ) {
        return input -> predicate1.test(input) && predicate2.test(input) && predicate3.test(input) && predicate4.test(input) && predicate5.test(input);
    }

    @SafeVarargs
    public static <T> Predicate<T> allOf(Predicate<? super T>... predicates) {
        return input -> {
            for (Predicate<? super T> predicate : predicates) {
                if (!predicate.test(input)) {
                    return false;
                }
            }

            return true;
        };
    }

    public static <T> Predicate<T> allOf(List<? extends Predicate<? super T>> predicates) {
        return switch (predicates.size()) {
            case 0 -> allOf();
            case 1 -> allOf((Predicate<? super T>)predicates.get(0));
            case 2 -> allOf((Predicate<? super T>)predicates.get(0), (Predicate<? super T>)predicates.get(1));
            case 3 -> allOf((Predicate<? super T>)predicates.get(0), (Predicate<? super T>)predicates.get(1), (Predicate<? super T>)predicates.get(2));
            case 4 -> allOf(
                (Predicate<? super T>)predicates.get(0),
                (Predicate<? super T>)predicates.get(1),
                (Predicate<? super T>)predicates.get(2),
                (Predicate<? super T>)predicates.get(3)
            );
            case 5 -> allOf(
                (Predicate<? super T>)predicates.get(0),
                (Predicate<? super T>)predicates.get(1),
                (Predicate<? super T>)predicates.get(2),
                (Predicate<? super T>)predicates.get(3),
                (Predicate<? super T>)predicates.get(4)
            );
            default -> {
                Predicate<? super T>[] predicates1 = predicates.toArray(Predicate[]::new);
                yield allOf(predicates1);
            }
        };
    }

    public static <T> Predicate<T> anyOf() {
        return input -> false;
    }

    public static <T> Predicate<T> anyOf(Predicate<? super T> predicate) {
        return (Predicate<T>)predicate;
    }

    public static <T> Predicate<T> anyOf(Predicate<? super T> predicate1, Predicate<? super T> predicate2) {
        return input -> predicate1.test(input) || predicate2.test(input);
    }

    public static <T> Predicate<T> anyOf(Predicate<? super T> predicate1, Predicate<? super T> predicate2, Predicate<? super T> predicate3) {
        return input -> predicate1.test(input) || predicate2.test(input) || predicate3.test(input);
    }

    public static <T> Predicate<T> anyOf(
        Predicate<? super T> predicate1, Predicate<? super T> predicate2, Predicate<? super T> predicate3, Predicate<? super T> predicate4
    ) {
        return input -> predicate1.test(input) || predicate2.test(input) || predicate3.test(input) || predicate4.test(input);
    }

    public static <T> Predicate<T> anyOf(
        Predicate<? super T> predicate1,
        Predicate<? super T> predicate2,
        Predicate<? super T> predicate3,
        Predicate<? super T> predicate4,
        Predicate<? super T> predicate5
    ) {
        return input -> predicate1.test(input) || predicate2.test(input) || predicate3.test(input) || predicate4.test(input) || predicate5.test(input);
    }

    @SafeVarargs
    public static <T> Predicate<T> anyOf(Predicate<? super T>... predicates) {
        return input -> {
            for (Predicate<? super T> predicate : predicates) {
                if (predicate.test(input)) {
                    return true;
                }
            }

            return false;
        };
    }

    public static <T> Predicate<T> anyOf(List<? extends Predicate<? super T>> predicates) {
        return switch (predicates.size()) {
            case 0 -> anyOf();
            case 1 -> anyOf((Predicate<? super T>)predicates.get(0));
            case 2 -> anyOf((Predicate<? super T>)predicates.get(0), (Predicate<? super T>)predicates.get(1));
            case 3 -> anyOf((Predicate<? super T>)predicates.get(0), (Predicate<? super T>)predicates.get(1), (Predicate<? super T>)predicates.get(2));
            case 4 -> anyOf(
                (Predicate<? super T>)predicates.get(0),
                (Predicate<? super T>)predicates.get(1),
                (Predicate<? super T>)predicates.get(2),
                (Predicate<? super T>)predicates.get(3)
            );
            case 5 -> anyOf(
                (Predicate<? super T>)predicates.get(0),
                (Predicate<? super T>)predicates.get(1),
                (Predicate<? super T>)predicates.get(2),
                (Predicate<? super T>)predicates.get(3),
                (Predicate<? super T>)predicates.get(4)
            );
            default -> {
                Predicate<? super T>[] predicates1 = predicates.toArray(Predicate[]::new);
                yield anyOf(predicates1);
            }
        };
    }

    public static <T> boolean isSymmetrical(int width, int height, List<T> list) {
        if (width == 1) {
            return true;
        } else {
            int i = width / 2;

            for (int i1 = 0; i1 < height; i1++) {
                for (int i2 = 0; i2 < i; i2++) {
                    int i3 = width - 1 - i2;
                    T object = list.get(i2 + i1 * width);
                    T object1 = list.get(i3 + i1 * width);
                    if (!object.equals(object1)) {
                        return false;
                    }
                }
            }

            return true;
        }
    }

    public static int growByHalf(int value, int minValue) {
        return (int)Math.max(Math.min((long)value + (value >> 1), 2147483639L), (long)minValue);
    }

    @SuppressForbidden(reason = "Intentional use of default locale for user-visible date")
    public static DateTimeFormatter localizedDateFormatter(FormatStyle dateTimeStyle) {
        return DateTimeFormatter.ofLocalizedDateTime(dateTimeStyle);
    }

    public static Util.OS getPlatform() {
        String string = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (string.contains("win")) {
            return Util.OS.WINDOWS;
        } else if (string.contains("mac")) {
            return Util.OS.OSX;
        } else if (string.contains("solaris")) {
            return Util.OS.SOLARIS;
        } else if (string.contains("sunos")) {
            return Util.OS.SOLARIS;
        } else if (string.contains("linux")) {
            return Util.OS.LINUX;
        } else {
            return string.contains("unix") ? Util.OS.LINUX : Util.OS.UNKNOWN;
        }
    }

    public static boolean isAarch64() {
        String string = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        return string.equals("aarch64");
    }

    public static URI parseAndValidateUntrustedUri(String uri) throws URISyntaxException {
        URI uri1 = new URI(uri);
        String scheme = uri1.getScheme();
        if (scheme == null) {
            throw new URISyntaxException(uri, "Missing protocol in URI: " + uri);
        } else {
            String string = scheme.toLowerCase(Locale.ROOT);
            if (!ALLOWED_UNTRUSTED_LINK_PROTOCOLS.contains(string)) {
                throw new URISyntaxException(uri, "Unsupported protocol in URI: " + uri);
            } else {
                return uri1;
            }
        }
    }

    public static <T> T findNextInIterable(Iterable<T> iterable, @Nullable T element) {
        Iterator<T> iterator = iterable.iterator();
        T object = iterator.next();
        if (element != null) {
            T object1 = object;

            while (object1 != element) {
                if (iterator.hasNext()) {
                    object1 = iterator.next();
                }
            }

            if (iterator.hasNext()) {
                return iterator.next();
            }
        }

        return object;
    }

    public static <T> T findPreviousInIterable(Iterable<T> iterable, @Nullable T current) {
        Iterator<T> iterator = iterable.iterator();
        T object = null;

        while (iterator.hasNext()) {
            T object1 = iterator.next();
            if (object1 == current) {
                if (object == null) {
                    object = iterator.hasNext() ? Iterators.getLast(iterator) : current;
                }
                break;
            }

            object = object1;
        }

        return object;
    }

    public static <T> T make(Supplier<T> supplier) {
        return supplier.get();
    }

    public static <T> T make(T object, Consumer<? super T> consumer) {
        consumer.accept(object);
        return object;
    }

    public static <K extends Enum<K>, V> Map<K, V> makeEnumMap(Class<K> enumClass, Function<K, V> valueGetter) {
        EnumMap<K, V> map = new EnumMap<>(enumClass);

        for (K _enum : enumClass.getEnumConstants()) {
            map.put(_enum, valueGetter.apply(_enum));
        }

        return map;
    }

    public static <K, V1, V2> Map<K, V2> mapValues(Map<K, V1> map, Function<? super V1, V2> mapper) {
        return map.entrySet().stream().collect(Collectors.toMap(Entry::getKey, entry -> mapper.apply(entry.getValue())));
    }

    public static <K, V1, V2> Map<K, V2> mapValuesLazy(Map<K, V1> map, com.google.common.base.Function<V1, V2> mapper) {
        return Maps.transformValues(map, mapper);
    }

    public static <V> CompletableFuture<List<V>> sequence(List<? extends CompletableFuture<V>> futures) {
        if (futures.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        } else if (futures.size() == 1) {
            return futures.getFirst().thenApply(ObjectLists::singleton);
        } else {
            CompletableFuture<Void> completableFuture = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            return completableFuture.thenApply(_void -> futures.stream().map(CompletableFuture::join).toList());
        }
    }

    public static <V> CompletableFuture<List<V>> sequenceFailFast(List<? extends CompletableFuture<? extends V>> completableFutures) {
        CompletableFuture<List<V>> completableFuture = new CompletableFuture<>();
        return fallibleSequence(completableFutures, completableFuture::completeExceptionally).applyToEither(completableFuture, Function.identity());
    }

    public static <V> CompletableFuture<List<V>> sequenceFailFastAndCancel(List<? extends CompletableFuture<? extends V>> completableFutures) {
        CompletableFuture<List<V>> completableFuture = new CompletableFuture<>();
        return fallibleSequence(completableFutures, throwable -> {
            if (completableFuture.completeExceptionally(throwable)) {
                for (CompletableFuture<? extends V> completableFuture1 : completableFutures) {
                    completableFuture1.cancel(true);
                }
            }
        }).applyToEither(completableFuture, Function.identity());
    }

    private static <V> CompletableFuture<List<V>> fallibleSequence(
        List<? extends CompletableFuture<? extends V>> completableFutures, Consumer<Throwable> throwableConsumer
    ) {
        ObjectArrayList<V> list = new ObjectArrayList<>();
        list.size(completableFutures.size());
        CompletableFuture<?>[] completableFutures1 = new CompletableFuture[completableFutures.size()];

        for (int i = 0; i < completableFutures.size(); i++) {
            int i1 = i;
            completableFutures1[i] = completableFutures.get(i).whenComplete((result, throwable) -> {
                if (throwable != null) {
                    throwableConsumer.accept(throwable);
                } else {
                    list.set(i1, (V)result);
                }
            });
        }

        return CompletableFuture.allOf(completableFutures1).thenApply(future -> list);
    }

    public static <T> Optional<T> ifElse(Optional<T> optional, Consumer<T> ifPresent, Runnable ifEmpty) {
        if (optional.isPresent()) {
            ifPresent.accept(optional.get());
        } else {
            ifEmpty.run();
        }

        return optional;
    }

    public static <T> Supplier<T> name(final Supplier<T> item, Supplier<String> nameSupplier) {
        if (SharedConstants.DEBUG_NAMED_RUNNABLES) {
            final String string = nameSupplier.get();
            return new Supplier<T>() {
                @Override
                public T get() {
                    return item.get();
                }

                @Override
                public String toString() {
                    return string;
                }
            };
        } else {
            return item;
        }
    }

    public static Runnable name(final Runnable item, Supplier<String> nameSupplier) {
        if (SharedConstants.DEBUG_NAMED_RUNNABLES) {
            final String string = nameSupplier.get();
            return new Runnable() {
                @Override
                public void run() {
                    item.run();
                }

                @Override
                public String toString() {
                    return string;
                }
            };
        } else {
            return item;
        }
    }

    public static void logAndPauseIfInIde(String message) {
        LOGGER.error(message);
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            doPause(message);
        }
    }

    public static void logAndPauseIfInIde(String message, Throwable error) {
        LOGGER.error(message, error);
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            doPause(message);
        }
    }

    public static <T extends Throwable> T pauseInIde(T throwable) {
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            LOGGER.error("Trying to throw a fatal exception, pausing in IDE", throwable);
            doPause(throwable.getMessage());
        }

        return throwable;
    }

    public static void setPause(Consumer<String> thePauser) {
        Util.thePauser = thePauser;
    }

    private static void doPause(String message) {
        Instant instant = Instant.now();
        LOGGER.warn("Did you remember to set a breakpoint here?");
        boolean flag = Duration.between(instant, Instant.now()).toMillis() > 500L;
        if (!flag) {
            thePauser.accept(message);
        }
    }

    public static String describeError(Throwable throwable) {
        if (throwable.getCause() != null) {
            return describeError(throwable.getCause());
        } else {
            return throwable.getMessage() != null ? throwable.getMessage() : throwable.toString();
        }
    }

    public static <T> T getRandom(T[] selections, RandomSource random) {
        return selections[random.nextInt(selections.length)];
    }

    public static int getRandom(int[] selections, RandomSource random) {
        return selections[random.nextInt(selections.length)];
    }

    public static <T> T getRandom(List<T> selections, RandomSource random) {
        return selections.get(random.nextInt(selections.size()));
    }

    public static <T> Optional<T> getRandomSafe(List<T> selections, RandomSource random) {
        return selections.isEmpty() ? Optional.empty() : Optional.of(getRandom(selections, random));
    }

    private static BooleanSupplier createRenamer(final Path filePath, final Path newName) {
        return new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                try {
                    Files.move(filePath, newName);
                    return true;
                } catch (IOException var2) {
                    Util.LOGGER.error("Failed to rename", (Throwable)var2);
                    return false;
                }
            }

            @Override
            public String toString() {
                return "rename " + filePath + " to " + newName;
            }
        };
    }

    private static BooleanSupplier createDeleter(final Path filePath) {
        return new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                try {
                    Files.deleteIfExists(filePath);
                    return true;
                } catch (IOException var2) {
                    Util.LOGGER.warn("Failed to delete", (Throwable)var2);
                    return false;
                }
            }

            @Override
            public String toString() {
                return "delete old " + filePath;
            }
        };
    }

    private static BooleanSupplier createFileDeletedCheck(final Path filePath) {
        return new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return !Files.exists(filePath);
            }

            @Override
            public String toString() {
                return "verify that " + filePath + " is deleted";
            }
        };
    }

    private static BooleanSupplier createFileCreatedCheck(final Path filePath) {
        return new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return Files.isRegularFile(filePath);
            }

            @Override
            public String toString() {
                return "verify that " + filePath + " is present";
            }
        };
    }

    private static boolean executeInSequence(BooleanSupplier... suppliers) {
        for (BooleanSupplier booleanSupplier : suppliers) {
            if (!booleanSupplier.getAsBoolean()) {
                LOGGER.warn("Failed to execute {}", booleanSupplier);
                return false;
            }
        }

        return true;
    }

    private static boolean runWithRetries(int maxTries, String actionName, BooleanSupplier... suppliers) {
        for (int i = 0; i < maxTries; i++) {
            if (executeInSequence(suppliers)) {
                return true;
            }

            LOGGER.error("Failed to {}, retrying {}/{}", actionName, i, maxTries);
        }

        LOGGER.error("Failed to {}, aborting, progress might be lost", actionName);
        return false;
    }

    public static void safeReplaceFile(Path current, Path latest, Path oldBackup) {
        safeReplaceOrMoveFile(current, latest, oldBackup, false);
    }

    public static boolean safeReplaceOrMoveFile(Path current, Path latest, Path oldBackup, boolean restore) {
        if (Files.exists(current)
            && !runWithRetries(10, "create backup " + oldBackup, createDeleter(oldBackup), createRenamer(current, oldBackup), createFileCreatedCheck(oldBackup))
            )
         {
            return false;
        } else if (!runWithRetries(10, "remove old " + current, createDeleter(current), createFileDeletedCheck(current))) {
            return false;
        } else if (!runWithRetries(10, "replace " + current + " with " + latest, createRenamer(latest, current), createFileCreatedCheck(current)) && !restore) {
            runWithRetries(10, "restore " + current + " from " + oldBackup, createRenamer(oldBackup, current), createFileCreatedCheck(current));
            return false;
        } else {
            return true;
        }
    }

    public static int offsetByCodepoints(String text, int cursorPos, int direction) {
        int len = text.length();
        if (direction >= 0) {
            for (int i = 0; cursorPos < len && i < direction; i++) {
                if (Character.isHighSurrogate(text.charAt(cursorPos++)) && cursorPos < len && Character.isLowSurrogate(text.charAt(cursorPos))) {
                    cursorPos++;
                }
            }
        } else {
            for (int ix = direction; cursorPos > 0 && ix < 0; ix++) {
                cursorPos--;
                if (Character.isLowSurrogate(text.charAt(cursorPos)) && cursorPos > 0 && Character.isHighSurrogate(text.charAt(cursorPos - 1))) {
                    cursorPos--;
                }
            }
        }

        return cursorPos;
    }

    public static Consumer<String> prefix(String prefix, Consumer<String> expectedSize) {
        return string -> expectedSize.accept(prefix + string);
    }

    public static DataResult<int[]> fixedSize(IntStream stream, int size) {
        int[] ints = stream.limit(size + 1).toArray();
        if (ints.length != size) {
            Supplier<String> supplier = () -> "Input is not a list of " + size + " ints";
            return ints.length >= size ? DataResult.error(supplier, Arrays.copyOf(ints, size)) : DataResult.error(supplier);
        } else {
            return DataResult.success(ints);
        }
    }

    public static DataResult<long[]> fixedSize(LongStream stream, int expectedSize) {
        long[] longs = stream.limit(expectedSize + 1).toArray();
        if (longs.length != expectedSize) {
            Supplier<String> supplier = () -> "Input is not a list of " + expectedSize + " longs";
            return longs.length >= expectedSize ? DataResult.error(supplier, Arrays.copyOf(longs, expectedSize)) : DataResult.error(supplier);
        } else {
            return DataResult.success(longs);
        }
    }

    public static <T> DataResult<List<T>> fixedSize(List<T> list, int expectedSize) {
        if (list.size() != expectedSize) {
            Supplier<String> supplier = () -> "Input is not a list of " + expectedSize + " elements";
            return list.size() >= expectedSize ? DataResult.error(supplier, list.subList(0, expectedSize)) : DataResult.error(supplier);
        } else {
            return DataResult.success(list);
        }
    }

    public static void startTimerHackThread() {
        Thread thread = new Thread("Timer hack thread") {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(2147483647L);
                    } catch (InterruptedException var2) {
                        Util.LOGGER.warn("Timer hack thread interrupted, that really should not happen");
                        return;
                    }
                }
            }
        };
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(LOGGER));
        thread.start();
    }

    public static void copyBetweenDirs(Path fromDirectory, Path toDirectory, Path filePath) throws IOException {
        Path path = fromDirectory.relativize(filePath);
        Path path1 = toDirectory.resolve(path);
        Files.copy(filePath, path1);
    }

    public static String sanitizeName(String fileName, CharPredicate characterValidator) {
        return fileName.toLowerCase(Locale.ROOT)
            .chars()
            .mapToObj(i -> characterValidator.test((char)i) ? Character.toString((char)i) : "_")
            .collect(Collectors.joining());
    }

    public static <K, V> SingleKeyCache<K, V> singleKeyCache(Function<K, V> computeValue) {
        return new SingleKeyCache<>(computeValue);
    }

    public static <T, R> Function<T, R> memoize(final Function<T, R> memoFunction) {
        return new Function<T, R>() {
            private final Map<T, R> cache = new ConcurrentHashMap<>();

            @Override
            public R apply(T key) {
                return this.cache.computeIfAbsent(key, memoFunction);
            }

            @Override
            public String toString() {
                return "memoize/1[function=" + memoFunction + ", size=" + this.cache.size() + "]";
            }
        };
    }

    public static <T, U, R> BiFunction<T, U, R> memoize(final BiFunction<T, U, R> memoBiFunction) {
        return new BiFunction<T, U, R>() {
            private final Map<Pair<T, U>, R> cache = new ConcurrentHashMap<>();

            @Override
            public R apply(T key1, U key2) {
                return this.cache.computeIfAbsent(Pair.of(key1, key2), pair -> memoBiFunction.apply(pair.getFirst(), pair.getSecond()));
            }

            @Override
            public String toString() {
                return "memoize/2[function=" + memoBiFunction + ", size=" + this.cache.size() + "]";
            }
        };
    }

    public static <T> List<T> toShuffledList(Stream<T> stream, RandomSource random) {
        ObjectArrayList<T> list = stream.collect(ObjectArrayList.toList());
        shuffle(list, random);
        return list;
    }

    public static IntArrayList toShuffledList(IntStream stream, RandomSource random) {
        IntArrayList list = IntArrayList.wrap(stream.toArray());
        int size = list.size();

        for (int i = size; i > 1; i--) {
            int randomInt = random.nextInt(i);
            list.set(i - 1, list.set(randomInt, list.getInt(i - 1)));
        }

        return list;
    }

    public static <T> List<T> shuffledCopy(T[] array, RandomSource random) {
        ObjectArrayList<T> list = new ObjectArrayList<>(array);
        shuffle(list, random);
        return list;
    }

    public static <T> List<T> shuffledCopy(ObjectArrayList<T> list, RandomSource random) {
        ObjectArrayList<T> list1 = new ObjectArrayList<>(list);
        shuffle(list1, random);
        return list1;
    }

    public static <T> void shuffle(List<T> list, RandomSource random) {
        int size = list.size();

        for (int i = size; i > 1; i--) {
            int randomInt = random.nextInt(i);
            list.set(i - 1, list.set(randomInt, list.get(i - 1)));
        }
    }

    public static <T> CompletableFuture<T> blockUntilDone(Function<Executor, CompletableFuture<T>> task) {
        return blockUntilDone(task, CompletableFuture::isDone);
    }

    public static <T> T blockUntilDone(Function<Executor, T> task, Predicate<T> donePredicate) {
        BlockingQueue<Runnable> blockingQueue = new LinkedBlockingQueue<>();
        T object = task.apply(blockingQueue::add);

        while (!donePredicate.test(object)) {
            try {
                Runnable runnable = blockingQueue.poll(100L, TimeUnit.MILLISECONDS);
                if (runnable != null) {
                    runnable.run();
                }
            } catch (InterruptedException var5) {
                LOGGER.warn("Interrupted wait");
                break;
            }
        }

        int size = blockingQueue.size();
        if (size > 0) {
            LOGGER.warn("Tasks left in queue: {}", size);
        }

        return object;
    }

    public static <T> ToIntFunction<T> createIndexLookup(List<T> list) {
        int size = list.size();
        if (size < 8) {
            return list::indexOf;
        } else {
            Object2IntMap<T> map = new Object2IntOpenHashMap<>(size);
            map.defaultReturnValue(-1);

            for (int i = 0; i < size; i++) {
                map.put(list.get(i), i);
            }

            return map;
        }
    }

    public static <T> ToIntFunction<T> createIndexIdentityLookup(List<T> list) {
        int size = list.size();
        if (size < 8) {
            ReferenceList<T> list1 = new ReferenceImmutableList<>(list);
            return list1::indexOf;
        } else {
            Reference2IntMap<T> map = new Reference2IntOpenHashMap<>(size);
            map.defaultReturnValue(-1);

            for (int i = 0; i < size; i++) {
                map.put(list.get(i), i);
            }

            return map;
        }
    }

    public static <A, B> Typed<B> writeAndReadTypedOrThrow(Typed<A> typed, Type<B> type, UnaryOperator<Dynamic<?>> operator) {
        Dynamic<?> dynamic = (Dynamic<?>)typed.write().getOrThrow();
        return readTypedOrThrow(type, operator.apply(dynamic), true);
    }

    public static <T> Typed<T> readTypedOrThrow(Type<T> type, Dynamic<?> data) {
        return readTypedOrThrow(type, data, false);
    }

    public static <T> Typed<T> readTypedOrThrow(Type<T> type, Dynamic<?> data, boolean partial) {
        DataResult<Typed<T>> dataResult = type.readTyped(data).map(Pair::getFirst);

        try {
            return partial ? dataResult.getPartialOrThrow(IllegalStateException::new) : dataResult.getOrThrow(IllegalStateException::new);
        } catch (IllegalStateException var7) {
            CrashReport crashReport = CrashReport.forThrowable(var7, "Reading type");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Info");
            crashReportCategory.setDetail("Data", data);
            crashReportCategory.setDetail("Type", type);
            throw new ReportedException(crashReport);
        }
    }

    public static <T> List<T> copyAndAdd(List<T> list, T value) {
        return ImmutableList.<T>builderWithExpectedSize(list.size() + 1).addAll(list).add(value).build();
    }

    public static <T> List<T> copyAndAdd(T value, List<T> list) {
        return ImmutableList.<T>builderWithExpectedSize(list.size() + 1).add(value).addAll(list).build();
    }

    public static <K, V> Map<K, V> copyAndPut(Map<K, V> map, K key, V value) {
        return ImmutableMap.<K, V>builderWithExpectedSize(map.size() + 1).putAll(map).put(key, value).buildKeepingLast();
    }

    public static enum OS {
        LINUX("linux"),
        SOLARIS("solaris"),
        WINDOWS("windows") {
            @Override
            protected String[] getOpenUriArguments(URI uri) {
                return new String[]{"rundll32", "url.dll,FileProtocolHandler", uri.toString()};
            }
        },
        OSX("mac") {
            @Override
            protected String[] getOpenUriArguments(URI uri) {
                return new String[]{"open", uri.toString()};
            }
        },
        UNKNOWN("unknown");

        private final String telemetryName;

        OS(final String telemetryName) {
            this.telemetryName = telemetryName;
        }

        public void openUri(URI uri) {
            try {
                Process process = Runtime.getRuntime().exec(this.getOpenUriArguments(uri));
                process.getInputStream().close();
                process.getErrorStream().close();
                process.getOutputStream().close();
            } catch (IOException var3) {
                Util.LOGGER.error("Couldn't open location '{}'", uri, var3);
            }
        }

        public void openFile(File file) {
            this.openUri(file.toURI());
        }

        public void openPath(Path path) {
            this.openUri(path.toUri());
        }

        protected String[] getOpenUriArguments(URI uri) {
            String string = uri.toString();
            if ("file".equals(uri.getScheme())) {
                string = string.replace("file:", "file://");
            }

            return new String[]{"xdg-open", string};
        }

        public void openUri(String uri) {
            try {
                this.openUri(new URI(uri));
            } catch (IllegalArgumentException | URISyntaxException var3) {
                Util.LOGGER.error("Couldn't open uri '{}'", uri, var3);
            }
        }

        public String telemetryName() {
            return this.telemetryName;
        }
    }
}
