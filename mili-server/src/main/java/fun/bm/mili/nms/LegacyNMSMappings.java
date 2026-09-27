package fun.bm.mili.nms;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.DefaultQualifier;

/**
 * Legacy NMS compatibility shim: resolves class, method and field names that were
 * renamed across Minecraft/Paper versions and therefore cannot be resolved by the
 * official spigot&rarr;mojang mapping consumed by {@code io.papermc.paper.util.ObfHelper}.
 *
 * <p>Starting with Minecraft 1.20.5, Paper switched to a Mojang-mapped jar by default.
 * Many plugins still reference old spigot-era names (e.g.
 * {@code net.minecraft.server.ScoreboardServer}, {@code Entity#getDataWatcher()})
 * which no longer exist. The Paper reflection proxy ({@code PaperReflection}) only
 * knows about current spigot&rarr;mojang pairs, so those lookups fail with
 * {@link ClassNotFoundException} / {@link NoSuchMethodException}.</p>
 *
 * <p>This shim is consulted by {@code PaperReflection} <em>before</em> the ObfHelper
 * fallback (see {@code mili-server/paper-patches/features/0024-*.patch} for the class
 * name hook and {@code 0025-*.patch} for the member name hooks):</p>
 * <ul>
 *     <li>{@code PaperReflection#mapClassName} &rarr; {@link #mapClassName(String)}</li>
 *     <li>{@code PaperReflection#mapMethodName} / {@code #mapDeclaredMethodName}
 *         &rarr; {@link #mapMethodName(Class, String, Class...)} /
 *         {@link #mapDeclaredMethodName(Class, String, Class...)}</li>
 *     <li>{@code PaperReflection#mapFieldName} / {@code #mapDeclaredFieldName}
 *         &rarr; {@link #mapFieldName(Class, String)} /
 *         {@link #mapDeclaredFieldName(Class, String)}</li>
 * </ul>
 *
 * <h2>Safety policy</h2>
 * <p>Member names are <strong>only</strong> remapped when the legacy name is
 * <em>not</em> declared anywhere on the target class hierarchy (superclasses and
 * interfaces). If a class genuinely declares the requested member, the name is
 * returned untouched, so live members can never be shadowed by this table.</p>
 *
 * <h2>Extending the table without recompiling</h2>
 * <p>Admins can add mappings via a properties file. Location: the path from the
 * {@code mili.nmscompat.mappings} system property, or {@code config/mili-legacy-nms.properties}
 * in the server working directory. Format (one {@code key=value} per line):</p>
 * <pre>
 * # class name: legacy fully-qualified name = current fully-qualified name
 * class.net.minecraft.server.SomeOldClass=net.minecraft.world.entity.SomeNewClass
 * # method: CurrentSimpleClassName.legacyMethodName = currentMethodName
 * method.Entity.getDataWatcher=getSynchedEntityData
 * # field: CurrentSimpleClassName.legacyFieldName = currentFieldName
 * field.ServerPlayer.playerConnection=connection
 * </pre>
 * The file is (re)loaded at server start; call {@link #reload()} to refresh at runtime.
 *
 * <h2>Diagnostics</h2>
 * <p>Every successful remap is logged at {@code FINE}. Set the system property
 * {@code -Dmili.nmscompat.verbose=true} to log remaps at {@code INFO}, which helps
 * identify exactly which plugin lookups are being rescued.</p>
 *
 * <h2>Limitations</h2>
 * <ul>
 *     <li>Only <em>reflective</em> lookups are handled (rewritten by
 *         {@code io.papermc:reflection-rewriter}). Class symbols hard-coded in a
 *         plugin's bytecode cannot be fixed at this layer.</li>
 *     <li>Method remapping is name-only (parameter types are used solely for the
 *         declared-member existence check, not for overload selection).</li>
 * </ul>
 */
@DefaultQualifier(NonNull.class)
public final class LegacyNMSMappings {

    private static final Logger LOGGER = Logger.getLogger("Mili/NMSCompat");

