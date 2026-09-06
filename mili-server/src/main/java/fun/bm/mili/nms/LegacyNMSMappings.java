package fun.bm.mili.nms;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;

/**
 * Maintains a lookup table of legacy/current NMS class name pairs that were renamed
 * across Minecraft major versions and therefore are missing from the official
 * spigot→mojang mapping consumed by {@code io.papermc.paper.util.ObfHelper}.
 *
 * <p>Starting with Minecraft 1.20.5, Paper switched to a Mojang-mapped jar by default.
 * Many server plugins still reference the old spigot-obfuscated class names (e.g.
 * {@code net.minecraft.server.ScoreboardServer}) which no longer exist as a class
 * in the live jar. The Paper reflection-rewriter (
 * {@code io.papermc.paper.util.ObfHelper}) only knows about the current
 * spigot (obfuscated) → mojang (public) name pairs, so lookups for legacy names fail
 * with {@link ClassNotFoundException}.</p>
 *
 * <p>This shim is injected into
 * {@code PaperReflection#mapClassName} (see
 * {@code mili-server/paper-patches/features/0024-Mili-Add-legacy-NMS-class-remapping.patch})
 * and runs BEFORE the ObfHelper fallback. Bidirectional mappings allow both:</p>
 * <ul>
 *     <li>Legacy spigot names → current mojang names (primary use case), and</li>
 *     <li>Current mojang names → legacy spigot-compatible names (reverse lookup,
 *         used by plugins that do {@code Class.forName} detection across MC versions).</li>
 * </ul>
 *
 * <h2>Known renames covered</h2>
 * <ul>
 *     <li>1.20.5 mojang remapper: server-side classes, entity data watcher classes,
 *         packet and network handler classes.</li>
 *     <li>1.21.x renames in entity AI, world generation and datafixer utilities.</li>
 * </ul>
 *
 * <h2>Limitations</h2>
 * <ul>
 *     <li>This only handles <em>reflective</em> lookups (i.e. {@code Class.forName}
 *         calls rewritten by {@code io.papermc:reflection-rewriter}). Direct
 *         class symbol references baked into a plugin's bytecode that were compiled
 *         against a renamed class cannot be remapped at this layer. Affected
 *         plugins must be updated by their authors.</li>
 * </ul>
 */
@DefaultQualifier(NonNull.class)
public final class LegacyNMSMappings {

    private LegacyNMSMappings() {
    }

    /**
     * Hard-coded legacy → current NMS class name pairs.
     *
     * <p>DO NOT add {@code net.minecraft.server.v1_*} versioned paths here – those
     * are handled by the rewriter's own version-stripping logic prior to reaching
     * this shim. Only add fully-qualified class names that the rewriter passes
     * through verbatim (i.e. dotted mojang/spigot form without a version prefix).</p>
     */
    private static final Map<String, String> DEFAULT_MAPPINGS = buildDefaults();

    /**
     * Reverse view of {@link #DEFAULT_MAPPINGS} – lazily initialised and only
     * populated with entries whose target is unique (no two legacy names map to
     * the same current name) so that reverse lookup stays deterministic.
     */
    private static final Map<String, String> REVERSE_MAPPINGS = buildReverse();

    private static Map<String, String> buildDefaults() {
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
        // DataWatcher → SynchedEntityData
        map.put("net.minecraft.network.syncher.DataWatcher", "net.minecraft.network.syncher.SynchedEntityData");
        map.put("net.minecraft.network.syncher.DataWatcherObject", "net.minecraft.network.syncher.EntityDataAccessor");
        map.put("net.minecraft.network.syncher.DataWatcherSerializer", "net.minecraft.network.syncher.EntityDataSerializer");
        map.put("net.minecraft.network.syncher.DataWatcherRegistry", "net.minecraft.network.syncher.EntitySerializers");
        // DataWatcher.Item → SynchedEntityData.DataValue
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

        return Collections.unmodifiableMap(map);
    }

    private static Map<String, String> buildReverse() {
        // Build REVERSE mappings by walking DEFAULT_MAPPINGS.
        // Only include entries whose value appears exactly once to avoid
        // ambiguous reverse lookups.
        final Map<String, Integer> targetCounts = new HashMap<>();
        for (final String value : DEFAULT_MAPPINGS.values()) {
            targetCounts.merge(value, 1, Integer::sum);
        }
        final Map<String, String> reverse = new HashMap<>();
        for (final Map.Entry<String, String> e : DEFAULT_MAPPINGS.entrySet()) {
            if (targetCounts.getOrDefault(e.getValue(), 0) == 1) {
                reverse.put(e.getValue(), e.getKey());
            }
        }
        return Collections.unmodifiableMap(reverse);
    }

    /**
     * Returns the unmodifiable view of the legacy → current name map.
     * The returned map is shared across threads; do not mutate.
     */
    public static Map<String, String> getMappings() {
        return DEFAULT_MAPPINGS;
    }

    /**
     * Returns the unmodifiable reverse view of the mapping table (current → legacy).
     */
    public static Map<String, String> getReverseMappings() {
        return REVERSE_MAPPINGS;
    }

    /**
     * Resolves a legacy class name to its current equivalent, or returns
     * {@code name} unchanged if no entry is present.
     *
     * <p>Lookup order:</p>
     * <ol>
     *     <li>Exact match in the legacy → current table.</li>
     *     <li>Suffix match: if {@code name} ends with a known short class name
     *         (e.g. {@code DataWatcherObject}) that has a unique entry in the
     *         table, return its current equivalent. This catches plugins that
     *         use a partial package prefix (e.g. from an older CraftBukkit
     *         version) but the same simple class name.</li>
     *     <li>Fallback: return {@code name} unchanged and let Paper's
     *         ObfHelper handle it.</li>
     * </ol>
     */
    public static String mapClassName(final String name) {
        // 1. Exact match
        final String mapped = DEFAULT_MAPPINGS.get(name);
        if (mapped != null) {
            return mapped;
        }

        // 2. Suffix match as a last-resort shim
        final String resolved = resolveBySuffix(name);
        if (resolved != null) {
            return resolved;
        }

        // 3. No match – let downstream (ObfHelper) handle it
        return name;
    }

    /**
     * Reverse mapping – given a (potentially current) mojang name, returns the
     * legacy spigot-equivalent if one is registered.
     */
    public static String reverseMapClassName(final String name) {
        return REVERSE_MAPPINGS.getOrDefault(name, name);
    }

    /**
     * Attempts to resolve {@code name} by matching its simple class name against
     * the keys in the mapping table. Returns {@code null} if zero or more than
     * one key shares the same simple class name (ambiguous).
     */
    private static String resolveBySuffix(final String name) {
        final int lastDot = name.lastIndexOf('.');
        if (lastDot < 0) {
            return null;
        }
        final String simpleName = name.substring(lastDot + 1);
        String uniqueTarget = null;
        int count = 0;
        for (final Map.Entry<String, String> e : DEFAULT_MAPPINGS.entrySet()) {
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
}