    /** System property: log every remap at INFO instead of FINE. */
    private static final String VERBOSE_PROPERTY = "mili.nmscompat.verbose";
    /** System property: explicit path to the external mappings properties file. */
    private static final String MAPPINGS_FILE_PROPERTY = "mili.nmscompat.mappings";
    /** Default external mappings file (relative to the server working directory). */
    private static final String DEFAULT_MAPPINGS_FILE = "config/mili-legacy-nms.properties";

    /** Immutable mapping tables; swapped atomically by {@link #reload()}. */
    private static volatile Snapshot snapshot = loadSnapshot();

    private LegacyNMSMappings() {
    }

    private record Snapshot(
        Map<String, String> classToCurrent,
        Map<String, String> currentToClass,
        Map<String, String> methodByKey,
        Map<String, String> methodByLegacyName,
        Map<String, String> fieldByKey,
        Map<String, String> fieldByLegacyName
    ) {
    }

    // ============================================================
    //  Class name mapping
    // ============================================================

    /**
     * Resolves a legacy class name to its current equivalent, or returns
     * {@code name} unchanged if no entry is present.
     *
     * <p>Lookup order:</p>
     * <ol>
     *     <li>Exact match in the legacy &rarr; current table.</li>
     *     <li>Suffix match: if the simple class name of {@code name} has a unique
     *         entry in the table, return its current equivalent (catches plugins
     *         that use a partial/outdated package prefix but the same simple name).</li>
     *     <li>Fallback: return {@code name} unchanged and let Paper's ObfHelper
     *         handle it.</li>
     * </ol>
     */
    public static String mapClassName(final String name) {
        final Snapshot snap = snapshot;
        final String mapped = snap.classToCurrent().get(name);
        if (mapped != null) {
            logRemap("class", name, mapped);
            return mapped;
        }
        final String resolved = resolveClassBySuffix(snap, name);
        if (resolved != null) {
            logRemap("class", name, resolved);
            return resolved;
        }
        return name;
    }

    /**
     * Reverse mapping &ndash; given a (potentially current) mojang name, returns the
     * legacy spigot-equivalent if one is registered.
     */
    public static String reverseMapClassName(final String name) {
        return snapshot.currentToClass().getOrDefault(name, name);
    }

    // ============================================================
    //  Method / field name mapping
    // ============================================================

    /**
     * Maps a legacy method name to its current equivalent for reflective
     * {@code getMethod(...)} lookups. See {@link #mapMember} for the policy.
     */
    public static String mapMethodName(final Class<?> clazz, final String name, final Class<?> @Nullable ... parameterTypes) {
        return mapMember(clazz, name, snapshot.methodByKey(), snapshot.methodByLegacyName(), true, parameterTypes);
    }

    /** Declared-method variant of {@link #mapMethodName}. */
    public static String mapDeclaredMethodName(final Class<?> clazz, final String name, final Class<?> @Nullable ... parameterTypes) {
        return mapMember(clazz, name, snapshot.methodByKey(), snapshot.methodByLegacyName(), true, parameterTypes);
    }

    /**
     * Maps a legacy field name to its current equivalent for reflective
     * {@code getField(...)} lookups. See {@link #mapMember} for the policy.
     */
    public static String mapFieldName(final Class<?> clazz, final String name) {
        return mapMember(clazz, name, snapshot.fieldByKey(), snapshot.fieldByLegacyName(), false, null);
    }

    /** Declared-field variant of {@link #mapFieldName}. */
    public static String mapDeclaredFieldName(final Class<?> clazz, final String name) {
        return mapMember(clazz, name, snapshot.fieldByKey(), snapshot.fieldByLegacyName(), false, null);
    }

    /**
     * Shared member remapping logic.
     *
     * <ol>
     *     <li>If {@code name} is declared anywhere on {@code clazz}'s hierarchy,
     *         return {@code name} unchanged &ndash; a live member always wins.</li>
     *     <li>Otherwise look up {@code "SimpleClassName." + name} walking the class
     *         hierarchy (so {@code ServerLevel.getType} resolves via {@code Level.getType}).</li>
     *     <li>Otherwise fall back to the unique-legacy-name global alias.</li>
     * </ol>
     */
    private static String mapMember(
        final Class<?> clazz,
        final String name,
        final Map<String, String> byKey,
        final Map<String, String> byLegacyName,
        final boolean isMethod,
        final Class<?> @Nullable [] parameterTypes
    ) {
        final Snapshot snap = snapshot;
        // 1) A live member with the requested name always wins.
        if (isMethod ? isDeclaredMethod(clazz, name, parameterTypes) : isDeclaredField(clazz, name)) {
            return name;
        }
        // 2) Keyed lookup: walk the hierarchy so subclass calls resolve to the
        //    declaring class' key (e.g. ServerLevel -> Level).
        String mapped = null;
        for (Class<?> c = clazz; c != null && mapped == null; c = c.getSuperclass()) {
            mapped = byKey.get(c.getSimpleName() + "." + name);
        }
        // 3) Global fallback by unique legacy simple name.
        if (mapped == null) {
            mapped = byLegacyName.get(name);
        }
        if (mapped != null && !mapped.equals(name)) {
            logRemap(isMethod ? "method" : "field", clazz.getName() + "#" + name, mapped);
            return mapped;
        }
        return name;
    }

    private static boolean isDeclaredMethod(final Class<?> clazz, final String name, final Class<?> @Nullable [] parameterTypes) {
        try {
            for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
                if (declaresMethod(c, name, parameterTypes)) {
                    return true;
                }
                for (final Class<?> i : c.getInterfaces()) {
                    if (declaresMethod(i, name, parameterTypes)) {
                        return true;
                    }
                }
            }
        } catch (final Throwable t) {
            LOGGER.log(Level.FINEST, "Failed to scan " + clazz + " for declared method " + name, t);
        }
        return false;
    }

    private static boolean declaresMethod(final Class<?> c, final String name, final Class<?> @Nullable [] parameterTypes) {
        for (final Method m : c.getDeclaredMethods()) {
            if (!m.getName().equals(name)) {
                continue;
            }
            if (parameterTypes == null || m.getParameterCount() == parameterTypes.length) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDeclaredField(final Class<?> clazz, final String name) {
        try {
            for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
                for (final Field f : c.getDeclaredFields()) {
                    if (f.getName().equals(name)) {
                        return true;
                    }
                }
            }
        } catch (final Throwable t) {
            LOGGER.log(Level.FINEST, "Failed to scan " + clazz + " for declared field " + name, t);
        }
        return false;
    }

    // ============================================================
    //  Table construction
    // ============================================================

    /**
     * Returns the unmodifiable view of the legacy &rarr; current class name map.
     * The returned map is shared across threads; do not mutate.
     */
    public static Map<String, String> getMappings() {
        return snapshot.classToCurrent();
    }

    /**
     * Returns the unmodifiable reverse view of the class mapping table (current &rarr; legacy).
     */
    public static Map<String, String> getReverseMappings() {
        return snapshot.currentToClass();
    }

    /** Atomically rebuilds all tables (defaults + external file). */
    public static void reload() {
        snapshot = loadSnapshot();
        LOGGER.info("Mili legacy NMS mappings reloaded");
    }

    private static Snapshot loadSnapshot() {
        final Map<String, String> classes = new HashMap<>(defaultClassMappings());
        final Map<String, String> methods = new HashMap<>(defaultMethodMappings());
        final Map<String, String> fields = new HashMap<>(defaultFieldMappings());
        loadExternalMappings(classes, methods, fields);
        return buildSnapshot(classes, methods, fields);
    }

    private static Snapshot buildSnapshot(
        final Map<String, String> classes,
        final Map<String, String> methods,
        final Map<String, String> fields
    ) {
        // Reverse class map: only unambiguous targets.
        final Map<String, Integer> targetCounts = new HashMap<>();
        for (final String value : classes.values()) {
            targetCounts.merge(value, 1, Integer::sum);
        }
        final Map<String, String> classReverse = new HashMap<>();
        for (final Map.Entry<String, String> e : classes.entrySet()) {
            if (targetCounts.getOrDefault(e.getValue(), 0) == 1) {
                classReverse.put(e.getValue(), e.getKey());
            }
        }
        return new Snapshot(
            Collections.unmodifiableMap(classes),
            Collections.unmodifiableMap(classReverse),
            Collections.unmodifiableMap(methods),
            Collections.unmodifiableMap(uniqueLegacyFallback(methods)),
            Collections.unmodifiableMap(fields),
            Collections.unmodifiableMap(uniqueLegacyFallback(fields))
        );
    }

    /**
     * Builds the global fallback view: legacy simple name &rarr; current name, keeping
     * only entries whose legacy part is unique (no two keys share the same legacy
     * name) so lookups stay deterministic.
     */
    private static Map<String, String> uniqueLegacyFallback(final Map<String, String> byKey) {
        final Map<String, Integer> legacyCounts = new HashMap<>();
        for (final String key : byKey.keySet()) {
            final String legacy = key.substring(key.lastIndexOf('.') + 1);
            legacyCounts.merge(legacy, 1, Integer::sum);
        }
        final Map<String, String> fallback = new HashMap<>();
        for (final Map.Entry<String, String> e : byKey.entrySet()) {
            final String legacy = e.getKey().substring(e.getKey().lastIndexOf('.') + 1);
            if (legacyCounts.getOrDefault(legacy, 0) == 1) {
                fallback.put(legacy, e.getValue());
            }
        }
        return fallback;
    }

    private static String resolveClassBySuffix(final Snapshot snap, final String name) {
        final int lastDot = name.lastIndexOf('.');
        if (lastDot < 0) {
            return null;
        }
        final String simpleName = name.substring(lastDot + 1);
        String uniqueTarget = null;
        int count = 0;
        for (final Map.Entry<String, String> e : snap.classToCurrent().entrySet()) {
            final String keySimple = e.getKey().substring(e.getKey().lastIndexOf('.') + 1);
            if (keySimple.equals(simpleName)) {
                uniqueTarget = e.getValue();
                count++;
                if (count > 1) {
                    return null; // ambiguous – let ObfHelper try
                }
            }
        }
        return count == 1 ? uniqueTarget : null;
    }

    // ============================================================
    //  Curated defaults
    // ============================================================

    /**
     * Hard-coded legacy &rarr; current NMS class name pairs.
     *
     * <p>DO NOT add {@code net.minecraft.server.v1_*} versioned paths here &ndash;
     * the CraftBukkit version prefix is handled by the rewriter's own
     * relocation-stripping logic. Only add fully-qualified names.</p>
     */
    private static Map<String, String> defaultClassMappings() {
        final Map<String, String> map = new HashMap<>();

        // ============================================================
        //  1) Server-wide classes that lost their "Server" prefix
        //     in the 1.20.5 mojang remap.
        // ============================================================
        // Scoreboard server side (used by CustomCrops 3.6.x, scoreboard plugins)
        map.put("net.minecraft.server.ScoreboardServer", "net.minecraft.server.ServerScoreboard");

        // ============================================================
        //  2) Network / syncher family
        // ============================================================
        // DataWatcher -> SynchedEntityData
        map.put("net.minecraft.network.syncher.DataWatcher", "net.minecraft.network.syncher.SynchedEntityData");
        map.put("net.minecraft.network.syncher.DataWatcherObject", "net.minecraft.network.syncher.EntityDataAccessor");
        map.put("net.minecraft.network.syncher.DataWatcherSerializer", "net.minecraft.network.syncher.EntityDataSerializer");
        map.put("net.minecraft.network.syncher.DataWatcherRegistry", "net.minecraft.network.syncher.EntitySerializers");
        // DataWatcher.Item -> SynchedEntityData.DataValue
        map.put("net.minecraft.network.syncher.DataWatcher$Item", "net.minecraft.network.syncher.SynchedEntityData$DataValue");
        // ServerGamePacketListenerImpl legacy alias
        map.put("net.minecraft.server.network.PlayerConnection", "net.minecraft.server.network.ServerGamePacketListenerImpl");
        map.put("net.minecraft.server.network.ServerPlayerConnection", "net.minecraft.server.network.ServerGamePacketListenerImpl");

        // ============================================================
        //  3) Entity type / registry
        // ============================================================
        map.put("net.minecraft.world.entity.EntityTypes", "net.minecraft.world.entity.EntityType");

        // ============================================================
        //  4) CraftBukkit relocation shims
        //     Some plugins resolve CraftBukkit impl classes by the pre-1.20.5
        //     package even on a mojang-mapped server.
        // ============================================================
        map.put("org.bukkit.craftbukkit.CraftScoreboard", "org.bukkit.craftbukkit.score.CraftScoreboard");
        map.put("org.bukkit.craftbukkit.CraftServer", "org.bukkit.craftbukkit.CraftServer");

        // ============================================================
        //  5) Block entity family (post-1.21 mojang rename)
        // ============================================================
        map.put("net.minecraft.world.level.block.entity.TileEntity", "net.minecraft.world.level.block.entity.BlockEntity");

        return map;
    }

    /**
     * Hard-coded legacy &rarr; current method name pairs, keyed by
     * {@code CurrentSimpleClassName.legacyMethodName}. Keys are resolved against
     * the class hierarchy, so {@code ServerLevel.getType} matches via
     * {@code Level.getType}.
     */
    private static Map<String, String> defaultMethodMappings() {
        final Map<String, String> map = new HashMap<>();

        // DataWatcher accessor (Mojang rename: getDataWatcher -> getSynchedEntityData)
        map.put("Entity.getDataWatcher", "getSynchedEntityData");
        // World/Level block queries (spigot-era names)
        map.put("Level.getType", "getBlockState");
        map.put("Level.setTypeAndData", "setBlock");
        // Block state access (spigot-era getBlockData -> defaultBlockState)
        map.put("BlockBehaviour.getBlockData", "defaultBlockState");
        map.put("BlockBehaviour.getBlockState", "defaultBlockState");

        return map;
    }

    /**
     * Hard-coded legacy &rarr; current field name pairs, keyed by
     * {@code CurrentSimpleClassName.legacyFieldName}.
     */
    private static Map<String, String> defaultFieldMappings() {
        final Map<String, String> map = new HashMap<>();

        // PlayerConnection field (Mojang rename: playerConnection -> connection)
        map.put("ServerPlayer.playerConnection", "connection");

        return map;
    }

    // ============================================================
    //  External mappings file
    // ============================================================

    private static void loadExternalMappings(
        final Map<String, String> classes,
        final Map<String, String> methods,
        final Map<String, String> fields
    ) {
        final Path file = resolveExternalFile();
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        final Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (final Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to read legacy NMS mappings file " + file, ex);
            return;
        }
        int loaded = 0;
        for (final String key : props.stringPropertyNames()) {
            final String value = props.getProperty(key, "").trim();
            if (value.isEmpty()) {
                continue;
            }
            try {
                if (key.startsWith("class.")) {
                    classes.put(key.substring("class.".length()), value);
                } else if (key.startsWith("method.")) {
                    methods.put(key.substring("method.".length()), value);
                } else if (key.startsWith("field.")) {
                    fields.put(key.substring("field.".length()), value);
                } else {
                    LOGGER.warning("Ignoring unrecognized legacy NMS mapping key '" + key
                        + "' (expected class./method./field. prefix) in " + file);
                    continue;
                }
                loaded++;
            } catch (final Exception ex) {
                LOGGER.log(Level.WARNING, "Invalid legacy NMS mapping entry '" + key + "' in " + file, ex);
            }
        }
        if (loaded > 0) {
            LOGGER.info("Loaded " + loaded + " extra legacy NMS mappings from " + file);
        }
    }

    private static @Nullable Path resolveExternalFile() {
        final String explicit = System.getProperty(MAPPINGS_FILE_PROPERTY);
        if (explicit != null && !explicit.isBlank()) {
            return Path.of(explicit);
        }
        final Path defaultPath = Path.of(DEFAULT_MAPPINGS_FILE);
        return Files.isRegularFile(defaultPath) ? defaultPath : null;
    }

    // ============================================================
    //  Diagnostics
    // ============================================================

    private static void logRemap(final String kind, final String from, final String to) {
        if (Boolean.getBoolean(VERBOSE_PROPERTY)) {
            LOGGER.info("[Mili NMSCompat] Remapped legacy " + kind + " '" + from + "' -> '" + to + "'");
        } else {
            LOGGER.fine("[Mili NMSCompat] Remapped legacy " + kind + " '" + from + "' -> '" + to + "'");
        }
    }
}
